package com.campuscompanion.assistant;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssistantConversationRepository
    extends JpaRepository<AssistantConversation, Long> {

  Optional<AssistantConversation> findByIdAndStudentId(Long id, Long studentId);

  @Query(
      """
      select c from AssistantConversation c
      where c.studentId = :studentId and c.source = :source
      order by c.updatedAt desc, c.id desc
      """)
  List<AssistantConversation> newest(
      @Param("studentId") Long studentId, @Param("source") String source, Pageable limit);
}
