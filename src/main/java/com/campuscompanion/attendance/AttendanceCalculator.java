package com.campuscompanion.attendance;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The attendance rules (FR-ATT-001, FR-ATT-002), in plain Java so they can be tested on their own.
 *
 * <p>Percentage = classes attended ÷ classes held × 100, rounded half up to two decimals. With no
 * classes held there is no percentage at all (null), which is different from 0%. A subject is
 * "below required" only when its percentage is lower than the required one; exactly equal is fine.
 */
public final class AttendanceCalculator {

  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  private AttendanceCalculator() {}

  /** The percentage to two decimals, or null when no class has been held. */
  public static BigDecimal percent(long classesAttended, long classesHeld) {
    if (classesHeld <= 0) {
      return null;
    }
    return BigDecimal.valueOf(classesAttended)
        .multiply(HUNDRED)
        .divide(BigDecimal.valueOf(classesHeld), 2, RoundingMode.HALF_UP);
  }

  /** True when there is a percentage and it is lower than the required one. */
  public static boolean isBelowRequired(BigDecimal percent, BigDecimal requiredPercent) {
    return percent != null && percent.compareTo(requiredPercent) < 0;
  }
}
