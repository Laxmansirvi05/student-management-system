package com.campuscompanion.subject.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubjectWriteRequest(
    @NotBlank(message = "Enter the subject code.")
        @Size(max = 20, message = "Use at most 20 characters.")
        String code,
    @NotBlank(message = "Enter the subject name.")
        @Size(max = 150, message = "Use at most 150 characters.")
        String name,
    @NotBlank(message = "Enter the branch.") @Size(max = 20, message = "Use at most 20 characters.")
        String branch,
    @NotNull(message = "Choose a semester.")
        @Min(value = 1, message = "Semester must be 1 to 8.")
        @Max(value = 8, message = "Semester must be 1 to 8.")
        Integer semester,
    @NotNull(message = "Enter the credits.")
        @Min(value = 0, message = "Credits must be 0 to 10.")
        @Max(value = 10, message = "Credits must be 0 to 10.")
        Integer credits,
    @NotNull(message = "Say whether the subject is active.") Boolean active) {}
