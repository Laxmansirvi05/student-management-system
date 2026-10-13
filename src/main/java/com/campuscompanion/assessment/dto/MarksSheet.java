package com.campuscompanion.assessment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * The marks of one assessment, one entry per student. The same shape is read and saved. Roll number
 * and name are sent to the admin for display and are ignored when the sheet comes back.
 */
public record MarksSheet(
    @NotEmpty(message = "The sheet has no students.") @Valid List<Entry> entries) {

  public record Entry(
      @NotNull(message = "The student is missing.") Long studentId,
      String rollNo,
      String fullName,
      BigDecimal marksObtained,
      @NotNull(message = "Say whether the student was absent.") Boolean absent) {}
}
