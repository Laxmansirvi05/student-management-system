package com.campuscompanion.assistant;

import com.campuscompanion.ai.AiFeature;
import com.campuscompanion.ai.AiInvalidResponseException;
import com.campuscompanion.ai.AiRequestStatus;
import com.campuscompanion.ai.AiTimeoutException;
import com.campuscompanion.ai.AiUnavailableException;
import com.campuscompanion.ai.AiUsageService;
import com.campuscompanion.ai.prompt.PromptBuilder;
import com.campuscompanion.ai.prompt.PromptLibrary;
import com.campuscompanion.ai.provider.AiCompletion;
import com.campuscompanion.ai.provider.AiImage;
import com.campuscompanion.ai.provider.AiProvider;
import com.campuscompanion.ai.provider.AiProviderException;
import com.campuscompanion.ai.provider.AiRequest;
import com.campuscompanion.ai.provider.VisionProvider;
import com.campuscompanion.assistant.dto.ChatRequest;
import com.campuscompanion.assistant.dto.ChatResponse;
import com.campuscompanion.assistant.dto.ConversationListResponse;
import com.campuscompanion.assistant.dto.ConversationResponse;
import com.campuscompanion.assistant.dto.MessageListResponse;
import com.campuscompanion.assistant.dto.MessageResponse;
import com.campuscompanion.common.config.AiProperties;
import com.campuscompanion.common.error.ConflictException;
import com.campuscompanion.common.error.ValidationException;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.student.StudentProfileService;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * The robot's Quick Assistant (ADR 0014): a short chat with the same AI assistant as the study
 * tools, behind the same {@link AiProvider}, the same limits and the same usage log. What is sent
 * to the model is the instructions and the text of this chat, nothing about any person. The chat is
 * kept for the student's own history and is read by nobody else.
 */
@Service
@PreAuthorize("hasRole('STUDENT')")
public class AssistantService {

  static final String NOTICE = "AI-generated. Check it against your course material.";
  static final int CONTEXT_MESSAGES = 20;
  static final int REPLY_MAX = 4000;

  /** An explanatory answer is longer; the stored text may not exceed 8,000 characters. */
  static final int EXPLAIN_REPLY_MAX = 7500;

  static final int EXPLAIN_TOKENS = 2600;

  /** The start of a stored question that came with a picture. The picture itself is not stored. */
  public static final String IMAGE_MARKER = "[Image] ";

  static final String IMAGE_DEFAULT_QUESTION = "Explain this image.";
  static final int HISTORY_LIST = 7;
  private static final String UNAVAILABLE = "The assistant is unavailable right now.";
  private static final String NO_VISION =
      "Questions with a picture are not set up on this server yet.";
  private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

  private final AssistantHistory history;
  private final AiUsageService usage;
  private final AiProvider provider;
  private final VisionProvider vision;
  private final AssistantImageChecker images;
  private final PromptLibrary templates;
  private final PromptBuilder prompts;
  private final AiProperties properties;
  private final StudentProfileService students;

  public AssistantService(
      AssistantHistory history,
      AiUsageService usage,
      AiProvider provider,
      VisionProvider vision,
      AssistantImageChecker images,
      PromptLibrary templates,
      PromptBuilder prompts,
      AiProperties properties,
      StudentProfileService students) {
    this.history = history;
    this.usage = usage;
    this.provider = provider;
    this.vision = vision;
    this.images = images;
    this.templates = templates;
    this.prompts = prompts;
    this.properties = properties;
    this.students = students;
  }

  /** Answers one message, without a picture. */
  public ChatResponse chat(CurrentUser user, ChatRequest request) {
    return chat(user, request, null, null);
  }

  /**
   * Answers one message, with an optional picture. The earlier messages of the chat, up to twenty
   * in all, are sent with it, so the model can follow the thread; it remembers nothing itself. The
   * answer style ("quick" or "explain") chooses the instructions. A picture is checked and redrawn,
   * goes to a model that reads pictures, and is not stored: the stored question only says that a
   * picture came with it. Nothing is stored when the answer fails, so a retry does not leave the
   * question twice.
   */
  public ChatResponse chat(
      CurrentUser user, ChatRequest request, byte[] picture, String pictureType) {
    Long studentId = students.currentStudent(user).studentId();
    if (request.questionId() != null) {
      return answerAgain(user, studentId, request);
    }
    String question = request.message() == null ? "" : request.message().strip();
    boolean withPicture = picture != null;
    if (question.isEmpty() && !withPicture) {
      throw new ValidationException("message", "Write a message.");
    }
    boolean explain = "explain".equals(request.style());
    List<AiImage> attached = List.of();
    if (withPicture) {
      if (!vision.available()) {
        throw new AiUnavailableException(NO_VISION);
      }
      attached = List.of(images.prepare(picture, pictureType));
      if (question.isEmpty()) {
        question = IMAGE_DEFAULT_QUESTION;
      }
      question = IMAGE_MARKER + question;
    }
    String source = "quick".equals(request.mode()) ? AssistantConversation.QUICK : "assistant";
    List<AssistantMessage> earlier = List.of();
    if (request.conversationId() != null) {
      AssistantConversation chat = history.owned(studentId, request.conversationId());
      source = chat.getSource();
      earlier = history.recent(chat.getId(), CONTEXT_MESSAGES - 1);
    }

    String answer = ask(user, transcript(earlier, question), explain, attached);
    AssistantHistory.Saved saved =
        history.save(
            studentId, request.conversationId(), source, question, answer, styleOf(explain));
    return new ChatResponse(
        respond(saved.chat()), respond(saved.question()), respond(saved.reply()), NOTICE);
  }

  /**
   * The answer, in the asked mode, to a question that is already in the chat (the "other mode" of
   * Quick Assistant V2). A saved answer is returned as it is, with no model call and no use of the
   * student's limit. Otherwise one is written from the chat before that question, saved next to the
   * question, and returned. A question that came with a picture cannot be answered again: the
   * picture is not kept.
   */
  private ChatResponse answerAgain(CurrentUser user, Long studentId, ChatRequest request) {
    if (request.conversationId() == null) {
      throw new ValidationException("conversationId", "Choose the chat of the question.");
    }
    boolean explain = "explain".equals(request.style());
    String style = styleOf(explain);
    AssistantConversation chat = history.owned(studentId, request.conversationId());
    AssistantMessage question = history.question(chat.getId(), request.questionId());
    var saved = history.answer(question.getId(), style);
    if (saved.isPresent()) {
      return new ChatResponse(respond(chat), respond(question), respond(saved.get()), NOTICE);
    }
    if (question.getContent().startsWith(IMAGE_MARKER)) {
      throw new ConflictException(
          "The picture of this question is not kept. Ask it again with the picture attached.");
    }
    List<AssistantMessage> earlier =
        history.before(chat.getId(), question.getId(), CONTEXT_MESSAGES - 1);
    String answer = ask(user, transcript(earlier, question.getContent()), explain, List.of());
    try {
      AssistantHistory.Saved written =
          history.saveAnswer(studentId, chat.getId(), question.getId(), answer, style);
      return new ChatResponse(
          respond(written.chat()), respond(written.question()), respond(written.reply()), NOTICE);
    } catch (DataIntegrityViolationException twice) {
      // Another request saved the same answer a moment earlier: that one is the answer.
      AssistantMessage first = history.answer(question.getId(), style).orElseThrow(() -> twice);
      return new ChatResponse(respond(chat), respond(question), respond(first), NOTICE);
    }
  }

  private static String styleOf(boolean explain) {
    return explain ? "explain" : "quick";
  }

  /** The student's chats, newest first. The robot's list is kept short. */
  public ConversationListResponse conversations(CurrentUser user, String source) {
    Long studentId = students.currentStudent(user).studentId();
    String kind = "quick".equals(source) ? AssistantConversation.QUICK : "assistant";
    int limit =
        AssistantConversation.QUICK.equals(kind) ? AssistantHistory.QUICK_CHATS_KEPT : HISTORY_LIST;
    return new ConversationListResponse(
        history.list(studentId, kind, limit).stream().map(AssistantService::respond).toList());
  }

  public MessageListResponse messages(CurrentUser user, Long conversationId) {
    Long studentId = students.currentStudent(user).studentId();
    history.owned(studentId, conversationId);
    return new MessageListResponse(
        history.all(conversationId).stream().map(AssistantService::respond).toList());
  }

  public void delete(CurrentUser user, Long conversationId) {
    history.delete(students.currentStudent(user).studentId(), conversationId);
  }

  // --- the model call -------------------------------------------------------------------------

  /**
   * One request to the model, with the limits checked before and the usage written after. A failure
   * or a timeout is logged but does not use up a request; a reply must be some text.
   */
  private String ask(
      CurrentUser user, String conversation, boolean explain, List<AiImage> pictures) {
    usage.begin(user.id());
    AiRequest prompt =
        prompts
            .build(
                explain ? templates.explainChat() : templates.quickChat(),
                Map.of("conversation", conversation),
                explain
                    ? Math.max(properties.maxOutputTokens(), EXPLAIN_TOKENS)
                    : properties.maxOutputTokens(),
                properties.responseTimeout())
            .withImages(pictures);
    AiProvider model = pictures.isEmpty() ? provider : vision.provider();
    long started = System.nanoTime();
    Integer outputChars = null;
    AiRequestStatus status = AiRequestStatus.FAILED;
    try {
      // With several models configured, an empty reply sends the request on to the next model.
      AiCompletion completion = model.complete(prompt, reply -> reply != null && !reply.isBlank());
      String text = completion.text() == null ? "" : completion.text().strip();
      outputChars = text.length();
      if (text.isEmpty()) {
        throw new AiInvalidResponseException(UNAVAILABLE);
      }
      status = AiRequestStatus.SUCCESS;
      return explain ? cutExplanation(text) : cut(text, REPLY_MAX);
    } catch (AiProviderException failure) {
      // Only the kind of failure is logged. The provider's own message can repeat the input.
      log.warn("The AI provider call failed: {}", failure.getKind());
      if (failure.getKind() == AiProviderException.Kind.TIMEOUT) {
        status = AiRequestStatus.TIMEOUT;
        throw new AiTimeoutException(UNAVAILABLE);
      }
      if (failure.getKind() == AiProviderException.Kind.INVALID_OUTPUT) {
        throw new AiInvalidResponseException(UNAVAILABLE);
      }
      throw new AiUnavailableException(UNAVAILABLE);
    } finally {
      long durationMs = (System.nanoTime() - started) / 1_000_000;
      usage.record(
          user.id(),
          AiFeature.QUICK_CHAT,
          prompt.promptVersion(),
          prompt.userContent().length(),
          outputChars,
          status,
          durationMs);
      usage.end(user.id());
    }
  }

  /**
   * The messages for the model: when a question has been answered in both modes, only the newest
   * answer goes with it, so the same question is not repeated in the context.
   */
  static List<AssistantMessage> oneAnswerEach(List<AssistantMessage> messages) {
    java.util.Map<Long, Long> newest = new java.util.HashMap<>();
    for (AssistantMessage message : messages) {
      if (message.getReplyTo() != null) {
        newest.put(message.getReplyTo(), message.getId());
      }
    }
    return messages.stream()
        .filter(
            message ->
                message.getReplyTo() == null
                    || message.getId().equals(newest.get(message.getReplyTo())))
        .toList();
  }

  private static String cut(String text, int max) {
    return text.length() > max ? text.substring(0, max) : text;
  }

  /**
   * An explanation too long to store is cut at the last blank line within the limit, and a code or
   * diagram block left open by the cut is closed, so the page never receives half a block.
   */
  static String cutExplanation(String text) {
    if (text.length() <= EXPLAIN_REPLY_MAX) {
      return text;
    }
    String head = text.substring(0, EXPLAIN_REPLY_MAX);
    int paragraph = head.lastIndexOf("\n\n");
    if (paragraph > EXPLAIN_REPLY_MAX / 2) {
      head = head.substring(0, paragraph);
    }
    int fences = 0;
    for (int at = head.indexOf("```"); at >= 0; at = head.indexOf("```", at + 3)) {
      fences++;
    }
    return fences % 2 == 1 ? head.stripTrailing() + "\n```" : head.stripTrailing();
  }

  /** The chat as text for the model: each message under its speaker, the new question last. */
  private static String transcript(List<AssistantMessage> earlier, String question) {
    StringBuilder text = new StringBuilder();
    for (AssistantMessage message : oneAnswerEach(earlier)) {
      text.append(AssistantMessage.USER.equals(message.getRole()) ? "Student: " : "Assistant: ")
          .append(trimmed(message.getContent()))
          .append("\n\n");
    }
    return text.append("Student: ").append(trimmed(question)).toString();
  }

  private static String trimmed(String text) {
    return text.length() > 8000 ? text.substring(0, 8000) : text;
  }

  private static ConversationResponse respond(AssistantConversation chat) {
    return new ConversationResponse(
        chat.getId(), chat.getTitle(), chat.getSource(), chat.getUpdatedAt());
  }

  private static MessageResponse respond(AssistantMessage message) {
    return new MessageResponse(
        message.getId(),
        message.getRole(),
        message.getContent(),
        message.getCreatedAt(),
        message.getStyle(),
        message.getReplyTo());
  }
}
