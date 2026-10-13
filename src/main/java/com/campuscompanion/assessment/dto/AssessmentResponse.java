package com.campuscompanion.assessment.dto;

import com.campuscompanion.assessment.AssessmentType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record AssessmentResponse(
    Long id,
    Long subjectId,
    String name,
    AssessmentType type,
    BigDecimal maxMarks,
    LocalDate heldOn,
    String academicYear,
    boolean published) {}
