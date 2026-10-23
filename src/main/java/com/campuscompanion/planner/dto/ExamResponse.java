package com.campuscompanion.planner.dto;

import java.time.LocalDate;

/**
 * One exam in the countdown: either the student's own ({@code source} "MINE", with an {@code id}
 * and editable) or an exam date the college published as a notice ({@code source} "COLLEGE", with
 * its {@code noticeId}, read-only).
 */
public record ExamResponse(
    Long id,
    String source,
    Long noticeId,
    String name,
    PlannerSubject subject,
    LocalDate examDate,
    boolean past) {}
