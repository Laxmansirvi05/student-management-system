package com.campuscompanion.assessment;

import static com.campuscompanion.support.ApiTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.support.ApiIntegrationTest;
import com.campuscompanion.support.ApiTestClient;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Subject grades with every attempt, and semesters for which the college published no SGPA
 * (docs/08-database-design.md 4.8a). All data is synthetic.
 */
class SubjectResultsIT extends ApiIntegrationTest {

  private void result(
      long student, int semester, String sgpa, int registered, int earned, String status) {
    jdbc.update(
        "insert into app.semester_results (student_id, semester, sgpa, credits_registered,"
            + " credits_earned, result_status, published_at) values (?, ?, ?::numeric, ?, ?, ?, now())",
        student,
        semester,
        sgpa,
        registered,
        earned,
        status);
  }

  private void grade(
      long student,
      int semester,
      String code,
      String credits,
      String grade,
      int attempt,
      String exam) {
    jdbc.update(
        "insert into app.subject_results (student_id, semester, subject_code, subject_name, credits,"
            + " grade, attempt, exam) values (?, ?, ?, ?, ?::numeric, ?, ?, ?)",
        student,
        semester,
        code,
        "Subject " + code,
        credits,
        grade,
        attempt,
        exam);
  }

  @Test
  void aStudentSeesEachSubjectWithItsLatestGrade() throws Exception {
    long student = accounts.student("23SUB001", "SUB", 3, "A");
    result(student, 1, "7.50", 18, 18, "PASS");
    grade(student, 1, "SUB101", "4", "F", 1, "I Year I Sem Regular");
    grade(student, 1, "SUB101", "4", "B", 2, "I Year I Sem Supplementary, July 2026");
    grade(student, 1, "SUB102", "3", "A+", 1, "I Year I Sem Regular");
    grade(student, 1, "SUB1MC", null, "S", 1, "I Year I Sem Regular");

    JsonNode semester =
        json(studentClient("23SUB001").get("/api/v1/results/me")).get("semesters").get(0);

    assertThat(semester.get("sgpa").decimalValue()).isEqualByComparingTo("7.50");
    JsonNode subjects = semester.get("subjects");
    assertThat(subjects).hasSize(3);
    JsonNode cleared = subjects.get(0);
    assertThat(cleared.get("subjectCode").asString()).isEqualTo("SUB101");
    assertThat(cleared.get("grade").asString()).isEqualTo("B");
    assertThat(cleared.get("gradePoint").asInt()).isEqualTo(6);
    assertThat(semester.get("cgpaToDate").decimalValue()).isEqualByComparingTo("7.50");
    assertThat(cleared.get("firstGrade").asString()).isEqualTo("F");
    assertThat(cleared.get("attempts").asInt()).isEqualTo(2);
    assertThat(cleared.get("exam").asString()).contains("Supplementary");
    assertThat(cleared.get("passed").asBoolean()).isTrue();
    assertThat(subjects.get(1).get("firstGrade").isNull()).isTrue();
    assertThat(subjects.get(2).get("credits").isNull()).isTrue();
    assertThat(subjects.get(2).get("gradePoint").isNull()).isTrue();
  }

  @Test
  void aSemesterWithAFailedSubjectHasNoSgpaAndNoCgpa() throws Exception {
    long student = accounts.student("23SUB002", "SUB", 3, "A");
    result(student, 1, "8.39", 18, 18, "PASS");
    result(student, 2, null, 22, 19, "FAIL");
    grade(student, 2, "SUB201", "3", "F", 1, "I Year II Sem Regular, July 2026");

    JsonNode results = json(studentClient("23SUB002").get("/api/v1/results/me"));

    assertThat(results.get("cgpa").isNull()).isTrue();
    JsonNode second = results.get("semesters").get(1);
    assertThat(second.get("sgpa").isNull()).isTrue();
    assertThat(results.get("semesters").get(0).get("cgpaToDate").decimalValue())
        .isEqualByComparingTo("8.39");
    assertThat(second.get("cgpaToDate").isNull()).isTrue();
    assertThat(second.get("subjects").get(0).get("passed").asBoolean()).isFalse();
  }

  @Test
  void thereIsNoPassedSemesterWithoutAnSgpa() throws Exception {
    long student = accounts.student("23SUB003", "SUB", 3, "A");
    ApiTestClient admin = adminClient("sub-admin@university.example");
    String withoutSgpa =
        """
        {"studentId":%d,"semester":1,"sgpa":null,"creditsRegistered":18,"creditsEarned":%d,
         "resultStatus":"%s","published":true}
        """;

    assertThat(
            invalidFields(
                admin.put(
                    "/api/v1/admin/semester-results", withoutSgpa.formatted(student, 18, "PASS"))))
        .containsExactly("sgpa");
    assertThat(
            admin
                .put("/api/v1/admin/semester-results", withoutSgpa.formatted(student, 15, "FAIL"))
                .statusCode())
        .isEqualTo(200);
  }

  @Test
  void theDatabaseRefusesAPassedSemesterWithoutSgpa() {
    long student = accounts.student("23SUB004", "SUB", 3, "A");

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> result(student, 1, null, 18, 18, "PASS"))
        .hasMessageContaining("semester_results_sgpa_required_when_passed");
  }
}
