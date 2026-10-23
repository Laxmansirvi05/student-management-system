package com.campuscompanion.planner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** A new or changed study task. The student is never in the request: the server knows. */
public record StudyTaskRequest(
    @NotBlank(message = "Give the task a title.")
        @Size(max = 200, message = "Use at most 200 characters.")
        String title,
    @Size(max = 1000, message = "Use at most 1,000 characters.") String notes,
    /** Optional: one of the student's subjects. */
    Long subjectId,
    /** Optional. */
    LocalDate dueDate) {}
