package com.campuscompanion.pyq;

import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.common.web.PageResponse;
import com.campuscompanion.pyq.dto.PyqLinkResponse;
import com.campuscompanion.pyq.dto.PyqOverviewResponse;
import com.campuscompanion.pyq.dto.PyqPaperResponse;
import com.campuscompanion.pyq.dto.PyqSubjectResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Students: the previous-year papers. Contract: docs/09-api-contract.md section 8.17. */
@RestController
@Validated
@RequestMapping("/api/v1/pyqs")
public class PyqController {

  private final PyqService pyqs;

  public PyqController(PyqService pyqs) {
    this.pyqs = pyqs;
  }

  @GetMapping("/overview")
  public PyqOverviewResponse overview() {
    return pyqs.overview();
  }

  @GetMapping("/subjects")
  public List<PyqSubjectResponse> subjects(
      @RequestParam(required = false) @Size(max = 10) String branch,
      @RequestParam(required = false) @Size(max = 100) String q) {
    return pyqs.subjects(branch, q);
  }

  @GetMapping
  public PageResponse<PyqPaperResponse> search(
      @AuthenticationPrincipal CurrentUser currentUser,
      @RequestParam(required = false) @Size(max = 10) String branch,
      @RequestParam(required = false) @Size(max = 120) String subject,
      @RequestParam(required = false) @Min(2000) @Max(2100) Integer year,
      @RequestParam(required = false) PyqExamKind kind,
      @RequestParam(required = false) @Size(max = 10) String regulation,
      @RequestParam(required = false) @Size(max = 100) String q,
      @RequestParam(required = false) @Size(max = 10) String sort,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return pyqs.search(currentUser, branch, subject, year, kind, regulation, q, sort, page, size);
  }

  @GetMapping("/saved")
  public PageResponse<PyqPaperResponse> saved(
      @AuthenticationPrincipal CurrentUser currentUser,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return pyqs.saved(currentUser, page, size);
  }

  @GetMapping("/{id}/link")
  public PyqLinkResponse link(
      @PathVariable Long id, @RequestParam(defaultValue = "false") boolean download) {
    return pyqs.link(id, download);
  }

  @PutMapping("/{id}/save")
  public ResponseEntity<Void> save(
      @AuthenticationPrincipal CurrentUser currentUser, @PathVariable Long id) {
    pyqs.save(currentUser, id);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{id}/save")
  public ResponseEntity<Void> unsave(
      @AuthenticationPrincipal CurrentUser currentUser, @PathVariable Long id) {
    pyqs.unsave(currentUser, id);
    return ResponseEntity.noContent().build();
  }
}
