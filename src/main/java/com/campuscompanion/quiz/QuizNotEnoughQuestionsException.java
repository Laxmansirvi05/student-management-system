package com.campuscompanion.quiz;

import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import java.util.Map;

/** The unit has fewer published questions than the test asked for. Says how many there are. */
public class QuizNotEnoughQuestionsException extends ApiException {

  private final int available;

  public QuizNotEnoughQuestionsException(int available) {
    super(
        ErrorCode.QUIZ_NOT_ENOUGH_QUESTIONS,
        available == 1
            ? "This unit has only 1 question so far."
            : "This unit has only " + available + " questions so far.");
    this.available = available;
  }

  @Override
  public Map<String, Object> getProperties() {
    return Map.of("available", available);
  }
}
