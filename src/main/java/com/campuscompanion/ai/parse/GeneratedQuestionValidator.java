package com.campuscompanion.ai.parse;

import com.campuscompanion.ai.dto.GenerateQuizResponse.Question;
import com.campuscompanion.ai.dto.QuestionType;
import com.campuscompanion.quiz.OptionKey;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Decides whether a question written by the AI can be shown (docs/12-AI-features-spec.md section
 * 5). It is plain Java. For a multiple-choice question it checks that there are exactly four
 * different options, that the correct option is one of A to D, and that the answer repeats the text
 * of the correct option. A question that fails any check is thrown away.
 *
 * <p>These checks are about form. They cannot tell whether the option marked correct really is.
 */
@Component
public class GeneratedQuestionValidator {

  static final int MAX_QUESTION = 1000;
  static final int MAX_OPTION = 300;
  static final int MAX_ANSWER = 4000;
  static final int MAX_EXPLANATION = 2000;

  /** A question as the AI wrote it, before any check. Any field can be null. */
  public record Draft(
      String type,
      String question,
      List<String> options,
      String correctOption,
      String answer,
      String explanation) {}

  public Optional<Question> validate(Draft draft, QuestionType wanted) {
    if (!wanted.name().equals(draft.type())
        || !fits(draft.question(), MAX_QUESTION)
        || !fits(draft.explanation(), MAX_EXPLANATION)
        || !fits(draft.answer(), MAX_ANSWER)) {
      return Optional.empty();
    }
    if (wanted != QuestionType.MCQ) {
      boolean noOptions = draft.options() == null || draft.options().isEmpty();
      return noOptions && draft.correctOption() == null
          ? Optional.of(
              new Question(
                  wanted, draft.question(), null, null, draft.answer(), draft.explanation()))
          : Optional.empty();
    }
    Optional<OptionKey> correct = optionKey(draft.correctOption());
    if (correct.isEmpty() || !fourDifferentOptions(draft.options())) {
      return Optional.empty();
    }
    String correctText = draft.options().get(correct.get().ordinal());
    if (!correctText.strip().equals(draft.answer().strip())) {
      return Optional.empty();
    }
    return Optional.of(
        new Question(
            wanted,
            draft.question(),
            List.copyOf(draft.options()),
            correct.get(),
            correctText,
            draft.explanation()));
  }

  private static boolean fits(String text, int limit) {
    return text != null && !text.isBlank() && text.length() <= limit;
  }

  private static boolean fourDifferentOptions(List<String> options) {
    if (options == null || options.size() != 4) {
      return false;
    }
    Set<String> seen = new HashSet<>();
    for (String option : options) {
      if (!fits(option, MAX_OPTION) || !seen.add(option.strip().toLowerCase(Locale.ROOT))) {
        return false;
      }
    }
    return true;
  }

  private static Optional<OptionKey> optionKey(String letter) {
    for (OptionKey key : OptionKey.values()) {
      if (key.name().equals(letter)) {
        return Optional.of(key);
      }
    }
    return Optional.empty();
  }
}
