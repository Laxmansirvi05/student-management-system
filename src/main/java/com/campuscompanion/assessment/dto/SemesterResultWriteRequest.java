package com.campuscompanion.assessment.dto;

import com.campuscompanion.assessment.ResultStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record SemesterResultWriteRequest(
    @NotNull(message = "Choose a student.") Long studentId,
    @NotNull(message = "Choose a semester.")
        @Min(value = 1, message = "Semester must be 1 to 8.")
        @Max(value = 8, message = "Semester must be 1 to 8.")
        Integer semester,
    // Empty while a subject is failed: the college publishes no SGPA then. Required for PASS.
    @DecimalMin(value = "0", message = "SGPA must be 0 to 10.")
        @DecimalMax(value = "10", message = "SGPA must be 0 to 10.")
        @Digits(integer = 2, fraction = 2, message = "Use at most two decimals.")
        BigDecimal sgpa,
    @NotNull(message = "Enter the credits registered.")
        @Min(value = 1, message = "Credits registered must be at least 1.")
        @Max(value = 100, message = "Credits registered is too large.")
        Integer creditsRegistered,
    @NotNull(message = "Enter the credits earned.")
        @Min(value = 0, message = "Credits earned cannot be negative.")
        Integer creditsEarned,
    @NotNull(message = "Choose a result status.") ResultStatus resultStatus,
    @NotNull(message = "Say whether it is published.") Boolean published) {}
