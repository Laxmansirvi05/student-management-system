package com.campuscompanion.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campuscompanion.common.error.ValidationException;
import com.campuscompanion.quiz.QuizEvaluationService.Evaluation;
import com.campuscompanion.quiz.QuizEvaluationService.MarkedAnswer;
import com.campuscompanion.quiz.QuizEvaluationService.ServedQuestion;
import com.campuscompanion.quiz.QuizEvaluationService.SubmittedAnswer;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The marking rule of Quick Exam, with no database and no Spring (task T4-04). */
class QuizEvaluationServiceTest {

  private static final List<ServedQuestion> SERVED =
      List.of(
          new ServedQuestion(1L, OptionKey.A),
          new ServedQuestion(2L, OptionKey.B),
          new ServedQuestion(3L, OptionKey.C));

  private final QuizEvaluationService evaluator = new QuizEvaluationService();

  @Test
  void everyRightAnswerScoresOne() {
    Evaluation evaluation =
        evaluator.evaluate(
            SERVED,
            List.of(
                new SubmittedAnswer(1L, OptionKey.A),
                new SubmittedAnswer(2L, OptionKey.B),
                new SubmittedAnswer(3L, OptionKey.C)));

    assertThat(evaluation.score()).isEqualTo(3);
    assertThat(evaluation.answers()).allMatch(MarkedAnswer::correct);
  }

  @Test
  void noRightAnswerScoresZero() {
    Evaluation evaluation =
        evaluator.evaluate(
            SERVED,
            List.of(
                new SubmittedAnswer(1L, OptionKey.D),
                new SubmittedAnswer(2L, OptionKey.D),
                new SubmittedAnswer(3L, OptionKey.D)));

    assertThat(evaluation.score()).isZero();
    assertThat(evaluation.answers()).noneMatch(MarkedAnswer::correct);
  }

  @Test
  void aPartlyRightTestIsMarkedQuestionByQuestionInTheOrderServed() {
    // The answers arrive in another order than the questions were served.
    Evaluation evaluation =
        evaluator.evaluate(
            SERVED,
            List.of(
                new SubmittedAnswer(3L, OptionKey.C),
                new SubmittedAnswer(1L, OptionKey.B),
                new SubmittedAnswer(2L, OptionKey.B)));

    assertThat(evaluation.score()).isEqualTo(2);
    assertThat(evaluation.answers())
        .containsExactly(
            new MarkedAnswer(1L, OptionKey.B, false),
            new MarkedAnswer(2L, OptionKey.B, true),
            new MarkedAnswer(3L, OptionKey.C, true));
  }

  @Test
  void aQuestionLeftOutOrSentWithoutAnOptionIsWrong() {
    Evaluation evaluation =
        evaluator.evaluate(
            SERVED, List.of(new SubmittedAnswer(1L, OptionKey.A), new SubmittedAnswer(2L, null)));

    assertThat(evaluation.score()).isEqualTo(1);
    assertThat(evaluation.answers())
        .containsExactly(
            new MarkedAnswer(1L, OptionKey.A, true),
            new MarkedAnswer(2L, null, false),
            new MarkedAnswer(3L, null, false));
  }

  @Test
  void nothingSubmittedScoresZero() {
    Evaluation evaluation = evaluator.evaluate(SERVED, List.of());

    assertThat(evaluation.score()).isZero();
    assertThat(evaluation.answers()).hasSize(3).noneMatch(MarkedAnswer::correct);
  }

  @Test
  void anAnswerForAQuestionThatIsNotPartOfTheTestIsRefused() {
    assertThatThrownBy(
            () ->
                evaluator.evaluate(
                    SERVED,
                    List.of(
                        new SubmittedAnswer(1L, OptionKey.A),
                        new SubmittedAnswer(99L, OptionKey.A))))
        .isInstanceOfSatisfying(
            ValidationException.class,
            failure ->
                assertThat(failure.getErrors().get(0).field()).isEqualTo("answers[1].questionId"));
  }

  @Test
  void twoAnswersForOneQuestionAreRefused() {
    assertThatThrownBy(
            () ->
                evaluator.evaluate(
                    SERVED,
                    List.of(
                        new SubmittedAnswer(1L, OptionKey.B),
                        new SubmittedAnswer(1L, OptionKey.A))))
        .isInstanceOfSatisfying(
            ValidationException.class,
            failure ->
                assertThat(failure.getErrors().get(0).field()).isEqualTo("answers[1].questionId"));
  }
}
