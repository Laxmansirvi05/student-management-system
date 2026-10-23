package com.campuscompanion.planner.dto;

import java.util.List;

/** The tasks of one view, and how many there are in each view. */
public record StudyTaskListResponse(List<StudyTaskResponse> tasks, Counts counts) {

  public record Counts(int pending, int overdue, int done) {}
}
