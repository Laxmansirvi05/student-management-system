package com.campuscompanion.resource;

import com.campuscompanion.common.web.PageResponse;
import com.campuscompanion.resource.dto.ResourceReportResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admins: reports students made on resources (FR-LIB-005). Contract: docs/09 section 8.23. */
@RestController
@Validated
@RequestMapping("/api/v1/admin/resource-reports")
public class AdminResourceReportController {

  private final ResourceReportService reports;

  public AdminResourceReportController(ResourceReportService reports) {
    this.reports = reports;
  }

  @GetMapping
  public PageResponse<ResourceReportResponse> list(
      @RequestParam(defaultValue = "OPEN")
          @Pattern(regexp = "OPEN|RESOLVED|ALL", message = "Choose OPEN, RESOLVED or ALL.")
          String status,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
    return reports.list(status, page, size);
  }

  @PostMapping("/{id}/resolve")
  public ResourceReportResponse resolve(@PathVariable Long id) {
    return reports.resolve(id);
  }
}
