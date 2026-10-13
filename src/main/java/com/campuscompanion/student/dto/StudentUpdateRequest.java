package com.campuscompanion.student.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The profile fields an admin may edit. The roll number and the login email are not here: they
 * cannot be changed once the account exists.
 */
public record StudentUpdateRequest(
    @NotBlank(message = "Full name is required.")
        @Size(max = 120, message = "Full name can have at most 120 characters.")
        String fullName,
    @NotBlank(message = "Branch is required.")
        @Size(max = 20, message = "Branch can have at most 20 characters.")
        String branch,
    @NotNull(message = "Semester is required.")
        @Min(value = 1, message = "Semester must be between 1 and 8.")
        @Max(value = 8, message = "Semester must be between 1 and 8.")
        Integer currentSemester,
    @NotBlank(message = "Section is required.")
        @Size(max = 5, message = "Section can have at most 5 characters.")
        String section,
    @NotNull(message = "Admission year is required.")
        @Min(value = 2000, message = "Admission year must be between 2000 and 2100.")
        @Max(value = 2100, message = "Admission year must be between 2000 and 2100.")
        Integer admissionYear,
    @Size(max = 20, message = "Phone can have at most 20 characters.") String phone) {}
