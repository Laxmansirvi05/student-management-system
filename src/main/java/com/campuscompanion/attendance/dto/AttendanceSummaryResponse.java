package com.campuscompanion.attendance.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * A student's attendance this semester: one row per subject and the overall figure. A percentage is
 * null when no class has been held yet.
 */
public record AttendanceSummaryResponse(
    BigDecimal requiredPercent, BigDecimal overallPercent, List<SubjectAttendance> subjects) {

  public record SubjectAttendance(
      Long subjectId,
      String subjectCode,
      String subjectName,
      long classesHeld,
      long classesAttended,
      BigDecimal percent,
      boolean belowRequired) {}
}
