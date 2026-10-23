package com.campuscompanion.planner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** A new or changed exam date of the student's own. */
public record ExamRequest(
    @NotBlank(message = "Name the exam.") @Size(max = 120, message = "Use at most 120 characters.")
        String name,
    /** Optional: one of the student's subjects. */
    Long subjectId,
    @NotNull(message = "Choose the date of the exam.") LocalDate examDate) {}
