package com.campuscompanion.quiz;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

  /** Whether a subject already has a question with exactly this text. */
  boolean existsBySubjectIdAndQuestionText(Long subjectId, String questionText);

  /** The IDs of the questions of a unit that may be used in a test. */
  @Query(
      """
      select q.id from QuizQuestion q
      where q.unitId = :unitId
        and q.status = com.campuscompanion.quiz.QuestionStatus.PUBLISHED
      """)
  List<Long> findPublishedIds(@Param("unitId") Long unitId);

  /** The IDs of every question of a subject that may be used in a test, whatever its unit. */
  @Query(
      """
      select q.id from QuizQuestion q
      where q.subjectId = :subjectId
        and q.status = com.campuscompanion.quiz.QuestionStatus.PUBLISHED
      """)
  List<Long> findPublishedIdsOfSubject(@Param("subjectId") Long subjectId);

  /** The question bank for admins. Every filter is optional (null means "any"). */
  @Query(
      """
      select q from QuizQuestion q
      where (:subjectId is null or q.subjectId = :subjectId)
        and (:unitId is null or q.unitId = :unitId)
        and (:status is null or q.status = :status)
      """)
  Page<QuizQuestion> search(
      @Param("subjectId") Long subjectId,
      @Param("unitId") Long unitId,
      @Param("status") QuestionStatus status,
      Pageable pageable);
}
