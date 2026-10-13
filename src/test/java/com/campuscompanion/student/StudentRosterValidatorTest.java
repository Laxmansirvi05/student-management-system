package com.campuscompanion.student;

import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.common.error.FieldErrorDetail;
import com.campuscompanion.student.dto.StudentCreateRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Task T1-10, scenario PV3: which roster records are refused, and for which field. */
class StudentRosterValidatorTest {

  private final StudentRosterValidator validator = new StudentRosterValidator();

  @Test
  void acceptsACompleteRecordInEitherLetterCase() {
    assertThat(validator.violations(student("23CSE001"))).isEmpty();
    assertThat(validator.violations(student("23cse001"))).isEmpty();
    assertThat(validator.violations(student("A".repeat(20)))).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"23 CSE001", " 23CSE001", "23CSE001 ", "23-CSE-001", "23CSE001@", "a@b.c", "२३"})
  void refusesARollNumberWithAnythingButLettersAndDigits(String rollNo) {
    assertThat(fields(validator.violations(student(rollNo)))).containsExactly("rollNo");
  }

  @Test
  void refusesAMissingOrOverlongRollNumber() {
    assertThat(fields(validator.violations(student(null)))).containsExactly("rollNo");
    assertThat(fields(validator.violations(student("")))).containsExactly("rollNo");
    assertThat(fields(validator.violations(student("A".repeat(21))))).containsExactly("rollNo");
  }

  @Test
  void namesEveryMissingRequiredField() {
    StudentCreateRequest empty = new StudentCreateRequest(null, null, " ", null, "", null, null);

    assertThat(fields(validator.violations(empty)))
        .containsExactlyInAnyOrder(
            "rollNo", "fullName", "branch", "section", "currentSemester", "admissionYear");
  }

  @Test
  void refusesValuesOutsideTheLimitsOfTheTables() {
    StudentCreateRequest tooBig =
        new StudentCreateRequest(
            "23CSE001", "N".repeat(121), "B".repeat(21), 9, "S".repeat(6), 1999, "9".repeat(21));

    assertThat(fields(validator.violations(tooBig)))
        .containsExactlyInAnyOrder(
            "fullName", "branch", "section", "currentSemester", "admissionYear", "phone");
    assertThat(fields(validator.violations(withSemesterAndYear(0, 2101))))
        .containsExactlyInAnyOrder("currentSemester", "admissionYear");
    assertThat(validator.violations(withSemesterAndYear(1, 2000))).isEmpty();
    assertThat(validator.violations(withSemesterAndYear(8, 2100))).isEmpty();
  }

  private static StudentCreateRequest student(String rollNo) {
    return new StudentCreateRequest(rollNo, "Demo Student One", "CSE", 3, "A", 2023, null);
  }

  private static StudentCreateRequest withSemesterAndYear(int semester, int year) {
    return new StudentCreateRequest("23CSE001", "Demo Student One", "CSE", semester, "A", year, "");
  }

  private static List<String> fields(List<FieldErrorDetail> problems) {
    return problems.stream().map(FieldErrorDetail::field).toList();
  }
}
