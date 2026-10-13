package com.campuscompanion.quiz;

/** One marked answer of a student, with the unit it belongs to. Used to work out progress. */
public record AnsweredQuestion(Long subjectId, Long unitId, Boolean correct) {

  public boolean isCorrect() {
    return Boolean.TRUE.equals(correct);
  }
}
