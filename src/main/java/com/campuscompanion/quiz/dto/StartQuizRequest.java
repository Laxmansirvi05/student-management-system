package com.campuscompanion.quiz.dto;

import jakarta.validation.constraints.NotNull;

/** What a student chooses before a test: the unit and the number of questions (5, 10 or 20). */
public record StartQuizRequest(
    @NotNull(message = "Choose a subject.") Long subjectId,
    @NotNull(message = "Choose a unit.") Long unitId,
    @NotNull(message = "Choose 5, 10 or 20 questions.") Integer questionCount) {}
