package com.campuscompanion.student;

import com.campuscompanion.common.error.FieldErrorDetail;
import com.campuscompanion.student.dto.StudentCreateRequest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Checks the roster details of a new student before anything is saved (FR-ADM-001). The limits are
 * the same as the database constraints in V1__core.sql, so a record that passes here fits the
 * tables. This class checks format only; whether the roll number is already taken is checked by
 * {@link StudentProvisioningService}.
 */
@Component
public class StudentRosterValidator {

  static final int MAX_ROLL_NO_LENGTH = 20;
  static final int MAX_NAME_LENGTH = 120;
  static final int MAX_BRANCH_LENGTH = 20;
  static final int MAX_SECTION_LENGTH = 5;
  static final int MAX_PHONE_LENGTH = 20;

  /** Lists every invalid field. An empty list means the record is acceptable. */
  public List<FieldErrorDetail> violations(StudentCreateRequest request) {
    List<FieldErrorDetail> problems = new ArrayList<>();
    checkRollNo(request.rollNo(), problems);
    checkText("fullName", "Full name", request.fullName(), MAX_NAME_LENGTH, problems);
    checkText("branch", "Branch", request.branch(), MAX_BRANCH_LENGTH, problems);
    checkText("section", "Section", request.section(), MAX_SECTION_LENGTH, problems);
    checkRange("currentSemester", "Semester", request.currentSemester(), 1, 8, problems);
    checkRange("admissionYear", "Admission year", request.admissionYear(), 2000, 2100, problems);
    if (request.phone() != null && request.phone().strip().length() > MAX_PHONE_LENGTH) {
      problems.add(
          new FieldErrorDetail(
              "phone", "Phone can have at most " + MAX_PHONE_LENGTH + " characters."));
    }
    return problems;
  }

  private static void checkRollNo(String rollNo, List<FieldErrorDetail> problems) {
    if (rollNo == null || rollNo.isEmpty()) {
      problems.add(new FieldErrorDetail("rollNo", "Roll number is required."));
    } else if (!rollNo.matches("^[A-Za-z0-9]+$")) {
      // Spaces are refused as well, including at the start and the end.
      problems.add(
          new FieldErrorDetail("rollNo", "Roll number can contain only letters and digits."));
    } else if (rollNo.length() > MAX_ROLL_NO_LENGTH) {
      problems.add(
          new FieldErrorDetail(
              "rollNo", "Roll number can have at most " + MAX_ROLL_NO_LENGTH + " characters."));
    }
  }

  private static void checkText(
      String field, String label, String value, int maxLength, List<FieldErrorDetail> problems) {
    if (value == null || value.isBlank()) {
      problems.add(new FieldErrorDetail(field, label + " is required."));
    } else if (value.strip().length() > maxLength) {
      problems.add(
          new FieldErrorDetail(field, label + " can have at most " + maxLength + " characters."));
    }
  }

  private static void checkRange(
      String field,
      String label,
      Integer value,
      int min,
      int max,
      List<FieldErrorDetail> problems) {
    if (value == null) {
      problems.add(new FieldErrorDetail(field, label + " is required."));
    } else if (value < min || value > max) {
      problems.add(
          new FieldErrorDetail(field, label + " must be between " + min + " and " + max + "."));
    }
  }
}
