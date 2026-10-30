package com.campuscompanion.coding;

import com.campuscompanion.coding.dto.CodingAccountRequest;
import com.campuscompanion.coding.dto.CodingAccountResponse;
import com.campuscompanion.coding.dto.LeaderboardResponse;
import com.campuscompanion.coding.dto.LeaderboardVisibility;
import com.campuscompanion.coding.dto.PortfolioResponse;
import com.campuscompanion.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
 * Students: the coding portfolio and the students' leaderboard (FR-COD-001 to FR-COD-004).
 * Contract: docs/09-api-contract.md section 8.24.
 */
@RestController
@Validated
@RequestMapping("/api/v1/coding")
public class CodingController {

  private final CodingAccountService accounts;
  private final CodingLeaderboardService leaderboard;

  public CodingController(CodingAccountService accounts, CodingLeaderboardService leaderboard) {
    this.accounts = accounts;
    this.leaderboard = leaderboard;
  }

  @GetMapping("/portfolio")
  public PortfolioResponse portfolio(@AuthenticationPrincipal CurrentUser user) {
    return accounts.portfolio(user);
  }

  @PostMapping("/accounts")
  @ResponseStatus(HttpStatus.CREATED)
  public CodingAccountResponse connect(
      @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody CodingAccountRequest request) {
    return accounts.connect(user, request);
  }

  @PostMapping("/accounts/{id}/verify")
  public CodingAccountResponse verify(
      @AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
    return accounts.verify(user, id);
  }

  @PostMapping("/accounts/{id}/refresh")
  public CodingAccountResponse refresh(
      @AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
    return accounts.refresh(user, id);
  }

  @DeleteMapping("/accounts/{id}")
  public ResponseEntity<Void> remove(
      @AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
    accounts.remove(user, id);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/leaderboard-visibility")
  public LeaderboardVisibility visibility(
      @AuthenticationPrincipal CurrentUser user,
      @Valid @RequestBody LeaderboardVisibility request) {
    return accounts.setVisibility(user, request);
  }

  @GetMapping("/leaderboard")
  public LeaderboardResponse leaderboard(
      @AuthenticationPrincipal CurrentUser user,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return leaderboard.leaderboard(user, page, size);
  }
}
