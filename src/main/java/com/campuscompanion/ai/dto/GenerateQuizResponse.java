package com.campuscompanion.ai.dto;

import com.campuscompanion.quiz.OptionKey;
import java.util.List;

/**
 * Practice questions for the student's own use. They are shown and then forgotten: nothing here is
 * saved to the question bank, and none of it is used in a Quick Exam.
 */
public record GenerateQuizResponse(
    List<Question> questions,
    int requested,
    int returned,
    AiBasis basis,
    String notice,
    String promptVersion) {

  /** For SHORT and LONG questions, options and correctOption are null. */
  public record Question(
      QuestionType type,
      String question,
      List<String> options,
      OptionKey correctOption,
      String answer,
      String explanation) {}
}
