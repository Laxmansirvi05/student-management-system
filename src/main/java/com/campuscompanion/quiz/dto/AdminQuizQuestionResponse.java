package com.campuscompanion.quiz.dto;

import com.campuscompanion.quiz.Difficulty;
import com.campuscompanion.quiz.OptionKey;
import com.campuscompanion.quiz.QuestionSource;
import com.campuscompanion.quiz.QuestionStatus;
import java.time.Instant;

/**
 * A question as an admin sees it, with its correct option. "used" is true once the question has
 * been served in a test; from then on what it asks and how it is marked can no longer change.
 */
public record AdminQuizQuestionResponse(
    Long id,
    Long subjectId,
    String subjectCode,
    Long unitId,
    Integer unitNo,
    String unitTitle,
    String questionText,
    String optionA,
    String optionB,
    String optionC,
    String optionD,
    OptionKey correctOption,
    String explanation,
    Difficulty difficulty,
    QuestionStatus status,
    QuestionSource source,
    boolean used,
    Instant createdAt) {}
