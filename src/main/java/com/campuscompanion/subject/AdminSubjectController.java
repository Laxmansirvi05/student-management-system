package com.campuscompanion.subject;

import com.campuscompanion.subject.dto.AdminSubjectResponse;
import com.campuscompanion.subject.dto.SubjectWriteRequest;
import com.campuscompanion.subject.dto.UnitResponse;
import com.campuscompanion.subject.dto.UnitWriteRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

/** Admin: subjects and units. Contract: docs/09-api-contract.md section 7. */
@RestController
@Validated
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin")
public class AdminSubjectController {

  private final AdminSubjectService subjects;

  public AdminSubjectController(AdminSubjectService subjects) {
    this.subjects = subjects;
  }

  @GetMapping("/subjects")
  public List<AdminSubjectResponse> list(
      @RequestParam(required = false) String branch,
      @RequestParam(required = false) @Min(1) @Max(8) Integer semester) {
    return subjects.list(branch, semester);
  }

  @PostMapping("/subjects")
  @ResponseStatus(HttpStatus.CREATED)
  public AdminSubjectResponse create(@Valid @RequestBody SubjectWriteRequest request) {
    return subjects.create(request);
  }

  @PutMapping("/subjects/{id}")
  public AdminSubjectResponse update(
      @PathVariable Long id, @Valid @RequestBody SubjectWriteRequest request) {
    return subjects.update(id, request);
  }

  @PostMapping("/subjects/{id}/units")
  @ResponseStatus(HttpStatus.CREATED)
  public UnitResponse addUnit(@PathVariable Long id, @Valid @RequestBody UnitWriteRequest request) {
    return subjects.addUnit(id, request);
  }

  @PutMapping("/units/{id}")
  public UnitResponse updateUnit(
      @PathVariable Long id, @Valid @RequestBody UnitWriteRequest request) {
    return subjects.updateUnit(id, request);
  }

  @DeleteMapping("/units/{id}")
  public ResponseEntity<Void> deleteUnit(@PathVariable Long id) {
    subjects.deleteUnit(id);
    return ResponseEntity.noContent().build();
  }
}
