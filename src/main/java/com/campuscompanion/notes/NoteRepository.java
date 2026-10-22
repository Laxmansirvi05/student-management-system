package com.campuscompanion.notes;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoteRepository extends JpaRepository<Note, Long>, JpaSpecificationExecutor<Note> {

  Optional<Note> findByIdAndStudentId(Long id, Long studentId);

  List<Note> findByIdInAndStudentId(Collection<Long> ids, Long studentId);

  /** The notes in the Trash, for emptying it. */
  List<Note> findByStudentIdAndDeletedAtIsNotNull(Long studentId);

  /** Notes by id that are not in the Trash, for the "shared with me" view. */
  List<Note> findByIdInAndDeletedAtIsNull(Collection<Long> ids);

  /** How many notes of each folder are shown in it: not trashed, not archived. */
  @Query(
      """
      select n.folderId, count(n) from Note n
      where n.studentId = :studentId and n.deletedAt is null and n.archived = false
        and n.folderId is not null
      group by n.folderId
      """)
  List<Object[]> countsPerFolder(@Param("studentId") Long studentId);

  /**
   * The newest notes of a student, cut down to what a folder card shows: the first 400 characters
   * of the document and the address of its first picture. Rows are id, folder id, title, preview,
   * first image, update time and pinned.
   */
  @Query(
      value =
          """
          select n.id, n.folder_id, n.title, left(n.content, 400),
                 substring(n.content from '<img[^>]+src="([^"]+)"'), n.updated_at, n.pinned
          from app.notes n
          where n.student_id = :studentId and n.deleted_at is null and n.archived = false
          order by n.pinned desc, n.updated_at desc
          limit :scan
          """,
      nativeQuery = true)
  List<Object[]> previewRows(@Param("studentId") Long studentId, @Param("scan") int scan);

  @Modifying
  @Query("update Note n set n.folderId = null where n.folderId = :folderId")
  void unfile(@Param("folderId") Long folderId);

  @Modifying
  @Query(
      """
      update Note n set n.deletedAt = :now, n.updatedAt = :now
      where n.studentId = :studentId and n.id in :ids and n.deletedAt is null
      """)
  int trash(
      @Param("studentId") Long studentId,
      @Param("ids") Collection<Long> ids,
      @Param("now") Instant now);
}
