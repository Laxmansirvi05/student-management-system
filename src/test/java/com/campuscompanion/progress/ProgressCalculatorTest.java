package com.campuscompanion.progress;

import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.progress.ProgressCalculator.UnitPerformance;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The progress rule at its boundaries: 5 answers, 60%, 80%, and the 20 most recent answers. */
class ProgressCalculatorTest {

  /** A list of answers, newest first: first the right ones, then the wrong ones. */
  private static List<Boolean> answers(int right, int wrong) {
    List<Boolean> answers = new ArrayList<>(Collections.nCopies(right, true));
    answers.addAll(Collections.nCopies(wrong, false));
    return answers;
  }

  @Test
  void noAnswersMeansNoAccuracyAndNotEnoughData() {
    UnitPerformance performance = ProgressCalculator.of(List.of());

    assertThat(performance.answered()).isZero();
    assertThat(performance.accuracyPercent()).isNull();
    assertThat(performance.level()).isEqualTo(PerformanceLevel.NOT_ENOUGH_DATA);
  }

  @Test
  void fourAnswersAreNotEnoughEvenWhenAllAreRight() {
    UnitPerformance performance = ProgressCalculator.of(answers(4, 0));

    assertThat(performance.accuracyPercent()).isEqualByComparingTo("100.00");
    assertThat(performance.level()).isEqualTo(PerformanceLevel.NOT_ENOUGH_DATA);
  }

  @Test
  void fiveAnswersAreEnoughToJudge() {
    assertThat(ProgressCalculator.of(answers(5, 0)).level()).isEqualTo(PerformanceLevel.STRONG);
    assertThat(ProgressCalculator.of(answers(0, 5)).level())
        .isEqualTo(PerformanceLevel.NEEDS_REVISION);
  }

  @Test
  void underSixtyPercentNeedsRevisionAndExactlySixtyIsImproving() {
    // 59 of 100 would be under 60, but only 20 answers count: 11 of 20 is 55%.
    assertThat(ProgressCalculator.of(answers(11, 9)).level())
        .isEqualTo(PerformanceLevel.NEEDS_REVISION);
    // 12 of 20 is exactly 60%.
    UnitPerformance atSixty = ProgressCalculator.of(answers(12, 8));
    assertThat(atSixty.accuracyPercent()).isEqualByComparingTo("60.00");
    assertThat(atSixty.level()).isEqualTo(PerformanceLevel.IMPROVING);
  }

  @Test
  void exactlyEightyPercentIsStrongAndJustUnderIsImproving() {
    // 16 of 20 is exactly 80%.
    UnitPerformance atEighty = ProgressCalculator.of(answers(16, 4));
    assertThat(atEighty.accuracyPercent()).isEqualByComparingTo("80.00");
    assertThat(atEighty.level()).isEqualTo(PerformanceLevel.STRONG);
    // 15 of 20 is 75%.
    assertThat(ProgressCalculator.of(answers(15, 5)).level()).isEqualTo(PerformanceLevel.IMPROVING);
  }

  @Test
  void accuracyIsRoundedHalfUpToTwoDecimals() {
    // 2 of 3 is 66.666...%, and 1 of 3 is 33.333...%.
    assertThat(ProgressCalculator.of(answers(2, 1)).accuracyPercent())
        .isEqualByComparingTo("66.67");
    assertThat(ProgressCalculator.of(answers(1, 2)).accuracyPercent())
        .isEqualByComparingTo("33.33");
  }

  @Test
  void onlyTheTwentyMostRecentAnswersCount() {
    // Newest first: 20 right answers, then 30 older wrong ones that no longer count.
    UnitPerformance performance = ProgressCalculator.of(answers(20, 30));

    assertThat(performance.answered()).isEqualTo(20);
    assertThat(performance.correct()).isEqualTo(20);
    assertThat(performance.level()).isEqualTo(PerformanceLevel.STRONG);
  }

  @Test
  void olderRightAnswersDoNotHideARecentBadRun() {
    // Newest first: 20 wrong answers, then 30 older right ones.
    List<Boolean> answers = new ArrayList<>(Collections.nCopies(20, false));
    answers.addAll(Collections.nCopies(30, true));

    UnitPerformance performance = ProgressCalculator.of(answers);

    assertThat(performance.correct()).isZero();
    assertThat(performance.level()).isEqualTo(PerformanceLevel.NEEDS_REVISION);
  }
}
