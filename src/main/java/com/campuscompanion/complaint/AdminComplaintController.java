package com.campuscompanion.complaint;

import com.campuscompanion.common.web.PageResponse;
import com.campuscompanion.complaint.dto.AdminComplaintResponse;
import com.campuscompanion.complaint.dto.ComplaintHandleRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin: every complaint, and handling them. Contract: docs/09-api-contract.md section 8.20. */
@RestController
@Validated
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin/complaints")
public class AdminComplaintController {

  private final AdminComplaintService complaints;

  public AdminComplaintController(AdminComplaintService complaints) {
    this.complaints = complaints;
  }

  @GetMapping
  public PageResponse<AdminComplaintResponse> list(
      @RequestParam(required = false) ComplaintType type,
      @RequestParam(required = false) ComplaintStatus status,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return complaints.list(type, status, page, size);
  }

  @GetMapping("/{id}")
  public AdminComplaintResponse one(@PathVariable Long id) {
    return complaints.one(id);
  }

  @PutMapping("/{id}")
  public AdminComplaintResponse handle(
      @PathVariable Long id, @Valid @RequestBody ComplaintHandleRequest request) {
    return complaints.handle(id, request);
  }
}
