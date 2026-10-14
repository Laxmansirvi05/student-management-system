package com.campuscompanion.resource;

import java.util.Collection;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookmarkRepository extends JpaRepository<Bookmark, Bookmark.Key> {

  /**
   * Adds a bookmark unless it is already there. It is one statement, so two requests at the same
   * moment cannot both try to insert it and make one of them fail.
   */
  @Modifying
  @Query(
      value =
          "insert into app.bookmarks (student_id, resource_id) values (:studentId, :resourceId)"
              + " on conflict (student_id, resource_id) do nothing",
      nativeQuery = true)
  void addIfMissing(@Param("studentId") Long studentId, @Param("resourceId") Long resourceId);

  /** Which of these resources the student has bookmarked. */
  @Query(
      "select b.resourceId from Bookmark b"
          + " where b.studentId = :studentId and b.resourceId in :resourceIds")
  Set<Long> bookmarkedAmong(
      @Param("studentId") Long studentId, @Param("resourceIds") Collection<Long> resourceIds);
}
