package com.campuscompanion.quiz.dto;

import com.campuscompanion.quiz.OptionKey;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The answers of a test. A question that is left out, or sent with no option, counts as wrong.
 * There is no field for a score: the server works the score out itself.
 */
public record SubmitQuizRequest(
    @NotNull(message = "Send the answers.")
        @Size(max = 20, message = "A test has at most 20 questions.")
        List<@Valid @NotNull Answer> answers) {

  public record Answer(
      @NotNull(message = "Say which question this answers.") Long questionId,
      OptionKey selectedOption) {}
}
