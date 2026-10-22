package com.campuscompanion.complaint;

import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.common.web.PageResponse;
import com.campuscompanion.complaint.dto.ComplaintRequest;
import com.campuscompanion.complaint.dto.ComplaintResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
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

/** Students: raise, read and support complaints. Contract: docs/09-api-contract.md section 8.19. */
@RestController
@Validated
@RequestMapping("/api/v1/complaints")
public class ComplaintController {

  private final ComplaintService complaints;

  public ComplaintController(ComplaintService complaints) {
    this.complaints = complaints;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ComplaintResponse raise(
      @AuthenticationPrincipal CurrentUser currentUser,
      @Valid @RequestBody ComplaintRequest request) {
    return complaints.raise(currentUser, request);
  }

  /** The public board. */
  @GetMapping
  public PageResponse<ComplaintResponse> board(
      @AuthenticationPrincipal CurrentUser currentUser,
      @RequestParam(required = false) ComplaintCategory category,
      @RequestParam(required = false) ComplaintStatus status,
      @RequestParam(defaultValue = "newest")
          @Pattern(regexp = "newest|top", message = "Choose newest or top.")
          String sort,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
    return complaints.board(currentUser, category, status, sort, page, size);
  }

  @GetMapping("/mine")
  public PageResponse<ComplaintResponse> mine(
      @AuthenticationPrincipal CurrentUser currentUser,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
    return complaints.mine(currentUser, page, size);
  }

  @GetMapping("/{id}")
  public ComplaintResponse one(
      @AuthenticationPrincipal CurrentUser currentUser, @PathVariable Long id) {
    return complaints.one(currentUser, id);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> withdraw(
      @AuthenticationPrincipal CurrentUser currentUser, @PathVariable Long id) {
    complaints.withdraw(currentUser, id);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{id}/support")
  public ComplaintResponse support(
      @AuthenticationPrincipal CurrentUser currentUser, @PathVariable Long id) {
    return complaints.support(currentUser, id);
  }

  @DeleteMapping("/{id}/support")
  public ComplaintResponse unsupport(
      @AuthenticationPrincipal CurrentUser currentUser, @PathVariable Long id) {
    return complaints.unsupport(currentUser, id);
  }
}
