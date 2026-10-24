package com.campuscompanion.notice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** One paper of an exam time table, as an admin enters it. */
public record ExamPaperRequest(
    @NotNull(message = "Enter the date of each paper.") LocalDate examDate,
    @NotBlank(message = "Enter the subject of each paper.")
        @Size(max = 120, message = "Use at most 120 characters for a subject.")
        String subject) {}
