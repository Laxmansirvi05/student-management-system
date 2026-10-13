package com.campuscompanion.notice;

import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.common.web.PageResponse;
import com.campuscompanion.notice.dto.NoticeResponse;
import com.campuscompanion.notice.dto.NoticeWriteRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

/** Admin: notices, drafts included. Contract: docs/09-api-contract.md section 7. */
@RestController
@Validated
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin/notices")
public class AdminNoticeController {

  private final AdminNoticeService notices;

  public AdminNoticeController(AdminNoticeService notices) {
    this.notices = notices;
  }

  @GetMapping
  public PageResponse<NoticeResponse> list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return notices.list(page, size);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public NoticeResponse create(
      @Valid @RequestBody NoticeWriteRequest request, @AuthenticationPrincipal CurrentUser admin) {
    return notices.create(request, admin);
  }

  @PutMapping("/{id}")
  public NoticeResponse update(
      @PathVariable Long id, @Valid @RequestBody NoticeWriteRequest request) {
    return notices.update(id, request);
  }

  @PostMapping("/{id}/publish")
  public ResponseEntity<Void> publish(@PathVariable Long id) {
    notices.publish(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/unpublish")
  public ResponseEntity<Void> unpublish(@PathVariable Long id) {
    notices.unpublish(id);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    notices.delete(id);
    return ResponseEntity.noContent().build();
  }
}
