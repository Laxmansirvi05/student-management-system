package com.campuscompanion.planner;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** The planner's date rules: overdue, past, and which dates may be chosen. */
class PlannerRulesTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 24);

  @Test
  void aPendingTaskIsOverdueOnlyAfterItsDueDay() {
    assertThat(PlannerRules.isOverdue(TaskStatus.PENDING, TODAY.minusDays(1), TODAY)).isTrue();
    assertThat(PlannerRules.isOverdue(TaskStatus.PENDING, TODAY, TODAY)).isFalse();
    assertThat(PlannerRules.isOverdue(TaskStatus.PENDING, TODAY.plusDays(1), TODAY)).isFalse();
    assertThat(PlannerRules.isOverdue(TaskStatus.PENDING, null, TODAY)).isFalse();
    assertThat(PlannerRules.isOverdue(TaskStatus.DONE, TODAY.minusDays(30), TODAY)).isFalse();
  }

  @Test
  void anExamIsPastFromTheDayAfterIt() {
    assertThat(PlannerRules.isPast(TODAY.minusDays(1), TODAY)).isTrue();
    assertThat(PlannerRules.isPast(TODAY, TODAY)).isFalse();
    assertThat(PlannerRules.isPast(TODAY.plusDays(1), TODAY)).isFalse();
  }

  @Test
  void aNewDateMustBeFromTodayToFiveYearsAhead() {
    assertThat(PlannerRules.problemWithDate(TODAY, null, TODAY)).isNull();
    assertThat(PlannerRules.problemWithDate(TODAY.plusYears(5), null, TODAY)).isNull();
    assertThat(PlannerRules.problemWithDate(null, null, TODAY)).isNull();
    assertThat(PlannerRules.problemWithDate(TODAY.minusDays(1), null, TODAY))
        .isEqualTo("Choose today or a later date.");
    assertThat(PlannerRules.problemWithDate(TODAY.plusYears(5).plusDays(1), null, TODAY))
        .isEqualTo("Choose a date within the next five years.");
  }

  @Test
  void anUnchangedPastDateIsKeptSoAnOverdueTaskCanStillBeEdited() {
    LocalDate past = TODAY.minusDays(10);
    assertThat(PlannerRules.problemWithDate(past, past, TODAY)).isNull();
    assertThat(PlannerRules.problemWithDate(TODAY.minusDays(9), past, TODAY)).isNotNull();
  }
}
