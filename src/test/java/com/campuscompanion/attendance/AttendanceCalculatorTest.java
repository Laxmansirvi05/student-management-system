package com.campuscompanion.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Task T2-05: the attendance rules (FR-ATT-001, FR-ATT-002), without Spring or a database. */
class AttendanceCalculatorTest {

  private static final BigDecimal REQUIRED = new BigDecimal("75");

  @Test
  void thereIsNoPercentageWhenNoClassHasBeenHeld() {
    assertThat(AttendanceCalculator.percent(0, 0)).isNull();
  }

  @Test
  void percentageHasTwoDecimalsAndRoundsHalfUp() {
    assertThat(AttendanceCalculator.percent(33, 40)).isEqualTo(new BigDecimal("82.50"));
    assertThat(AttendanceCalculator.percent(2, 3)).isEqualTo(new BigDecimal("66.67"));
    assertThat(AttendanceCalculator.percent(1, 3)).isEqualTo(new BigDecimal("33.33"));
    assertThat(AttendanceCalculator.percent(1, 6)).isEqualTo(new BigDecimal("16.67"));
    // 5 of 800 is 0.625%: the half rounds up, not down.
    assertThat(AttendanceCalculator.percent(5, 800)).isEqualTo(new BigDecimal("0.63"));
    assertThat(AttendanceCalculator.percent(0, 12)).isEqualTo(new BigDecimal("0.00"));
    assertThat(AttendanceCalculator.percent(12, 12)).isEqualTo(new BigDecimal("100.00"));
  }

  @Test
  void aSubjectIsBelowRequiredOnlyWhenItIsLowerThanTheRequiredPercentage() {
    assertThat(AttendanceCalculator.isBelowRequired(new BigDecimal("74.99"), REQUIRED)).isTrue();
    assertThat(AttendanceCalculator.isBelowRequired(new BigDecimal("75.00"), REQUIRED)).isFalse();
    assertThat(AttendanceCalculator.isBelowRequired(new BigDecimal("75.01"), REQUIRED)).isFalse();
    assertThat(AttendanceCalculator.isBelowRequired(new BigDecimal("0.00"), REQUIRED)).isTrue();
  }

  @Test
  void aSubjectWithNoClassesIsNotFlagged() {
    assertThat(AttendanceCalculator.isBelowRequired(null, REQUIRED)).isFalse();
  }
}
