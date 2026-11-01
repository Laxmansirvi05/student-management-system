package com.campuscompanion.assistant;

import com.campuscompanion.common.error.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The storage side of the assistant chats, in short transactions of their own. The call to the AI
 * provider takes seconds, so it is never made inside one: a chat is read first, the answer is
 * fetched, and only then are both messages written together.
 */
@Component
class AssistantHistory {

  static final int QUICK_CHATS_KEPT = 6;
  static final int TITLE_MAX = 60;

  private final AssistantConversationRepository conversations;
  private final AssistantMessageRepository messages;
  private final Clock clock;

  AssistantHistory(
      AssistantConversationRepository conversations,
      AssistantMessageRepository messages,
      Clock clock) {
    this.conversations = conversations;
    this.messages = messages;
    this.clock = clock;
  }

  /** The student's chat, or not found. */
  @Transactional(readOnly = true)
  AssistantConversation owned(Long studentId, Long conversationId) {
    return conversations
        .findByIdAndStudentId(conversationId, studentId)
        .orElseThrow(ResourceNotFoundException::new);
  }

  /** The last messages of a chat in the order they were written. */
  @Transactional(readOnly = true)
  List<AssistantMessage> recent(Long conversationId, int count) {
    List<AssistantMessage> found =
        new ArrayList<>(messages.newest(conversationId, PageRequest.of(0, count)));
    Collections.reverse(found);
    return found;
  }

  /** The messages before one message, in the order they were written. */
  @Transactional(readOnly = true)
  List<AssistantMessage> before(Long conversationId, Long messageId, int count) {
    List<AssistantMessage> found =
        new ArrayList<>(messages.newestBefore(conversationId, messageId, PageRequest.of(0, count)));
    Collections.reverse(found);
    return found;
  }

  /** A question of the chat, or not found. */
  @Transactional(readOnly = true)
  AssistantMessage question(Long conversationId, Long questionId) {
    return messages
        .findByIdAndConversationIdAndRole(questionId, conversationId, AssistantMessage.USER)
        .orElseThrow(ResourceNotFoundException::new);
  }

  /** The saved answer of a question in a mode, if there is one. */
  @Transactional(readOnly = true)
  java.util.Optional<AssistantMessage> answer(Long questionId, String style) {
    return messages.findByReplyToAndStyle(questionId, style);
  }

  @Transactional(readOnly = true)
  List<AssistantMessage> all(Long conversationId) {
    return messages.findByConversationIdOrderByIdAsc(conversationId);
  }

  @Transactional(readOnly = true)
  List<AssistantConversation> list(Long studentId, String source, int limit) {
    return conversations.newest(studentId, source, PageRequest.of(0, limit));
  }

  /**
   * Writes the question and the answer. A new chat is created first (titled with the start of the
   * question), and a chat from the robot keeps only the newest few: older ones are deleted.
   */
  @Transactional
  Saved save(
      Long studentId,
      Long conversationId,
      String source,
      String question,
      String answer,
      String style) {
    Instant now = clock.instant();
    AssistantConversation chat;
    if (conversationId == null) {
      String title = question.length() > TITLE_MAX ? question.substring(0, TITLE_MAX) : question;
      chat = conversations.save(new AssistantConversation(studentId, title, source, now));
    } else {
      chat = owned(studentId, conversationId);
      chat.touch(now);
    }
    AssistantMessage asked =
        messages.save(new AssistantMessage(chat.getId(), AssistantMessage.USER, question, now));
    AssistantMessage replied =
        messages.save(new AssistantMessage(chat.getId(), answer, now, asked.getId(), style));
    if (AssistantConversation.QUICK.equals(chat.getSource())) {
      List<AssistantConversation> all =
          conversations.newest(studentId, chat.getSource(), PageRequest.of(0, 100));
      for (int i = QUICK_CHATS_KEPT; i < all.size(); i++) {
        conversations.delete(all.get(i));
      }
    }
    return new Saved(chat, asked, replied);
  }

  /**
   * Writes one more answer to a question already in the chat, in another mode. The unique index on
   * the question and the mode refuses a second answer of the same mode.
   */
  @Transactional
  Saved saveAnswer(
      Long studentId, Long conversationId, Long questionId, String answer, String style) {
    AssistantConversation chat = owned(studentId, conversationId);
    chat.touch(clock.instant());
    AssistantMessage asked = question(chat.getId(), questionId);
    AssistantMessage replied =
        messages.saveAndFlush(
            new AssistantMessage(chat.getId(), answer, clock.instant(), asked.getId(), style));
    return new Saved(chat, asked, replied);
  }

  @Transactional
  void delete(Long studentId, Long conversationId) {
    conversations.delete(owned(studentId, conversationId));
  }

  record Saved(AssistantConversation chat, AssistantMessage question, AssistantMessage reply) {}
}
