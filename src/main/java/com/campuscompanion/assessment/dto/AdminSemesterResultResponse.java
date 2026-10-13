package com.campuscompanion.assessment.dto;

import com.campuscompanion.assessment.ResultStatus;
import java.math.BigDecimal;

/** A semester result as an admin sees it: the official values, and whether it is published. */
public record AdminSemesterResultResponse(
    Long studentId,
    int semester,
    BigDecimal sgpa,
    int creditsRegistered,
    int creditsEarned,
    ResultStatus resultStatus,
    boolean published) {}
