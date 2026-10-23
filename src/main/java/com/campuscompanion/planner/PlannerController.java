package com.campuscompanion.planner;

import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.planner.dto.ExamListResponse;
import com.campuscompanion.planner.dto.ExamRequest;
import com.campuscompanion.planner.dto.ExamResponse;
import com.campuscompanion.planner.dto.StudyTaskListResponse;
import com.campuscompanion.planner.dto.StudyTaskRequest;
import com.campuscompanion.planner.dto.StudyTaskResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Students: study tasks and the exam countdown (FR-PLN-001, FR-PLN-002). Contract:
 * docs/09-api-contract.md section 8.21.
 */
@RestController
@Validated
@RequestMapping("/api/v1/planner")
public class PlannerController {

  private final StudyTaskService tasks;
  private final ExamCountdownService exams;

  public PlannerController(StudyTaskService tasks, ExamCountdownService exams) {
    this.tasks = tasks;
    this.exams = exams;
  }

  @GetMapping("/tasks")
  public StudyTaskListResponse tasks(
      @AuthenticationPrincipal CurrentUser user,
      @RequestParam(defaultValue = "pending")
          @Pattern(
              regexp = "pending|overdue|done|all",
              message = "Choose pending, overdue, done or all.")
          String view) {
    return tasks.list(user, view);
  }

  @PostMapping("/tasks")
  @ResponseStatus(HttpStatus.CREATED)
  public StudyTaskResponse createTask(
      @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody StudyTaskRequest request) {
    return tasks.create(user, request);
  }

  @PutMapping("/tasks/{id}")
  public StudyTaskResponse updateTask(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable Long id,
      @Valid @RequestBody StudyTaskRequest request) {
    return tasks.update(user, id, request);
  }

  @PostMapping("/tasks/{id}/complete")
  public StudyTaskResponse complete(
      @AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
    return tasks.complete(user, id);
  }

  @PostMapping("/tasks/{id}/reopen")
  public StudyTaskResponse reopen(
      @AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
    return tasks.reopen(user, id);
  }

  @DeleteMapping("/tasks/{id}")
  public ResponseEntity<Void> deleteTask(
      @AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
    tasks.delete(user, id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/exams")
  public ExamListResponse exams(@AuthenticationPrincipal CurrentUser user) {
    return exams.list(user);
  }

  @PostMapping("/exams")
  @ResponseStatus(HttpStatus.CREATED)
  public ExamResponse createExam(
      @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody ExamRequest request) {
    return exams.create(user, request);
  }

  @PutMapping("/exams/{id}")
  public ExamResponse updateExam(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable Long id,
      @Valid @RequestBody ExamRequest request) {
    return exams.update(user, id, request);
  }

  @DeleteMapping("/exams/{id}")
  public ResponseEntity<Void> deleteExam(
      @AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
    exams.delete(user, id);
    return ResponseEntity.noContent().build();
  }
}
