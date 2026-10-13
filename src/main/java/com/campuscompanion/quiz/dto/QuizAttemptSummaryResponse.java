package com.campuscompanion.quiz.dto;

import com.campuscompanion.quiz.AttemptStatus;
import java.time.Instant;

/** One line of a student's test history. */
public record QuizAttemptSummaryResponse(
    Long attemptId,
    Long subjectId,
    String subjectCode,
    String subjectName,
    Long unitId,
    Integer unitNo,
    String unitTitle,
    AttemptStatus status,
    Integer score,
    int questionCount,
    Instant startedAt) {}
