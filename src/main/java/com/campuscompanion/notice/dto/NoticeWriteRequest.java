package com.campuscompanion.notice.dto;

import com.campuscompanion.notice.NoticeCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

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
    LocalDate dueDate) {}
