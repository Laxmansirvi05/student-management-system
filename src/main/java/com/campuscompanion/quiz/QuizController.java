package com.campuscompanion.quiz;

import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.common.web.PageResponse;
import com.campuscompanion.quiz.dto.QuizAttemptStartedResponse;
import com.campuscompanion.quiz.dto.QuizAttemptSummaryResponse;
import com.campuscompanion.quiz.dto.QuizResultResponse;
import com.campuscompanion.quiz.dto.StartQuizRequest;
import com.campuscompanion.quiz.dto.SubmitQuizRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Quick Exam for students. Contract: docs/09-api-contract.md sections 8.7 and 8.8. */
@RestController
@Validated
@RequestMapping("/api/v1/quiz-attempts")
public class QuizController {

  private final QuizService quiz;

  public QuizController(QuizService quiz) {
    this.quiz = quiz;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public QuizAttemptStartedResponse start(
      @AuthenticationPrincipal CurrentUser currentUser,
      @Valid @RequestBody StartQuizRequest request) {
    return quiz.start(currentUser, request);
  }

  @GetMapping("/me")
  public PageResponse<QuizAttemptSummaryResponse> history(
      @AuthenticationPrincipal CurrentUser currentUser,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return quiz.history(currentUser, page, size);
  }

  @GetMapping("/{id}")
  public QuizResultResponse result(
      @AuthenticationPrincipal CurrentUser currentUser, @PathVariable Long id) {
    return quiz.result(currentUser, id);
  }

  @PostMapping("/{id}/submit")
  public QuizResultResponse submit(
      @AuthenticationPrincipal CurrentUser currentUser,
      @PathVariable Long id,
      @Valid @RequestBody SubmitQuizRequest request) {
    return quiz.submit(currentUser, id, request);
  }
}
