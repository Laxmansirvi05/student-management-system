package com.campuscompanion.resource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** What is wrong with a resource, in the student's words. Plain text. */
public record ResourceReportRequest(
    @NotBlank(message = "Say what is wrong with this resource.")
        @Size(min = 5, max = 500, message = "Use 5 to 500 characters.")
        String reason) {}
