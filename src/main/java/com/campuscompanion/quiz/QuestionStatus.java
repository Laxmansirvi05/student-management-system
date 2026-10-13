package com.campuscompanion.quiz;

/**
 * Only published questions are used in tests. A question that was used is retired when it should no
 * longer appear; it is never deleted, so past results stay complete.
 */
public enum QuestionStatus {
  DRAFT,
  PUBLISHED,
  RETIRED
}
