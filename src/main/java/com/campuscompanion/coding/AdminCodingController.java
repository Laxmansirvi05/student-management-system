package com.campuscompanion.coding;

import com.campuscompanion.coding.dto.AdminLeaderboardEntry;
import com.campuscompanion.coding.dto.AdminPortfolioResponse;
import com.campuscompanion.common.web.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin: the coding leaderboard of every student and one student's portfolio (FR-COD-005).
 * Contract: docs/09-api-contract.md section 8.24.
 */
@RestController
@Validated
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin/coding")
public class AdminCodingController {

  private final AdminCodingService coding;

  public AdminCodingController(AdminCodingService coding) {
    this.coding = coding;
  }

  @GetMapping("/leaderboard")
  public PageResponse<AdminLeaderboardEntry> leaderboard(
      @RequestParam(required = false) @Size(max = 20) String branch,
      @RequestParam(required = false) @Min(1) @Max(8) Integer semester,
      @RequestParam(required = false) @Size(max = 5) String section,
      @RequestParam(required = false) @Size(max = 100) String q,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return coding.leaderboard(branch, semester, section, q, page, size);
  }

  @GetMapping("/students/{studentId}/portfolio")
  public AdminPortfolioResponse portfolio(@PathVariable Long studentId) {
    return coding.portfolio(studentId);
  }
}
