package com.campuscompanion.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SummarizeRequest(
    @NotBlank(message = "Paste the text to summarize.")
        @Size(min = 200, max = 12000, message = "Use 200 to 12,000 characters.")
        String text,
    @NotNull(message = "Choose a style.") SummaryStyle style,
    Long subjectId) {}
