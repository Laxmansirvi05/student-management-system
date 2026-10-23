package com.campuscompanion.planner.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A study task. {@code overdue} is worked out on the server from the campus date: pending, with a
 * due date before today.
 */
public record StudyTaskResponse(
    Long id,
    String title,
    String notes,
    PlannerSubject subject,
    LocalDate dueDate,
    String status,
    boolean overdue,
    Instant completedAt,
    Instant createdAt) {}
