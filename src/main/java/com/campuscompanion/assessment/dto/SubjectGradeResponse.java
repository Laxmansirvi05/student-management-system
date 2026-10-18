package com.campuscompanion.assessment.dto;

import java.math.BigDecimal;

/**
 * A subject of a published semester: its current grade and the examination that gave it. When an
 * earlier attempt was failed, firstGrade is that first grade; otherwise it is null.
 */
public record SubjectGradeResponse(
    String subjectCode,
    String subjectName,
    BigDecimal credits,
    String grade,
    String exam,
    int attempts,
    String firstGrade,
    boolean passed) {}
