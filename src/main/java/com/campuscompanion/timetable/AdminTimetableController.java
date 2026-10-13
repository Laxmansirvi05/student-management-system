package com.campuscompanion.timetable;

import com.campuscompanion.timetable.dto.AdminTimetableEntryResponse;
import com.campuscompanion.timetable.dto.TimetableWriteRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

/** Admin: the timetable. Contract: docs/09-api-contract.md section 7. */
@RestController
@Validated
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin/timetable")
public class AdminTimetableController {

  private final AdminTimetableService timetable;

  public AdminTimetableController(AdminTimetableService timetable) {
    this.timetable = timetable;
  }

  @GetMapping
  public List<AdminTimetableEntryResponse> list(
      @RequestParam(required = false) String branch,
      @RequestParam(required = false) @Min(1) @Max(8) Integer semester,
      @RequestParam(required = false) String section) {
    return timetable.list(branch, semester, section);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AdminTimetableEntryResponse create(@Valid @RequestBody TimetableWriteRequest request) {
    return timetable.create(request);
  }

  @PutMapping("/{id}")
  public AdminTimetableEntryResponse update(
      @PathVariable Long id, @Valid @RequestBody TimetableWriteRequest request) {
    return timetable.update(id, request);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    timetable.delete(id);
    return ResponseEntity.noContent().build();
  }
}
