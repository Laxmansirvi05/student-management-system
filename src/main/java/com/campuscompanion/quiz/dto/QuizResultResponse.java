package com.campuscompanion.quiz.dto;

import com.campuscompanion.quiz.AttemptStatus;
import com.campuscompanion.quiz.OptionKey;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** The result of a closed test: the score, and every question with its correct answer. */
public record QuizResultResponse(
    Long attemptId,
    AttemptStatus status,
    int score,
    int questionCount,
    BigDecimal percent,
    Instant startedAt,
    Instant submittedAt,
    Long subjectId,
    String subjectCode,
    String subjectName,
    Long unitId,
    Integer unitNo,
    String unitTitle,
    List<Item> items) {

  public record Item(
      Long questionId,
      int position,
      String text,
      List<QuizOption> options,
      OptionKey selectedOption,
      OptionKey correctOption,
      boolean correct,
      String explanation) {}
}
