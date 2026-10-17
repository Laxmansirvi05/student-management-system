package com.campuscompanion.attendance;

import static com.campuscompanion.support.ApiTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.support.ApiIntegrationTest;
import com.campuscompanion.support.ApiTestClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * The college's attendance totals: imported by an admin from a CSV file, shown to each student as
 * their own report. All data is synthetic.
 */
class AttendanceReportIT extends ApiIntegrationTest {

  private static final String IMPORT = "/api/v1/admin/imports/attendance-reports";
  private static final String PERIOD = "?fromDate=2026-07-27&toDate=2026-09-30";

  private ApiTestClient admin() throws Exception {
    return adminClient("report-admin@university.example");
  }

  private static HttpResponse<String> send(ApiTestClient client, String query, String csv)
      throws Exception {
    return client.postFile(
        IMPORT + query, "attendance.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void aStudentSeesTheirOwnReportAndItBecomesTheOverallFigure() throws Exception {
    accounts.student("23REP001", "REP", 3, "E");
    accounts.student("23REP002", "REP", 3, "E");
    String csv =
        "rollNo,fullName,classesHeld,classesAttended,percent\n"
            + "23rep001,Demo One,211,163,77.25\n"
            + "23REP002,Demo Two,211,87,41.23\n";

    HttpResponse<String> response = send(admin(), PERIOD, csv);

    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    assertThat(json(response).get("created").asInt()).isEqualTo(2);
    JsonNode mine = json(studentClient("23REP002").get("/api/v1/attendance/me"));
    JsonNode report = mine.get("report");
    assertThat(report.get("fromDate").asString()).isEqualTo("2026-07-27");
    assertThat(report.get("classesHeld").asLong()).isEqualTo(211);
    assertThat(report.get("classesAttended").asLong()).isEqualTo(87);
    assertThat(report.get("percent").decimalValue()).isEqualByComparingTo("41.23");
    assertThat(report.get("belowRequired").asBoolean()).isTrue();
    assertThat(mine.get("overallPercent").decimalValue()).isEqualByComparingTo("41.23");
    JsonNode dashboard = json(studentClient("23REP002").get("/api/v1/students/me/dashboard"));
    assertThat(dashboard.toString()).contains("41.23");
  }

  @Test
  void importingTheSamePeriodAgainCorrectsAndAudits() throws Exception {
    accounts.student("23REP010", "REP", 3, "E");
    ApiTestClient admin = admin();
    send(admin, PERIOD, "rollNo,classesHeld,classesAttended\n23REP010,211,150\n");

    HttpResponse<String> again =
        send(admin, PERIOD, "rollNo,classesHeld,classesAttended\n23REP010,211,152\n");
    HttpResponse<String> same =
        send(admin, PERIOD, "rollNo,classesHeld,classesAttended\n23REP010,211,152\n");

    assertThat(json(again).get("corrected").asInt()).isEqualTo(1);
    assertThat(json(same).get("unchanged").asInt()).isEqualTo(1);
    assertThat(latestAudit("ATTENDANCE_CORRECTED"))
        .contains("\"classesAttended\": 150")
        .contains("\"classesAttended\": 152");
    assertThat(
            json(studentClient("23REP010").get("/api/v1/attendance/me"))
                .get("report")
                .get("classesAttended")
                .asLong())
        .isEqualTo(152);
  }

  @Test
  void listsEveryProblemAndSavesNothing() throws Exception {
    accounts.student("23REP020", "REP", 3, "E");
    accounts.student("23REP021", "REP", 3, "E");
    String csv =
        "rollNo,classesHeld,classesAttended,percent\n"
            + "23REP020,211,163,77.25\n" // line 2: fine, but must not be saved
            + "23REP999,211,100,\n" // line 3: no such student
            + "23REP021,211,163,71.25\n" // line 4: percent disagrees
            + "23REP020,211,10,\n" // line 5: repeated roll number
            + ",211,10,\n" // line 6: no roll number
            + "23REP021,0,0,\n"; // line 7: held must be at least 1 (and repeated)

    HttpResponse<String> response = send(admin(), PERIOD, csv);

    assertThat(invalidFields(response))
        .contains(
            "rows[3].rollNo",
            "rows[4].percent",
            "rows[5].rollNo",
            "rows[6].rollNo",
            "rows[7].classesHeld")
        .doesNotContain("rows[2].rollNo");
    assertThat(json(studentClient("23REP020").get("/api/v1/attendance/me")).get("report").isNull())
        .isTrue();
  }

  @Test
  void checksThePeriodAndTheFile() throws Exception {
    ApiTestClient admin = admin();
    String csv = "rollNo,classesHeld,classesAttended\n23REP030,10,5\n";

    assertThat(invalidFields(send(admin, "", csv))).contains("fromDate", "toDate");
    assertThat(invalidFields(send(admin, "?fromDate=2026-09-30&toDate=2026-07-27", csv)))
        .containsExactly("toDate");
    assertThat(invalidFields(send(admin, "?fromDate=2026-07-27&toDate=2999-01-01", csv)))
        .containsExactly("toDate");
    assertThat(invalidFields(send(admin, PERIOD, "roll,held\n1,2\n"))).containsOnly("header");
    assertProblem(
        admin.postFile(IMPORT + PERIOD, "a.pdf", "text/csv", csv.getBytes(StandardCharsets.UTF_8)),
        415,
        "UPLOAD_INVALID_TYPE");
  }

  @Test
  void onlyAnAdminMayImport() throws Exception {
    accounts.student("23REP040", "REP", 3, "E");
    String csv = "rollNo,classesHeld,classesAttended\n23REP040,10,5\n";

    assertProblem(send(studentClient("23REP040"), PERIOD, csv), 403, "ACCESS_DENIED");
    assertProblem(send(visitor(), PERIOD, csv), 401, "AUTH_REQUIRED");
  }
}
