package com.campuscompanion.notes;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoteLinkRepository extends JpaRepository<NoteLink, Long> {

  List<NoteLink> findBySourceNoteIdAndStudentIdOrderByIdAsc(Long sourceNoteId, Long studentId);

  Optional<NoteLink> findBySourceNoteIdAndTargetTypeAndTargetId(
      Long sourceNoteId, String targetType, Long targetId);

  Optional<NoteLink> findByIdAndSourceNoteIdAndStudentId(
      Long id, Long sourceNoteId, Long studentId);
}
