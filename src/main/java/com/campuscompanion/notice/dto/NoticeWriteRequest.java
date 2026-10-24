package com.campuscompanion.notice.dto;

import com.campuscompanion.notice.NoticeCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record NoticeWriteRequest(
    @NotBlank(message = "Enter a title.") @Size(max = 200, message = "Use at most 200 characters.")
        String title,
    @NotBlank(message = "Enter the text of the notice.")
        @Size(max = 5000, message = "Use at most 5,000 characters.")
        String body,
    @NotNull(message = "Choose a category.") NoticeCategory category,
    @Size(max = 20, message = "Use at most 20 characters.") String targetBranch,
    @Min(value = 1, message = "Semester must be 1 to 8.")
        @Max(value = 8, message = "Semester must be 1 to 8.")
        Integer targetSemester,
    LocalDate dueDate,
    /**
     * The papers of an exam time table (category EXAM only). Null leaves the saved papers as they
     * are; an empty list removes them.
     */
    @Size(max = 30, message = "Enter at most 30 papers.")
        List<@Valid ExamPaperRequest> examPapers) {}
