package com.campuscompanion.notice;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

  /**
   * Published notices a reader may see: those for everyone, and those targeted at the reader's
   * branch and semester. A null branch or semester means the reader sees every target (an admin). A
   * null category means "any". Drafts never match.
   */
  @Query(
      """
      select n from Notice n
      where n.status = com.campuscompanion.notice.NoticeStatus.PUBLISHED
        and (:branch is null or n.targetBranch is null or n.targetBranch = :branch)
        and (:semester is null or n.targetSemester is null or n.targetSemester = :semester)
        and (:category is null or n.category = :category)
      """)
  Page<Notice> findVisible(
      @Param("branch") String branch,
      @Param("semester") Short semester,
      @Param("category") NoticeCategory category,
      Pageable pageable);

  /** One published notice, if the reader may see it. */
  @Query(
      """
      select n from Notice n
      where n.id = :id
        and n.status = com.campuscompanion.notice.NoticeStatus.PUBLISHED
        and (:branch is null or n.targetBranch is null or n.targetBranch = :branch)
        and (:semester is null or n.targetSemester is null or n.targetSemester = :semester)
      """)
  Optional<Notice> findVisibleById(
      @Param("id") Long id, @Param("branch") String branch, @Param("semester") Short semester);

  /** Published exam notices a student may see, whatever their dates. */
  @Query(
      """
      select n from Notice n
      where n.status = com.campuscompanion.notice.NoticeStatus.PUBLISHED
        and n.category = com.campuscompanion.notice.NoticeCategory.EXAM
        and (n.targetBranch is null or n.targetBranch = :branch)
        and (n.targetSemester is null or n.targetSemester = :semester)
      order by n.id
      """)
  List<Notice> findVisibleExamNotices(
      @Param("branch") String branch, @Param("semester") Short semester);

  /** Published, visible notices whose due date falls in the given days, soonest first. */
  @Query(
      """
      select n from Notice n
      where n.status = com.campuscompanion.notice.NoticeStatus.PUBLISHED
        and (n.targetBranch is null or n.targetBranch = :branch)
        and (n.targetSemester is null or n.targetSemester = :semester)
        and n.dueDate between :from and :to
      order by n.dueDate, n.id
      """)
  List<Notice> findDeadlines(
      @Param("branch") String branch,
      @Param("semester") Short semester,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to);
}
