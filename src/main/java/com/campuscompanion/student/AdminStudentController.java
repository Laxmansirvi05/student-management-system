package com.campuscompanion.student;

import com.campuscompanion.common.web.PageResponse;
import com.campuscompanion.student.dto.AdminStudentResponse;
import com.campuscompanion.student.dto.StudentCreateRequest;
import com.campuscompanion.student.dto.StudentUpdateRequest;
import com.campuscompanion.student.dto.TemporaryPasswordResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

/** Admin: student accounts. Contract: docs/09-api-contract.md sections 7, 8.12 and 8.13. */
@RestController
@Validated
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin/students")
public class AdminStudentController {

  private final AdminStudentService students;
  private final StudentProvisioningService provisioning;

  public AdminStudentController(
      AdminStudentService students, StudentProvisioningService provisioning) {
    this.students = students;
    this.provisioning = provisioning;
  }

  @GetMapping
  public PageResponse<AdminStudentResponse> search(
      @RequestParam(required = false) String branch,
      @RequestParam(required = false) @Min(1) @Max(8) Integer semester,
      @RequestParam(required = false) String section,
      @RequestParam(required = false) @Size(min = 2, max = 100) String q,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return students.search(branch, semester, section, q, page, size);
  }

  /** Creates the account and the profile. The temporary password is never in the response. */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AdminStudentResponse provision(@RequestBody StudentCreateRequest request) {
    return provisioning.provision(request);
  }

  @GetMapping("/{id}")
  public AdminStudentResponse get(@PathVariable Long id) {
    return students.get(id);
  }

  @PutMapping("/{id}")
  public AdminStudentResponse update(
      @PathVariable Long id, @Valid @RequestBody StudentUpdateRequest request) {
    return students.update(id, request);
  }

  @PostMapping("/{id}/deactivate")
  public ResponseEntity<Void> deactivate(@PathVariable Long id) {
    students.deactivate(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/activate")
  public ResponseEntity<Void> activate(@PathVariable Long id) {
    students.activate(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/reset-password")
  public TemporaryPasswordResponse resetPassword(@PathVariable Long id) {
    return students.resetPassword(id);
  }
}
