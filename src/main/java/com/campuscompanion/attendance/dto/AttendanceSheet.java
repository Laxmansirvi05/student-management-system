package com.campuscompanion.attendance.dto;

import com.campuscompanion.attendance.AttendanceStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/**
 * The attendance of one class: a subject, a section, a date and a period, with one entry per
 * student. The same shape is read and saved. Roll number and name are sent to the admin for display
 * and are ignored when the sheet comes back.
 */
public record AttendanceSheet(
    @NotNull(message = "Choose a subject.") Long subjectId,
    @NotBlank(message = "Choose a section.") String section,
    @NotNull(message = "Choose a date.") LocalDate classDate,
    @NotNull(message = "Choose a period.")
        @Min(value = 1, message = "Period must be 1 to 12.")
        @Max(value = 12, message = "Period must be 1 to 12.")
        Integer periodNo,
    @NotEmpty(message = "The sheet has no students.") @Valid List<Entry> entries) {

  public record Entry(
      @NotNull(message = "The student is missing.") Long studentId,
      String rollNo,
      String fullName,
      AttendanceStatus status) {}
}
