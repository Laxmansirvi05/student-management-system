package com.campuscompanion.assistant;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssistantMessageRepository extends JpaRepository<AssistantMessage, Long> {

  List<AssistantMessage> findByConversationIdOrderByIdAsc(Long conversationId);

  /** The newest messages of a chat, newest first. */
  @Query("select m from AssistantMessage m where m.conversationId = :id order by m.id desc")
  List<AssistantMessage> newest(@Param("id") Long conversationId, Pageable limit);
}
