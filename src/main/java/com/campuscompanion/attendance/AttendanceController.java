package com.campuscompanion.attendance;

import com.campuscompanion.attendance.dto.AttendanceEntryResponse;
import com.campuscompanion.attendance.dto.AttendanceSummaryResponse;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.common.web.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A student's own attendance. The paths end in "/me" and take no student identifier. Contract:
 * docs/09-api-contract.md section 8.3.
 */
@RestController
@Validated
@RequestMapping("/api/v1/attendance/me")
public class AttendanceController {

  private final AttendanceService attendance;

  public AttendanceController(AttendanceService attendance) {
    this.attendance = attendance;
  }

  @GetMapping
  public AttendanceSummaryResponse summary(@AuthenticationPrincipal CurrentUser currentUser) {
    return attendance.summaryFor(currentUser);
  }

  @GetMapping("/subjects/{subjectId}")
  public PageResponse<AttendanceEntryResponse> history(
      @AuthenticationPrincipal CurrentUser currentUser,
      @PathVariable Long subjectId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return attendance.historyFor(currentUser, subjectId, page, size);
  }
}
