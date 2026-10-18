package com.campuscompanion.assessment.dto;

import com.campuscompanion.assessment.ResultStatus;
import java.math.BigDecimal;
import java.util.List;

public record SemesterResultResponse(
    int semester,
    BigDecimal sgpa,
    int creditsRegistered,
    int creditsEarned,
    ResultStatus resultStatus,
    List<SubjectGradeResponse> subjects) {}
