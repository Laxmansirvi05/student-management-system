package com.campuscompanion.quiz;

import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.common.web.PageResponse;
import com.campuscompanion.quiz.dto.AdminQuizQuestionResponse;
import com.campuscompanion.quiz.dto.QuizQuestionWriteRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Admin: the question bank. Contract: docs/openapi.yaml, /admin/quiz-questions. */
@RestController
@Validated
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin/quiz-questions")
public class AdminQuizQuestionController {

  private final AdminQuizQuestionService questions;

  public AdminQuizQuestionController(AdminQuizQuestionService questions) {
    this.questions = questions;
  }

  @GetMapping
  public PageResponse<AdminQuizQuestionResponse> list(
      @RequestParam(required = false) Long subjectId,
      @RequestParam(required = false) Long unitId,
      @RequestParam(required = false) QuestionStatus status,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return questions.list(subjectId, unitId, status, page, size);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AdminQuizQuestionResponse create(
      @Valid @RequestBody QuizQuestionWriteRequest request,
      @AuthenticationPrincipal CurrentUser admin) {
    return questions.create(request, admin);
  }

  @PutMapping("/{id}")
  public AdminQuizQuestionResponse update(
      @PathVariable Long id, @Valid @RequestBody QuizQuestionWriteRequest request) {
    return questions.update(id, request);
  }
}
