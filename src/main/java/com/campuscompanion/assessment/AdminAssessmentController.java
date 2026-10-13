package com.campuscompanion.assessment;

import com.campuscompanion.assessment.dto.AdminSemesterResultResponse;
import com.campuscompanion.assessment.dto.AssessmentResponse;
import com.campuscompanion.assessment.dto.AssessmentWriteRequest;
import com.campuscompanion.assessment.dto.MarksSheet;
import com.campuscompanion.assessment.dto.SemesterResultWriteRequest;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.common.web.SheetSaved;
import jakarta.validation.Valid;
import java.util.List;
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

/**
 * Admin: assessments, marks sheets and semester results. Contract: docs/09-api-contract.md sections
 * 7 and 8.10.
 */
@RestController
@Validated
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin")
public class AdminAssessmentController {

  private final AdminAssessmentService assessments;
  private final AdminResultService results;

  public AdminAssessmentController(AdminAssessmentService assessments, AdminResultService results) {
    this.assessments = assessments;
    this.results = results;
  }

  @GetMapping("/assessments")
  public List<AssessmentResponse> list(@RequestParam(required = false) Long subjectId) {
    return assessments.list(subjectId);
  }

  @PostMapping("/assessments")
  @ResponseStatus(HttpStatus.CREATED)
  public AssessmentResponse create(@Valid @RequestBody AssessmentWriteRequest request) {
    return assessments.create(request);
  }

  @PutMapping("/assessments/{id}")
  public AssessmentResponse update(
      @PathVariable Long id, @Valid @RequestBody AssessmentWriteRequest request) {
    return assessments.update(id, request);
  }

  @GetMapping("/assessments/{id}/marks")
  public MarksSheet marksSheet(@PathVariable Long id) {
    return assessments.sheet(id);
  }

  @PutMapping("/assessments/{id}/marks")
  public SheetSaved saveMarks(
      @PathVariable Long id,
      @Valid @RequestBody MarksSheet sheet,
      @AuthenticationPrincipal CurrentUser admin) {
    return assessments.save(id, sheet, admin);
  }

  @GetMapping("/semester-results")
  public List<AdminSemesterResultResponse> resultsOf(@RequestParam Long studentId) {
    return results.resultsOf(studentId);
  }

  @PutMapping("/semester-results")
  public AdminSemesterResultResponse saveResult(
      @Valid @RequestBody SemesterResultWriteRequest request) {
    return results.save(request);
  }
}
