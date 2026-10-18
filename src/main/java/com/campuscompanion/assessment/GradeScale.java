package com.campuscompanion.assessment;

import java.util.Map;

/**
 * The college's B.Tech grading table (AR25): letter grade to grade point. C is the lowest pass. F
 * and Ab (absent) are fails with 0 points. S and U mark a course without credits (satisfactory or
 * not) and have no grade point.
 */
public final class GradeScale {

  private static final Map<String, Integer> POINTS =
      Map.of("O", 10, "A+", 9, "A", 8, "B+", 7, "B", 6, "C", 5, "F", 0, "Ab", 0);

  private GradeScale() {}

  /** The grade point, or null for S, U or an unknown grade. */
  public static Integer points(String grade) {
    return POINTS.get(grade);
  }

  /** P or F, as on the memorandum of grades. */
  public static boolean passed(String grade) {
    return !"F".equals(grade) && !"Ab".equals(grade) && !"U".equals(grade);
  }
}
