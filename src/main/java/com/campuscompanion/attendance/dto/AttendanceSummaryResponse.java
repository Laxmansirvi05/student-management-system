package com.campuscompanion.attendance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A student's attendance this semester: one row per subject, the college's latest report, and the
 * overall figure. A percentage is null when no class has been held yet. When no class has been
 * recorded subject by subject, the overall figure is the report's.
 */
public record AttendanceSummaryResponse(
    BigDecimal requiredPercent,
    BigDecimal overallPercent,
    List<SubjectAttendance> subjects,
    Report report) {

  /** The college's total for a period, or null when there is none. */
  public record Report(
      LocalDate fromDate,
      LocalDate toDate,
      long classesHeld,
      long classesAttended,
      BigDecimal percent,
      boolean belowRequired) {}

  public record SubjectAttendance(
      Long subjectId,
      String subjectCode,
      String subjectName,
      long classesHeld,
      long classesAttended,
      BigDecimal percent,
      boolean belowRequired) {}
}
