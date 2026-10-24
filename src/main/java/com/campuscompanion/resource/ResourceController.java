package com.campuscompanion.resource;

import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.common.web.PageResponse;
import com.campuscompanion.resource.dto.DownloadUrlResponse;
import com.campuscompanion.resource.dto.ResourceReportRequest;
import com.campuscompanion.resource.dto.ResourceReportResponse;
import com.campuscompanion.resource.dto.ResourceResponse;
import com.campuscompanion.resource.dto.ResourceSubjectResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

/**
 * The resource hub: search, one resource, its signed link, question papers and bookmarks. Contract:
 * docs/09-api-contract.md sections 8.5 and 8.6.
 */
@RestController
@Validated
@RequestMapping("/api/v1")
public class ResourceController {

  private final ResourceService resources;
  private final BookmarkService bookmarks;
  private final ResourceReportService reports;

  public ResourceController(
      ResourceService resources, BookmarkService bookmarks, ResourceReportService reports) {
    this.resources = resources;
    this.bookmarks = bookmarks;
    this.reports = reports;
  }

  @GetMapping("/resources")
  public PageResponse<ResourceResponse> search(
      @AuthenticationPrincipal CurrentUser currentUser,
      @RequestParam(required = false) Long subjectId,
      @RequestParam(required = false) Long unitId,
      @RequestParam(required = false) @Min(1) @Max(8) Integer semester,
      @RequestParam(required = false) ResourceType type,
      @RequestParam(required = false) @Size(min = 2, max = 100) String q,
      @RequestParam(defaultValue = "createdAt,desc")
          @Pattern(regexp = "(createdAt|title),(asc|desc)", message = "Choose a listed sort order.")
          String sort,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return resources.search(currentUser, subjectId, unitId, semester, type, q, sort, page, size);
  }

  /** The subject folders of the resource hub, with how many resources each has. */
  @GetMapping("/resources/subjects")
  public List<ResourceSubjectResponse> subjectFolders() {
    return resources.subjectFolders();
  }

  @GetMapping("/resources/{id}")
  public ResourceResponse one(
      @AuthenticationPrincipal CurrentUser currentUser, @PathVariable Long id) {
    return resources.get(currentUser, id);
  }

  @GetMapping("/resources/{id}/download-url")
  public DownloadUrlResponse downloadUrl(
      @AuthenticationPrincipal CurrentUser currentUser,
      @PathVariable Long id,
      @RequestParam(defaultValue = "false") boolean download) {
    return resources.downloadUrl(currentUser, id, download);
  }

  @GetMapping("/question-papers")
  public PageResponse<ResourceResponse> papers(
      @AuthenticationPrincipal CurrentUser currentUser,
      @RequestParam(required = false) Long subjectId,
      @RequestParam(required = false) @Min(2000) @Max(2100) Integer examYear,
      @RequestParam(required = false) ExamType examType,
      @RequestParam(required = false) @Size(min = 2, max = 100) String q,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return resources.papers(currentUser, subjectId, examYear, examType, q, page, size);
  }

  @GetMapping("/bookmarks/me")
  public PageResponse<ResourceResponse> bookmarks(
      @AuthenticationPrincipal CurrentUser currentUser,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return bookmarks.list(currentUser, page, size);
  }

  @PutMapping("/bookmarks/{resourceId}")
  public ResponseEntity<Void> addBookmark(
      @AuthenticationPrincipal CurrentUser currentUser, @PathVariable Long resourceId) {
    bookmarks.add(currentUser, resourceId);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/bookmarks/{resourceId}")
  public ResponseEntity<Void> removeBookmark(
      @AuthenticationPrincipal CurrentUser currentUser, @PathVariable Long resourceId) {
    bookmarks.remove(currentUser, resourceId);
    return ResponseEntity.noContent().build();
  }

  /** A student reports a problem with a resource (FR-LIB-005). */
  @PostMapping("/resources/{id}/reports")
  @ResponseStatus(HttpStatus.CREATED)
  public ResourceReportResponse report(
      @AuthenticationPrincipal CurrentUser currentUser,
      @PathVariable Long id,
      @Valid @RequestBody ResourceReportRequest request) {
    return reports.report(currentUser, id, request);
  }
}
