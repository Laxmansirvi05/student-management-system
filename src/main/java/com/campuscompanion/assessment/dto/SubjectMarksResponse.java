package com.campuscompanion.assessment.dto;

import com.campuscompanion.assessment.AssessmentType;
import java.math.BigDecimal;
import java.util.List;

/** A student's published marks in one subject. */
public record SubjectMarksResponse(
    Long subjectId, String subjectCode, String subjectName, List<AssessmentMark> assessments) {

  /**
   * One published assessment. When the student was absent, {@code absent} is true and there are no
   * marks and no percentage; "absent" is never shown as zero.
   */
  public record AssessmentMark(
      Long assessmentId,
      String name,
      AssessmentType type,
      BigDecimal maxMarks,
      BigDecimal marksObtained,
      boolean absent,
      BigDecimal percent) {}
}
