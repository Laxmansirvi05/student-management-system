package com.campuscompanion.notice;

import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.common.web.PageResponse;
import com.campuscompanion.notice.dto.NoticeDocumentLinkResponse;
import com.campuscompanion.notice.dto.NoticeResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Published notices for any logged-in user. Contract: docs/09-api-contract.md. */
@RestController
@Validated
@RequestMapping("/api/v1/notices")
public class NoticeController {

  private final NoticeService notices;

  public NoticeController(NoticeService notices) {
    this.notices = notices;
  }

  @GetMapping
  public PageResponse<NoticeResponse> list(
      @AuthenticationPrincipal CurrentUser currentUser,
      @RequestParam(required = false) NoticeCategory category,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return notices.visibleTo(currentUser, category, page, size);
  }

  /** A five-minute link to the notice's official document; add download=true to save it. */
  @GetMapping("/{id}/document")
  public NoticeDocumentLinkResponse document(
      @AuthenticationPrincipal CurrentUser currentUser,
      @PathVariable Long id,
      @RequestParam(defaultValue = "false") boolean download) {
    return notices.documentLink(currentUser, id, download);
  }

  @GetMapping("/{id}")
  public NoticeResponse one(
      @AuthenticationPrincipal CurrentUser currentUser, @PathVariable Long id) {
    return notices.oneVisibleTo(currentUser, id);
  }
}
