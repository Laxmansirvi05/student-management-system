package com.campuscompanion.planner;

import java.time.LocalDate;

/**
 * The planner's date rules, as plain Java so they can be tested without Spring. "Today" is always
 * the campus date (CampusTime), passed in by the caller. The number of days left is not worked out
 * here: the pages count it from the date with the same helper as the notice deadlines, so that it
 * stays right after midnight without asking the server again.
 */
public final class PlannerRules {

  /** A date further ahead than this is almost certainly a typing mistake. */
  static final int MAX_YEARS_AHEAD = 5;

  private PlannerRules() {}

  /** A task is overdue when it is still pending and its due date is before today. */
  public static boolean isOverdue(TaskStatus status, LocalDate dueDate, LocalDate today) {
    return status == TaskStatus.PENDING && dueDate != null && dueDate.isBefore(today);
  }

  /** An exam is past once its date is before today; on the day itself it is still to come. */
  public static boolean isPast(LocalDate examDate, LocalDate today) {
    return examDate.isBefore(today);
  }

  /**
   * Why a date cannot be chosen, or null when it can. A new date may not be in the past, nor more
   * than five years ahead. A date that is not being changed is always accepted, so that an overdue
   * task or a past exam can still be edited.
   */
  public static String problemWithDate(LocalDate chosen, LocalDate current, LocalDate today) {
    if (chosen == null || chosen.equals(current)) {
      return null;
    }
    if (chosen.isBefore(today)) {
      return "Choose today or a later date.";
    }
    if (chosen.isAfter(today.plusYears(MAX_YEARS_AHEAD))) {
      return "Choose a date within the next five years.";
    }
    return null;
  }
}
