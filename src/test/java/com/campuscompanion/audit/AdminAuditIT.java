package com.campuscompanion.audit;

import static com.campuscompanion.support.ApiTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.support.ApiIntegrationTest;
import com.campuscompanion.support.ApiTestClient;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** The admin audit log viewer (FR-ADM-009, P1): read only, filtered, and free of passwords. */
class AdminAuditIT extends ApiIntegrationTest {

  private static final String LOGS = "/api/v1/admin/audit-logs";

  private static String student(String rollNo) {
    return """
        {"rollNo":"%s","fullName":"Demo Audit","branch":"AUD","currentSemester":3,"section":"A","admissionYear":2023}
        """
        .formatted(rollNo);
  }

  @Test
  void showsWhoDidWhatToWhomNewestFirst() throws Exception {
    ApiTestClient admin = adminClient("audit-admin@university.example");
    long id = json(admin.post("/api/v1/admin/students", student("23AUD001"))).get("id").asLong();
    assertThat(admin.post("/api/v1/admin/students/" + id + "/deactivate", "").statusCode())
        .isEqualTo(204);

    JsonNode page = json(admin.get(LOGS + "?size=5"));

    JsonNode newest = page.get("content").get(0);
    assertThat(newest.get("action").asString()).isEqualTo("ACCOUNT_DEACTIVATED");
    assertThat(newest.get("actor").asString()).isEqualTo("audit-admin@university.example");
    assertThat(newest.get("subject").asString()).isEqualTo("23aud001@university.example");
    assertThat(newest.get("newValue").get("active").asBoolean()).isFalse();
    assertThat(page.get("totalElements").asInt()).isGreaterThanOrEqualTo(2);
  }

  @Test
  void filtersByActionAndNeverShowsPasswordMaterial() throws Exception {
    ApiTestClient admin = adminClient("audit-admin2@university.example");
    long id = json(admin.post("/api/v1/admin/students", student("23AUD002"))).get("id").asLong();
    HttpResponse<String> reset = admin.post("/api/v1/admin/students/" + id + "/reset-password", "");
    String temporaryPassword = json(reset).get("temporaryPassword").asString();

    HttpResponse<String> response = admin.get(LOGS + "?action=PASSWORD_RESET&size=100");

    assertThat(response.statusCode()).isEqualTo(200);
    for (JsonNode entry : json(response).get("content")) {
      assertThat(entry.get("action").asString()).isEqualTo("PASSWORD_RESET");
      assertThat(entry.get("oldValue").isNull()).isTrue();
      assertThat(entry.get("newValue").isNull()).isTrue();
    }
    assertThat(response.body()).doesNotContain(temporaryPassword).doesNotContain("23AUD002");
    assertThat(response.body().toLowerCase()).doesNotContain("hash");
  }

  @Test
  void listsTheActionsForTheFilter() throws Exception {
    ApiTestClient admin = adminClient("audit-admin3@university.example");

    JsonNode actions = json(admin.get(LOGS + "/actions"));

    assertThat(texts(actions)).contains("MARKS_UPDATED", "PASSWORD_RESET", "ACCOUNT_CREATED");
  }

  @Test
  void rejectsBadPaging() throws Exception {
    ApiTestClient admin = adminClient("audit-admin4@university.example");

    assertThat(admin.get(LOGS + "?size=0").statusCode()).isEqualTo(400);
    assertThat(admin.get(LOGS + "?size=101").statusCode()).isEqualTo(400);
  }

  @Test
  void onlyAnAdminMayRead() throws Exception {
    accounts.student("23AUD900", "AUD", 3, "A");

    assertProblem(studentClient("23AUD900").get(LOGS), 403, "ACCESS_DENIED");
    assertProblem(visitor().get(LOGS), 401, "AUTH_REQUIRED");
  }

  @Test
  void nothingCanChangeOrDeleteAnEntry() throws Exception {
    ApiTestClient admin = adminClient("audit-admin5@university.example");

    assertThat(admin.delete(LOGS + "/1").statusCode()).isIn(404, 405);
    assertThat(admin.put(LOGS + "/1", "{}").statusCode()).isIn(404, 405);
    assertThat(admin.post(LOGS, "{}").statusCode()).isIn(404, 405);
  }

  private static java.util.List<String> texts(JsonNode array) {
    java.util.List<String> values = new java.util.ArrayList<>();
    array.forEach(item -> values.add(item.asString()));
    return values;
  }
}
