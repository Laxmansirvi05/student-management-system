package com.campuscompanion.progress;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * The progress rule (FR-PRG-001), as plain Java with no database and no AI:
 *
 * <ul>
 *   <li>accuracy in a unit is worked out over the student's 20 most recent answers in that unit;
 *   <li>with fewer than 5 answers there is not enough data to judge;
 *   <li>otherwise under 60% needs revision, 80% and over is strong, and anything between is
 *       improving.
 * </ul>
 */
public final class ProgressCalculator {

  public static final int WINDOW = 20;
  public static final int MIN_ANSWERS = 5;
  private static final BigDecimal NEEDS_REVISION_BELOW = new BigDecimal("60");
  private static final BigDecimal STRONG_FROM = new BigDecimal("80");

  private ProgressCalculator() {}

  /** accuracyPercent is null when there are no answers at all. */
  public record UnitPerformance(
      int answered, int correct, BigDecimal accuracyPercent, PerformanceLevel level) {}

  /**
   * @param newestFirst for each answer in the unit, whether it was correct, newest first
   */
  public static UnitPerformance of(List<Boolean> newestFirst) {
    List<Boolean> recent = newestFirst.subList(0, Math.min(WINDOW, newestFirst.size()));
    int answered = recent.size();
    int correct = (int) recent.stream().filter(Boolean::booleanValue).count();
    if (answered == 0) {
      return new UnitPerformance(0, 0, null, PerformanceLevel.NOT_ENOUGH_DATA);
    }
    BigDecimal accuracy =
        BigDecimal.valueOf(correct)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(answered), 2, RoundingMode.HALF_UP);
    return new UnitPerformance(answered, correct, accuracy, levelFor(answered, accuracy));
  }

  static PerformanceLevel levelFor(int answered, BigDecimal accuracyPercent) {
    if (answered < MIN_ANSWERS) {
      return PerformanceLevel.NOT_ENOUGH_DATA;
    }
    if (accuracyPercent.compareTo(NEEDS_REVISION_BELOW) < 0) {
      return PerformanceLevel.NEEDS_REVISION;
    }
    return accuracyPercent.compareTo(STRONG_FROM) >= 0
        ? PerformanceLevel.STRONG
        : PerformanceLevel.IMPROVING;
  }
}
