package com.campuscompanion.resource;

import static com.campuscompanion.support.ApiTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.support.ApiIntegrationTest;
import com.campuscompanion.support.ApiTestClient;
import com.campuscompanion.support.TestPdfs;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Reporting a resource (FR-LIB-005): students report, admins list and resolve. Synthetic data. */
class ResourceReportIT extends ApiIntegrationTest {

  private static final String ADMIN_REPORTS = "/api/v1/admin/resource-reports";

  private ApiTestClient admin;
  private long subjectId;

  @BeforeEach
  void setUp() throws Exception {
    admin = adminClient("reports-admin@university.example");
    subjectId = subject(admin, "RPT" + System.nanoTime() % 100000, "RPT", 3);
  }

  private long upload(String title) throws Exception {
    HttpResponse<String> response =
        admin.postMultipart(
            "/api/v1/admin/resources",
            "{\"title\":\"%s\",\"subjectId\":%d,\"type\":\"NOTES\"}".formatted(title, subjectId),
            "file.pdf",
            "application/pdf",
            TestPdfs.valid(1));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    return json(response).get("id").asLong();
  }

  private ApiTestClient student(String rollNo) throws Exception {
    accounts.student(rollNo, "RPT", 3, "A");
    return studentClient(rollNo);
  }

  private static String reason(String text) {
    return "{\"reason\":\"%s\"}".formatted(text);
  }

  @Test
  void aStudentReportsAResourceOnceAndAnAdminSeesAndResolvesIt() throws Exception {
    long id = upload("Unit 2 notes");
    ApiTestClient me = student("23RPT001");

    HttpResponse<String> reported =
        me.post("/api/v1/resources/" + id + "/reports", reason("  Pages 3 to 5 are missing.  "));
    assertThat(reported.statusCode()).as(reported.body()).isEqualTo(201);
    JsonNode report = json(reported);
    assertThat(report.get("status").asString()).isEqualTo("OPEN");
    assertThat(report.get("reason").asString()).isEqualTo("Pages 3 to 5 are missing.");
    assertThat(report.get("reportedBy").isNull()).isTrue();

    // The same problem again is not a second report.
    assertProblem(
        me.post("/api/v1/resources/" + id + "/reports", reason("Still missing pages.")),
        409,
        "CONFLICT");
    // Another student may report it too.
    assertThat(
            student("23RPT002")
                .post("/api/v1/resources/" + id + "/reports", reason("Wrong subject."))
                .statusCode())
        .isEqualTo(201);

    JsonNode open = json(admin.get(ADMIN_REPORTS)).get("content");
    assertThat(open.valueStream().filter(r -> r.get("resourceId").asLong() == id).count())
        .isEqualTo(2);
    JsonNode mine =
        open.valueStream()
            .filter(r -> r.get("id").asLong() == report.get("id").asLong())
            .findFirst()
            .orElseThrow();
    assertThat(mine.get("resourceTitle").asString()).isEqualTo("Unit 2 notes");
    assertThat(mine.get("reportedBy").asString()).isEqualTo("23RPT001");

    long reportId = report.get("id").asLong();
    JsonNode resolved = json(admin.post(ADMIN_REPORTS + "/" + reportId + "/resolve", ""));
    assertThat(resolved.get("status").asString()).isEqualTo("RESOLVED");
    assertThat(resolved.get("resolvedAt").isNull()).isFalse();
    String firstResolvedAt = resolved.get("resolvedAt").asString();
    assertThat(
            json(admin.post(ADMIN_REPORTS + "/" + reportId + "/resolve", ""))
                .get("resolvedAt")
                .asString())
        .isEqualTo(firstResolvedAt);
    assertThat(
            json(admin.get(ADMIN_REPORTS + "?status=RESOLVED"))
                .get("content")
                .valueStream()
                .anyMatch(r -> r.get("id").asLong() == reportId))
        .isTrue();

    // Resolved once: one audit row, by status only, never the reason or the student.
    java.util.List<String> audited =
        jdbc.queryForList(
            "select new_value::text from app.audit_logs where action = 'RESOURCE_REPORT_RESOLVED'"
                + " and entity_type = 'RESOURCE_REPORT' and entity_id = ?",
            String.class,
            String.valueOf(reportId));
    assertThat(audited).hasSize(1);
    assertThat(audited.get(0)).contains("RESOLVED").doesNotContain("missing", "23RPT001");
    // Once resolved, the student may report a new problem.
    assertThat(
            me.post("/api/v1/resources/" + id + "/reports", reason("A new problem now."))
                .statusCode())
        .isEqualTo(201);
  }

  @Test
  void onlyStudentsReportOnlyVisibleResourcesAndOnlyAdminsListThem() throws Exception {
    long id = upload("Some notes");
    ApiTestClient me = student("23RPT003");

    assertProblem(
        visitor().post("/api/v1/resources/" + id + "/reports", reason("Broken file.")),
        401,
        "AUTH_REQUIRED");
    assertProblem(
        admin.post("/api/v1/resources/" + id + "/reports", reason("Broken file.")),
        403,
        "ACCESS_DENIED");
    assertProblem(me.get(ADMIN_REPORTS), 403, "ACCESS_DENIED");
    assertProblem(me.post(ADMIN_REPORTS + "/1/resolve", ""), 403, "ACCESS_DENIED");
    assertProblem(visitor().get(ADMIN_REPORTS), 401, "AUTH_REQUIRED");

    assertProblem(
        me.post("/api/v1/resources/999999/reports", reason("Broken file.")), 404, "NOT_FOUND");
    jdbc.update("update app.learning_resources set status = 'HIDDEN' where id = ?", id);
    assertProblem(
        me.post("/api/v1/resources/" + id + "/reports", reason("Broken file.")), 404, "NOT_FOUND");
    assertProblem(admin.post(ADMIN_REPORTS + "/999999/resolve", ""), 404, "NOT_FOUND");
  }

  @Test
  void aReasonMustBeFiveToFiveHundredCharacters() throws Exception {
    long id = upload("Notes to report");
    ApiTestClient me = student("23RPT004");
    String path = "/api/v1/resources/" + id + "/reports";
    assertThat(invalidFields(me.post(path, reason("bad")))).containsExactly("reason");
    assertThat(invalidFields(me.post(path, reason("   ")))).containsOnly("reason");
    assertThat(invalidFields(me.post(path, reason("x".repeat(501))))).containsExactly("reason");
    assertThat(invalidFields(me.post(path, "{}"))).containsExactly("reason");
    assertProblem(
        me.post(path, "{\"reason\":\"Broken file.\",\"studentId\":1}"), 400, "VALIDATION_FAILED");
    assertProblem(admin.get(ADMIN_REPORTS + "?status=WHATEVER"), 400, "VALIDATION_FAILED");
  }
}
