package com.campuscompanion.attendance;

import com.campuscompanion.attendance.dto.AttendanceSheet;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.common.web.SheetSaved;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin: the attendance sheet of one class. Contract: docs/09-api-contract.md section 8.9. */
@RestController
@Validated
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin/attendance")
public class AdminAttendanceController {

  private final AdminAttendanceService attendance;

  public AdminAttendanceController(AdminAttendanceService attendance) {
    this.attendance = attendance;
  }

  @GetMapping
  public AttendanceSheet sheet(
      @RequestParam Long subjectId,
      @RequestParam @NotBlank String section,
      @RequestParam LocalDate classDate,
      @RequestParam @Min(1) @Max(12) int periodNo) {
    return attendance.sheet(subjectId, section, classDate, periodNo);
  }

  @PutMapping
  public SheetSaved save(
      @Valid @RequestBody AttendanceSheet sheet, @AuthenticationPrincipal CurrentUser admin) {
    return attendance.save(sheet, admin);
  }
}
