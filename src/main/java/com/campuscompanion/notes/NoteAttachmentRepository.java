package com.campuscompanion.notes;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoteAttachmentRepository extends JpaRepository<NoteAttachment, Long> {

  Optional<NoteAttachment> findByIdAndStudentId(Long id, Long studentId);

  List<NoteAttachment> findByNoteIdAndStudentId(Long noteId, Long studentId);

  List<NoteAttachment> findByNoteId(Long noteId);

  List<NoteAttachment> findByNoteIdIn(Collection<Long> noteIds);

  long countByStoragePath(String storagePath);

  /** A student's attachments, newest first. The kind is optional. */
  @Query(
      """
      select a from NoteAttachment a
      where a.studentId = :studentId and (:kind is null or a.kind = :kind)
      order by a.createdAt desc, a.id desc
      """)
  List<NoteAttachment> forStudent(
      @Param("studentId") Long studentId, @Param("kind") String kind, Pageable limit);
}
