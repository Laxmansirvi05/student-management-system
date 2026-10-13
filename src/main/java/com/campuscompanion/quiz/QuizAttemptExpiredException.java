package com.campuscompanion.quiz;

import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;

/**
 * The answers arrived after the time limit and the grace period. The test is closed as expired in
 * the same transaction, and that change is kept although this exception is thrown.
 */
public class QuizAttemptExpiredException extends ApiException {

  public QuizAttemptExpiredException() {
    super(
        ErrorCode.QUIZ_ATTEMPT_EXPIRED, "The time for this test ran out before it was submitted.");
  }
}
