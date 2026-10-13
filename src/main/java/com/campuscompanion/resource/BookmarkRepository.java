package com.campuscompanion.resource;

import java.util.Collection;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookmarkRepository extends JpaRepository<Bookmark, Bookmark.Key> {

  /** Which of these resources the student has bookmarked. */
  @Query(
      "select b.resourceId from Bookmark b"
          + " where b.studentId = :studentId and b.resourceId in :resourceIds")
  Set<Long> bookmarkedAmong(
      @Param("studentId") Long studentId, @Param("resourceIds") Collection<Long> resourceIds);
}
