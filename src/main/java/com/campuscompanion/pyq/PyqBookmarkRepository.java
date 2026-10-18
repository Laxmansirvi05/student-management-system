package com.campuscompanion.pyq;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PyqBookmarkRepository extends JpaRepository<PyqBookmark, PyqBookmark.Key> {

  /** Saving twice does nothing, even when two requests arrive together. */
  @Modifying
  @Query(
      value =
          "insert into app.pyq_bookmarks (student_id, paper_id) values (:studentId, :paperId)"
              + " on conflict (student_id, paper_id) do nothing",
      nativeQuery = true)
  void addIfMissing(@Param("studentId") Long studentId, @Param("paperId") Long paperId);

  /** Which of these papers the student has saved. */
  @Query("select b.paperId from PyqBookmark b where b.studentId = :studentId and b.paperId in :ids")
  List<Long> savedAmong(@Param("studentId") Long studentId, @Param("ids") Collection<Long> ids);

  /** Used before a paper is deleted, so no bookmark is left pointing at it. */
  @Modifying
  @Query("delete from PyqBookmark b where b.paperId = :paperId")
  void deleteAllOf(@Param("paperId") Long paperId);
}
