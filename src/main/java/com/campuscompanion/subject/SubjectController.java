package com.campuscompanion.subject;

import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.subject.dto.SubjectResponse;
import com.campuscompanion.subject.dto.UnitResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Subjects and units for any logged-in user. Contract: docs/09-api-contract.md. */
@RestController
@Validated
@RequestMapping("/api/v1/subjects")
public class SubjectController {

  private final SubjectService subjects;

  public SubjectController(SubjectService subjects) {
    this.subjects = subjects;
  }

  @GetMapping
  public List<SubjectResponse> list(
      @AuthenticationPrincipal CurrentUser currentUser,
      @RequestParam(required = false) @Min(1) @Max(8) Integer semester) {
    return subjects.visibleTo(currentUser, semester);
  }

  @GetMapping("/{id}/units")
  public List<UnitResponse> units(@PathVariable Long id) {
    return subjects.unitsOf(id);
  }
}
