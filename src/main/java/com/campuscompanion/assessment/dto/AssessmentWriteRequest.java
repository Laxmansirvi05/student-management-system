package com.campuscompanion.assessment.dto;

import com.campuscompanion.assessment.AssessmentType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record AssessmentWriteRequest(
    @NotNull(message = "Choose a subject.") Long subjectId,
    @NotBlank(message = "Enter a name.") @Size(max = 100, message = "Use at most 100 characters.")
        String name,
    @NotNull(message = "Choose a type.") AssessmentType type,
    @NotNull(message = "Enter the maximum marks.")
        @DecimalMin(value = "0", inclusive = false, message = "Maximum marks must be more than 0.")
        @DecimalMax(value = "999.99", message = "Maximum marks can be at most 999.99.")
        @Digits(integer = 3, fraction = 2, message = "Use at most two decimals.")
        BigDecimal maxMarks,
    LocalDate heldOn,
    @NotBlank(message = "Enter the academic year.")
        @Pattern(regexp = "^\\d{4}-\\d{2}$", message = "Write the academic year like 2026-27.")
        String academicYear,
    @NotNull(message = "Say whether it is published.") Boolean published) {}
