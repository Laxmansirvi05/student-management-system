package com.campuscompanion.assessment;

import com.campuscompanion.assessment.dto.ResultsResponse;
import com.campuscompanion.assessment.dto.SubjectMarksResponse;
import com.campuscompanion.common.security.CurrentUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A student's own marks and semester results. The paths end in "/me" and take no student
 * identifier. Contract: docs/09-api-contract.md section 8.4.
 */
@RestController
@Validated
@RequestMapping("/api/v1")
public class MarksController {

  private final MarksService marks;
  private final ResultService results;

  public MarksController(MarksService marks, ResultService results) {
    this.marks = marks;
    this.results = results;
  }

  @GetMapping("/marks/me")
  public List<SubjectMarksResponse> marks(
      @AuthenticationPrincipal CurrentUser currentUser,
      @RequestParam(required = false) Long subjectId,
      @RequestParam(required = false) @Min(1) @Max(8) Integer semester) {
    return marks.marksFor(currentUser, subjectId, semester);
  }

  @GetMapping("/results/me")
  public ResultsResponse results(@AuthenticationPrincipal CurrentUser currentUser) {
    return results.resultsFor(currentUser);
  }
}
