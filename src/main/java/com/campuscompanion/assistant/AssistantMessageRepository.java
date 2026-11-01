package com.campuscompanion.assistant;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssistantMessageRepository extends JpaRepository<AssistantMessage, Long> {

  List<AssistantMessage> findByConversationIdOrderByIdAsc(Long conversationId);

  /** The newest messages of a chat before one message, newest first. */
  @Query(
      "select m from AssistantMessage m where m.conversationId = :id and m.id < :before order by m.id desc")
  List<AssistantMessage> newestBefore(
      @Param("id") Long conversationId, @Param("before") Long before, Pageable limit);

  /** The saved answer of a question in one mode, if there is one. */
  java.util.Optional<AssistantMessage> findByReplyToAndStyle(Long replyTo, String style);

  /** A question of a chat, by its ID. */
  java.util.Optional<AssistantMessage> findByIdAndConversationIdAndRole(
      Long id, Long conversationId, String role);

  /** The newest messages of a chat, newest first. */
  @Query("select m from AssistantMessage m where m.conversationId = :id order by m.id desc")
  List<AssistantMessage> newest(@Param("id") Long conversationId, Pageable limit);
}
