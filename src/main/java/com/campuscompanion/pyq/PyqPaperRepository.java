package com.campuscompanion.pyq;

import com.campuscompanion.pyq.dto.PyqSubjectResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PyqPaperRepository extends JpaRepository<PyqPaper, Long> {

  /**
   * The paper search. Every filter is optional (null means "any") and all the filters given must
   * match. The search words (at most three, see {@link PyqSearchTerms}) must each appear in the
   * subject, the course code or the year.
   */
  @Query(
      """
      select p from PyqPaper p
      where (:status is null or p.status = :status)
        and (:branch is null or p.branch = :branch)
        and (:subject is null or p.subjectName = :subject)
        and (:year is null or p.examYear = :year)
        and (:kind is null or p.examKind = :kind)
        and (:regulation is null or p.regulation = :regulation)
        and (:t1 is null or lower(concat(p.subjectName, ' ', coalesce(p.courseCode, ''), ' ', cast(p.examYear as string))) like :t1 escape '\\')
        and (:t2 is null or lower(concat(p.subjectName, ' ', coalesce(p.courseCode, ''), ' ', cast(p.examYear as string))) like :t2 escape '\\')
        and (:t3 is null or lower(concat(p.subjectName, ' ', coalesce(p.courseCode, ''), ' ', cast(p.examYear as string))) like :t3 escape '\\')
      """)
  Page<PyqPaper> search(
      @Param("status") PyqStatus status,
      @Param("branch") String branch,
      @Param("subject") String subject,
      @Param("year") Integer year,
      @Param("kind") PyqExamKind kind,
      @Param("regulation") String regulation,
      @Param("t1") String t1,
      @Param("t2") String t2,
      @Param("t3") String t3,
      Pageable pageable);

  /** The published papers a student has saved. A hidden paper drops out by itself. */
  @Query(
      """
      select p from PyqPaper p
      where p.status = com.campuscompanion.pyq.PyqStatus.PUBLISHED
        and p.id in (select b.paperId from PyqBookmark b where b.studentId = :studentId)
      """)
  Page<PyqPaper> findSaved(@Param("studentId") Long studentId, Pageable pageable);

  /** One card per subject of the published papers, with how many papers and from which years. */
  @Query(
      """
      select new com.campuscompanion.pyq.dto.PyqSubjectResponse(
          p.branch, p.subjectName, count(p), min(p.examYear), max(p.examYear))
      from PyqPaper p
      where p.status = com.campuscompanion.pyq.PyqStatus.PUBLISHED
        and (:branch is null or p.branch = :branch)
        and (:t1 is null or lower(concat(p.subjectName, ' ', coalesce(p.courseCode, ''))) like :t1 escape '\\')
        and (:t2 is null or lower(concat(p.subjectName, ' ', coalesce(p.courseCode, ''))) like :t2 escape '\\')
        and (:t3 is null or lower(concat(p.subjectName, ' ', coalesce(p.courseCode, ''))) like :t3 escape '\\')
      group by p.branch, p.subjectName
      order by p.subjectName, p.branch
      """)
  List<PyqSubjectResponse> subjects(
      @Param("branch") String branch,
      @Param("t1") String t1,
      @Param("t2") String t2,
      @Param("t3") String t3);

  /** Branch, subject count and paper count, for the branch chips. */
  @Query(
      """
      select p.branch, count(distinct p.subjectName), count(p) from PyqPaper p
      where p.status = com.campuscompanion.pyq.PyqStatus.PUBLISHED
      group by p.branch order by p.branch
      """)
  List<Object[]> branchCounts();

  @Query(
      """
      select distinct p.examYear from PyqPaper p
      where p.status = com.campuscompanion.pyq.PyqStatus.PUBLISHED order by p.examYear desc
      """)
  List<Integer> years();

  @Query(
      """
      select distinct p.regulation from PyqPaper p
      where p.status = com.campuscompanion.pyq.PyqStatus.PUBLISHED and p.regulation is not null
      order by p.regulation
      """)
  List<String> regulations();

  Optional<PyqPaper> findByChecksumSha256(String checksumSha256);

  /** The paper that already holds this sitting, regulation and question set, if any. */
  @Query(
      """
      select p from PyqPaper p
      where p.branch = :branch and lower(p.subjectName) = lower(:subject)
        and p.examYear = :year and p.examMonth = :month and p.examKind = :kind
        and coalesce(p.midNumber, 0) = :mid and coalesce(p.regulation, '') = :regulation
        and p.variant = :variant
      """)
  Optional<PyqPaper> findSameSitting(
      @Param("branch") String branch,
      @Param("subject") String subject,
      @Param("year") int year,
      @Param("month") int month,
      @Param("kind") PyqExamKind kind,
      @Param("mid") int mid,
      @Param("regulation") String regulation,
      @Param("variant") int variant);

  /** Counts a download. One statement, so two downloads at the same moment are both counted. */
  @Modifying
  @Query("update PyqPaper p set p.downloadCount = p.downloadCount + 1 where p.id = :id")
  void countDownload(@Param("id") Long id);
}
