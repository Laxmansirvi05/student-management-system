package com.campuscompanion.quiz.dto;

import jakarta.validation.constraints.NotNull;

/**
 * What a student chooses before a test: the subject and the number of questions (5, 10 or 20). The
 * unit is optional: without it, the questions come from the whole subject.
 */
public record StartQuizRequest(
    @NotNull(message = "Choose a subject.") Long subjectId,
    Long unitId,
    @NotNull(message = "Choose 5, 10 or 20 questions.") Integer questionCount) {}
