package com.campuscompanion.pyq.dto;

import com.campuscompanion.pyq.PyqExamKind;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** What an admin says about a paper when adding or editing it. */
public record PyqMetadata(
    @NotBlank(message = "Enter the branch.")
        @Pattern(regexp = "^[A-Za-z]{2,10}$", message = "Use 2 to 10 letters, such as CSE or FE.")
        String branch,
    @NotBlank(message = "Enter the subject.")
        @Size(min = 3, max = 120, message = "Use 3 to 120 characters.")
        String subject,
    @NotNull(message = "Enter the exam year.")
        @Min(value = 2000, message = "Enter a year from 2000.")
        @Max(value = 2100, message = "Enter a year up to 2100.")
        Integer year,
    @NotNull(message = "Choose the month.")
        @Min(value = 1, message = "Month is 1 to 12.")
        @Max(value = 12, message = "Month is 1 to 12.")
        Integer month,
    @NotNull(message = "Choose the kind of exam.") PyqExamKind kind,
    @Min(value = 1, message = "A mid paper is number 1 to 4.")
        @Max(value = 4, message = "A mid paper is number 1 to 4.")
        Integer midNumber,
    @Pattern(regexp = "^$|^AR[0-9]{2}$", message = "Use a regulation such as AR20.")
        String regulation,
    @Size(max = 40, message = "Use at most 40 characters.") String courseCode,
    @Min(value = 1, message = "Question set is 1 to 20.")
        @Max(value = 20, message = "Question set is 1 to 20.")
        Integer variant) {}
