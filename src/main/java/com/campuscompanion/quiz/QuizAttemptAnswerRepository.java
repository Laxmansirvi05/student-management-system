package com.campuscompanion.quiz;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizAttemptAnswerRepository extends JpaRepository<QuizAttemptAnswer, Long> {

  List<QuizAttemptAnswer> findByAttemptIdOrderByPosition(Long attemptId);

  /** True once the question has been served in any test. */
  boolean existsByQuestionId(Long questionId);

  /** Of the given questions, the IDs of those that have been served in a test. */
  @Query("select distinct a.questionId from QuizAttemptAnswer a where a.questionId in :questionIds")
  List<Long> usedAmong(@Param("questionIds") List<Long> questionIds);

  /**
   * Every question a student answered in a submitted test, newest test first. A question left
   * unanswered in a submitted test is in the list, marked wrong. Expired tests are not.
   */
  @Query(
      """
      select new com.campuscompanion.quiz.AnsweredQuestion(a.subjectId, a.unitId, answer.correct)
      from QuizAttemptAnswer answer, QuizAttempt a
      where answer.attemptId = a.id
        and a.studentId = :studentId
        and a.status = com.campuscompanion.quiz.AttemptStatus.SUBMITTED
      order by a.submittedAt desc, a.id desc, answer.position asc
      """)
  List<AnsweredQuestion> findAnsweredBy(@Param("studentId") Long studentId);
}
