package com.campuscompanion.planner.dto;

import java.util.List;

/** Exams to come, soonest first, and the student's own past exams, latest first. */
public record ExamListResponse(List<ExamResponse> upcoming, List<ExamResponse> past) {}
