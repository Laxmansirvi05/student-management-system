package com.campuscompanion.resource.dto;

import com.campuscompanion.resource.ExamType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record QuestionPaperDetails(
    @NotNull(message = "Enter the exam year.")
        @Min(value = 2000, message = "Exam year must be 2000 to 2100.")
        @Max(value = 2100, message = "Exam year must be 2000 to 2100.")
        Integer examYear,
    @NotNull(message = "Choose the exam type.") ExamType examType,
    @Min(value = 1, message = "Maximum marks must be at least 1.")
        @Max(value = 30000, message = "Maximum marks is too large.")
        Integer maxMarks) {}
