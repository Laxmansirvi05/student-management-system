package com.campuscompanion.quiz.dto;

import com.campuscompanion.quiz.Difficulty;
import com.campuscompanion.quiz.OptionKey;
import com.campuscompanion.quiz.QuestionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A question as an admin writes it. */
public record QuizQuestionWriteRequest(
    @NotNull(message = "Choose a subject.") Long subjectId,
    /** Optional: a question may belong to the whole subject. */
    Long unitId,
    @NotBlank(message = "Enter the question.")
        @Size(max = 1000, message = "Use at most 1,000 characters.")
        String questionText,
    @NotBlank(message = "Enter option A.") @Size(max = 300, message = "Use at most 300 characters.")
        String optionA,
    @NotBlank(message = "Enter option B.") @Size(max = 300, message = "Use at most 300 characters.")
        String optionB,
    @NotBlank(message = "Enter option C.") @Size(max = 300, message = "Use at most 300 characters.")
        String optionC,
    @NotBlank(message = "Enter option D.") @Size(max = 300, message = "Use at most 300 characters.")
        String optionD,
    @NotNull(message = "Choose the correct option.") OptionKey correctOption,
    @NotBlank(message = "Enter an explanation.")
        @Size(max = 2000, message = "Use at most 2,000 characters.")
        String explanation,
    @NotNull(message = "Choose a difficulty.") Difficulty difficulty,
    @NotNull(message = "Choose a status.") QuestionStatus status) {}
