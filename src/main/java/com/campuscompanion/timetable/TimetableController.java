package com.campuscompanion.timetable;

import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.timetable.dto.TimetableEntryResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** A student's own timetable. Contract: docs/09-api-contract.md. */
@RestController
@Validated
@RequestMapping("/api/v1/timetable/me")
public class TimetableController {

  private final TimetableService timetable;

  public TimetableController(TimetableService timetable) {
    this.timetable = timetable;
  }

  @GetMapping
  public List<TimetableEntryResponse> timetable(
      @AuthenticationPrincipal CurrentUser currentUser,
      @RequestParam(required = false) @Min(1) @Max(7) Integer day) {
    return timetable.timetableFor(currentUser, day);
  }
}
