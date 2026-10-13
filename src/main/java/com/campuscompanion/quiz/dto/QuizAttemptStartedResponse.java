package com.campuscompanion.quiz.dto;

import java.time.Instant;
import java.util.List;

/**
 * A test that has just started. It holds the questions and their options in the order to show them.
 * It has no field for a correct option or an explanation, so neither can be sent by mistake.
 */
public record QuizAttemptStartedResponse(
    Long attemptId,
    Instant startedAt,
    Instant expiresAt,
    long durationSeconds,
    List<Question> questions) {

  public record Question(Long questionId, int position, String text, List<QuizOption> options) {}
}
