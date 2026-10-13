package com.campuscompanion.subject.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UnitWriteRequest(
    @NotNull(message = "Enter the unit number.")
        @Min(value = 1, message = "Unit number must be 1 to 20.")
        @Max(value = 20, message = "Unit number must be 1 to 20.")
        Integer unitNo,
    @NotBlank(message = "Enter the unit title.")
        @Size(max = 200, message = "Use at most 200 characters.")
        String title) {}
