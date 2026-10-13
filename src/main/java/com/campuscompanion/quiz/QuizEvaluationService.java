package com.campuscompanion.quiz;

import com.campuscompanion.common.error.ValidationException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Marks a test (FR-QZ-003). This is plain Java: it needs no database and no Spring, and no AI is
 * involved. It is given the questions that were served with their correct options, and the answers
 * that were sent, and it returns the mark for every question and the score.
 *
 * <p>The rules: a question that was not answered is wrong; an answer for a question that is not
 * part of the test is refused; so is a second answer for the same question.
 */
@Component
public class QuizEvaluationService {

  /** A question of the test, with the option that is right. */
  public record ServedQuestion(Long questionId, OptionKey correctOption) {}

  /** What the student sent for one question. The option is null when nothing was chosen. */
  public record SubmittedAnswer(Long questionId, OptionKey selectedOption) {}

  public record MarkedAnswer(Long questionId, OptionKey selectedOption, boolean correct) {}

  public record Evaluation(int score, List<MarkedAnswer> answers) {}

  public Evaluation evaluate(List<ServedQuestion> served, List<SubmittedAnswer> submitted) {
    Map<Long, OptionKey> correctOptions = new HashMap<>();
    for (ServedQuestion question : served) {
      correctOptions.put(question.questionId(), question.correctOption());
    }

    Map<Long, OptionKey> chosen = new HashMap<>();
    for (int i = 0; i < submitted.size(); i++) {
      SubmittedAnswer answer = submitted.get(i);
      String field = "answers[" + i + "].questionId";
      if (!correctOptions.containsKey(answer.questionId())) {
        throw new ValidationException(field, "This question is not part of the test.");
      }
      if (chosen.containsKey(answer.questionId())) {
        throw new ValidationException(field, "This question was answered more than once.");
      }
      chosen.put(answer.questionId(), answer.selectedOption());
    }

    int score = 0;
    List<MarkedAnswer> marked = new ArrayList<>();
    for (ServedQuestion question : served) {
      OptionKey selected = chosen.get(question.questionId());
      boolean correct = selected != null && selected == question.correctOption();
      if (correct) {
        score++;
      }
      marked.add(new MarkedAnswer(question.questionId(), selected, correct));
    }
    return new Evaluation(score, marked);
  }
}
