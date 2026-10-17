package com.campuscompanion.academics;

import static com.campuscompanion.support.ApiTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.support.ApiIntegrationTest;
import com.campuscompanion.support.ApiTestClient;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Tasks T2-06 and T2-07 over real HTTP: the admin's attendance sheet and the student's own
 * attendance (FR-ADM-002, FR-ATT-001, FR-ATT-002). Covers scenarios Z6 and Z11. All data is
 * synthetic.
 */
class AttendanceApiIT extends ApiIntegrationTest {

  private ApiTestClient admin;
  private long first;
  private long second;
  private long otherSection;

  @BeforeEach
  void setUp() throws Exception {
    admin = adminClient("att.admin@university.example");
    first = accounts.student("ATT23001", "ATT", 3, "A");
    second = accounts.student("ATT23002", "ATT", 3, "A");
    otherSection = accounts.student("ATT23003", "ATT", 3, "B");
  }

  @Test
  void summaryCountsClassesAndFlagsOnlySubjectsBelowTheRequiredPercentage() throws Exception {
    // A branch of its own, so the subjects of the other tests cannot join these totals.
    first = accounts.student("ATS23001", "ATS", 3, "A");
    second = accounts.student("ATS23002", "ATS", 3, "A");
    long exactlyRequired = subject(admin, "ATS101", "ATS", 3);
    long below = subject(admin, "ATS102", "ATS", 3);
    subject(admin, "ATS103", "ATS", 3);
    // Subject one: the first student attends 3 of 4, exactly 75%. Subject two: 2 of 3.
    for (int daysAgo = 4; daysAgo >= 1; daysAgo--) {
      save(
          exactlyRequired,
          today().minusDays(daysAgo),
          1,
          daysAgo == 4 ? "ABSENT" : "PRESENT",
          "PRESENT");
    }
    for (int daysAgo = 3; daysAgo >= 1; daysAgo--) {
      save(below, today().minusDays(daysAgo), 2, daysAgo == 2 ? "ABSENT" : "PRESENT", "PRESENT");
    }

    JsonNode summary = json(studentClient("ATS23001").get("/api/v1/attendance/me"));

    assertThat(summary.get("requiredPercent").decimalValue()).isEqualByComparingTo("75");
    assertThat(summary.get("overallPercent").decimalValue()).isEqualByComparingTo("71.43");
    JsonNode subjects = summary.get("subjects");
    assertThat(texts(subjects, "subjectCode")).containsExactly("ATS101", "ATS102", "ATS103");
    assertSubject(subjects.get(0), 4, 3, "75.00", false);
    assertSubject(subjects.get(1), 3, 2, "66.67", true);
    assertThat(subjects.get(2).get("classesHeld").asInt()).isZero();
    assertThat(subjects.get(2).get("percent").isNull()).as("no classes is not 0%").isTrue();
    assertThat(subjects.get(2).get("belowRequired").asBoolean()).isFalse();

    JsonNode other = json(studentClient("ATS23002").get("/api/v1/attendance/me"));
    assertThat(other.get("overallPercent").decimalValue())
        .as("each student sees their own figures")
        .isEqualByComparingTo("100.00");

    JsonNode history =
        json(studentClient("ATS23001").get("/api/v1/attendance/me/subjects/" + exactlyRequired));
    assertThat(history.get("totalElements").asInt()).isEqualTo(4);
    assertThat(history.get("content").get(0).get("classDate").asString())
        .as("newest first")
        .isEqualTo(today().minusDays(1).toString());
    assertThat(history.get("content").get(3).get("status").asString()).isEqualTo("ABSENT");
    JsonNode secondPage =
        json(
            studentClient("ATS23001")
                .get("/api/v1/attendance/me/subjects/" + exactlyRequired + "?page=1&size=3"));
    assertThat(secondPage.get("content").size()).isEqualTo(1);
    assertThat(secondPage.get("totalPages").asInt()).isEqualTo(2);
    assertProblem(
        studentClient("ATS23001")
            .get("/api/v1/attendance/me/subjects/" + exactlyRequired + "?size=101"),
        400,
        "VALIDATION_FAILED");
    assertThat(
            json(studentClient("ATS23001").get("/api/v1/attendance/me?studentId=" + second))
                .get("overallPercent")
                .decimalValue())
        .as("a student identifier in the request changes nothing")
        .isEqualByComparingTo("71.43");
  }

  @Test
  void savingASheetAgainCorrectsItAndAuditsOnlyTheCorrections() throws Exception {
    long subjectId = subject(admin, "ATT201", "ATT", 3);
    LocalDate date = today().minusDays(1);

    JsonNode saved = json(save(subjectId, date, 1, "PRESENT", "ABSENT"));
    assertThat(saved.get("saved").asInt()).isEqualTo(2);
    assertThat(saved.get("changed").asInt()).isEqualTo(2);
    assertThat(json(save(subjectId, date, 1, "PRESENT", "ABSENT")).get("changed").asInt())
        .as("the same sheet again changes nothing")
        .isZero();
    assertThat(auditCount()).isZero();

    assertThat(json(save(subjectId, date, 1, "PRESENT", "PRESENT")).get("changed").asInt())
        .isEqualTo(1);

    assertThat(rowsOf(subjectId)).as("corrected, never duplicated").isEqualTo(2);
    assertThat(auditCount()).isEqualTo(1);
    assertThat(latestAudit("ATTENDANCE_CORRECTED"))
        .isEqualTo("{\"status\": \"ABSENT\"} | {\"status\": \"PRESENT\"}");

    JsonNode sheet =
        json(
            admin.get(
                "/api/v1/admin/attendance?subjectId=%d&section=A&classDate=%s&periodNo=1"
                    .formatted(subjectId, date)));
    assertThat(texts(sheet.get("entries"), "rollNo")).containsExactly("ATT23001", "ATT23002");
    assertThat(texts(sheet.get("entries"), "status")).containsExactly("PRESENT", "PRESENT");
    JsonNode unsaved =
        json(
            admin.get(
                "/api/v1/admin/attendance?subjectId=%d&section=A&classDate=%s&periodNo=2"
                    .formatted(subjectId, date)));
    assertThat(unsaved.get("entries").get(0).get("status").isNull())
        .as("nothing saved for period 2")
        .isTrue();
  }

  @Test
  void aBadSheetIsRejectedWholeAndNothingIsSaved() throws Exception {
    long subjectId = subject(admin, "ATT301", "ATT", 3);

    assertThat(invalidFields(save(subjectId, today().plusDays(1), 1, "PRESENT", "PRESENT")))
        .containsExactly("classDate");
    assertThat(
            invalidFields(
                admin.put(
                    "/api/v1/admin/attendance",
                    sheet(
                        subjectId,
                        today(),
                        1,
                        entry(first, "PRESENT"),
                        entry(otherSection, "PRESENT")))))
        .containsExactly("entries[1].studentId");
    assertThat(
            invalidFields(
                admin.put(
                    "/api/v1/admin/attendance",
                    sheet(subjectId, today(), 1, entry(first, "PRESENT"), entry(first, "ABSENT")))))
        .containsExactly("entries[1].studentId");
    assertThat(
            invalidFields(
                admin.put(
                    "/api/v1/admin/attendance",
                    sheet(
                        subjectId,
                        today(),
                        1,
                        entry(first, "PRESENT"),
                        "{\"studentId\":" + second + ",\"status\":null}"))))
        .containsExactly("entries[1].status");
    assertThat(invalidFields(save(999999, today(), 1, "PRESENT", "PRESENT")))
        .containsExactly("subjectId");
    assertProblem(
        admin.put(
            "/api/v1/admin/attendance", sheet(subjectId, today(), 13, entry(first, "PRESENT"))),
        400,
        "VALIDATION_FAILED");

    assertThat(rowsOf(subjectId)).isZero();
  }

  @Test
  void onlyAdminsWriteAttendanceAndOnlyStudentsReadTheirOwn() throws Exception {
    long subjectId = subject(admin, "ATT401", "ATT", 3);
    ApiTestClient student = studentClient("ATT23001");
    String body = sheet(subjectId, today(), 1, entry(first, "PRESENT"));

    assertProblem(student.put("/api/v1/admin/attendance", body), 403, "ACCESS_DENIED");
    assertProblem(
        student.get(
            "/api/v1/admin/attendance?subjectId=%d&section=A&classDate=%s&periodNo=1"
                .formatted(subjectId, today())),
        403,
        "ACCESS_DENIED");
    assertProblem(admin.get("/api/v1/attendance/me"), 403, "ACCESS_DENIED");
    assertProblem(visitor().get("/api/v1/attendance/me"), 401, "AUTH_REQUIRED");
    assertProblem(visitor().put("/api/v1/admin/attendance", body), 401, "AUTH_REQUIRED");
    assertThat(rowsOf(subjectId)).as("the database is unchanged").isZero();
  }

  private HttpResponse<String> save(
      long subjectId, LocalDate date, int period, String firstStatus, String secondStatus)
      throws Exception {
    return admin.put(
        "/api/v1/admin/attendance",
        sheet(subjectId, date, period, entry(first, firstStatus), entry(second, secondStatus)));
  }

  private static String sheet(long subjectId, LocalDate date, int period, String... entries) {
    return "{\"subjectId\":%d,\"section\":\"A\",\"classDate\":\"%s\",\"periodNo\":%d,\"entries\":[%s]}"
        .formatted(subjectId, date, period, String.join(",", entries));
  }

  private static String entry(long studentId, String status) {
    return "{\"studentId\":%d,\"status\":\"%s\"}".formatted(studentId, status);
  }

  private static void assertSubject(
      JsonNode subject, int held, int attended, String percent, boolean below) {
    assertThat(subject.get("classesHeld").asInt()).isEqualTo(held);
    assertThat(subject.get("classesAttended").asInt()).isEqualTo(attended);
    assertThat(subject.get("percent").decimalValue()).isEqualByComparingTo(new BigDecimal(percent));
    assertThat(subject.get("belowRequired").asBoolean()).isEqualTo(below);
  }

  private int rowsOf(long subjectId) {
    return jdbc.queryForObject(
        "select count(*) from app.attendance_records where subject_id = ?",
        Integer.class,
        subjectId);
  }

  private int auditCount() {
    return jdbc.queryForObject(
        "select count(*) from app.audit_logs a join app.attendance_records r on r.id::text = a.entity_id"
            + " join app.subjects s on s.id = r.subject_id"
            + " where a.action = 'ATTENDANCE_CORRECTED' and a.entity_type = 'ATTENDANCE_RECORD'"
            + " and s.code = 'ATT201'",
        Integer.class);
  }
}
