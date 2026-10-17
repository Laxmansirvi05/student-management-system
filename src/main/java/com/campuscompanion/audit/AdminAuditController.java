package com.campuscompanion.audit;

import com.campuscompanion.audit.dto.AuditEntryResponse;
import com.campuscompanion.common.web.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin: the audit log, read only. Contract: docs/09-api-contract.md section 8.15. */
@RestController
@Validated
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin/audit-logs")
public class AdminAuditController {

  private final AdminAuditService audit;

  public AdminAuditController(AdminAuditService audit) {
    this.audit = audit;
  }

  @GetMapping
  public PageResponse<AuditEntryResponse> search(
      @RequestParam(required = false) String action,
      @RequestParam(required = false) String entityType,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return audit.search(action, entityType, page, size);
  }

  /** The actions that can appear, for the filter on the page. */
  @GetMapping("/actions")
  public List<String> actions() {
    return java.util.Arrays.stream(AuditAction.values()).map(Enum::name).toList();
  }
}
