package com.campuscompanion.ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Exactly one of topic and sourceText is given. The service checks that. */
public record GenerateQuizRequest(
    @Size(min = 3, max = 200, message = "Use 3 to 200 characters.") String topic,
    @Size(min = 200, max = 8000, message = "Use 200 to 8,000 characters.") String sourceText,
    Long subjectId,
    Long unitId,
    @NotNull(message = "Choose a question type.") QuestionType questionType,
    @NotNull(message = "Choose how many questions.")
        @Min(value = 1, message = "Ask for 1 to 10 questions.")
        @Max(value = 10, message = "Ask for 1 to 10 questions.")
        Integer count) {}
