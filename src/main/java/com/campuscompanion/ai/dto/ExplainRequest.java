package com.campuscompanion.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ExplainRequest(
    @NotBlank(message = "Enter a topic.")
        @Size(min = 3, max = 200, message = "Use 3 to 200 characters.")
        String topic,
    Long subjectId,
    Long unitId,
    @NotNull(message = "Choose an answer format.") AnswerFormat answerFormat,
    @Size(max = 2000, message = "Use at most 2,000 characters.") String context) {}
