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
import com.campuscompanion.ai.provider.AiProvider;
import com.campuscompanion.ai.provider.AiProviderException;
import com.campuscompanion.ai.provider.AiRequest;
import com.campuscompanion.assistant.dto.ChatRequest;
import com.campuscompanion.assistant.dto.ChatResponse;
import com.campuscompanion.assistant.dto.ConversationListResponse;
import com.campuscompanion.assistant.dto.ConversationResponse;
import com.campuscompanion.assistant.dto.MessageListResponse;
import com.campuscompanion.assistant.dto.MessageResponse;
import com.campuscompanion.common.config.AiProperties;
import com.campuscompanion.common.error.ValidationException;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.student.StudentProfileService;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
  static final int HISTORY_LIST = 7;
  private static final String UNAVAILABLE = "The assistant is unavailable right now.";
  private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

  private final AssistantHistory history;
  private final AiUsageService usage;
  private final AiProvider provider;
  private final PromptLibrary templates;
  private final PromptBuilder prompts;
  private final AiProperties properties;
  private final StudentProfileService students;

  public AssistantService(
      AssistantHistory history,
      AiUsageService usage,
      AiProvider provider,
      PromptLibrary templates,
      PromptBuilder prompts,
      AiProperties properties,
      StudentProfileService students) {
    this.history = history;
    this.usage = usage;
    this.provider = provider;
    this.templates = templates;
    this.prompts = prompts;
    this.properties = properties;
    this.students = students;
  }

  /**
   * Answers one message. The earlier messages of the chat, up to twenty in all, are sent with it,
   * so the model can follow the thread; it remembers nothing itself. Nothing is stored when the
   * answer fails, so a retry does not leave the question twice.
   */
  public ChatResponse chat(CurrentUser user, ChatRequest request) {
    Long studentId = students.currentStudent(user).studentId();
    String question = request.message() == null ? "" : request.message().strip();
    if (question.isEmpty()) {
      throw new ValidationException("message", "Write a message.");
    }
    String source = "quick".equals(request.mode()) ? AssistantConversation.QUICK : "assistant";
    List<AssistantMessage> earlier = List.of();
    if (request.conversationId() != null) {
      AssistantConversation chat = history.owned(studentId, request.conversationId());
      source = chat.getSource();
      earlier = history.recent(chat.getId(), CONTEXT_MESSAGES - 1);
    }

    String answer = ask(user, transcript(earlier, question));
    AssistantHistory.Saved saved =
        history.save(studentId, request.conversationId(), source, question, answer);
    return new ChatResponse(
        respond(saved.chat()), respond(saved.question()), respond(saved.reply()), NOTICE);
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
  private String ask(CurrentUser user, String conversation) {
    usage.begin(user.id());
    AiRequest prompt =
        prompts.build(
            templates.quickChat(),
            Map.of("conversation", conversation),
            properties.maxOutputTokens(),
            properties.responseTimeout());
    long started = System.nanoTime();
    Integer outputChars = null;
    AiRequestStatus status = AiRequestStatus.FAILED;
    try {
      // With several models configured, an empty reply sends the request on to the next model.
      AiCompletion completion =
          provider.complete(prompt, reply -> reply != null && !reply.isBlank());
      String text = completion.text() == null ? "" : completion.text().strip();
      outputChars = text.length();
      if (text.isEmpty()) {
        throw new AiInvalidResponseException(UNAVAILABLE);
      }
      status = AiRequestStatus.SUCCESS;
      return text.length() > REPLY_MAX ? text.substring(0, REPLY_MAX) : text;
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

  /** The chat as text for the model: each message under its speaker, the new question last. */
  private static String transcript(List<AssistantMessage> earlier, String question) {
    StringBuilder text = new StringBuilder();
    for (AssistantMessage message : earlier) {
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
        message.getId(), message.getRole(), message.getContent(), message.getCreatedAt());
  }
}
