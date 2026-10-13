package com.campuscompanion.support;

import static com.campuscompanion.support.ApiTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.common.config.CampusTime;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * What the API tests share: logged-in clients, short ways to create subjects and read the audit
 * log, and the checks every endpoint needs (visitor 401, wrong role 403).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class ApiIntegrationTest extends PostgresIntegrationTest {

  @Value("${local.server.port}")
  protected int port;

  @Autowired protected TestAccounts accounts;
  @Autowired protected JdbcTemplate jdbc;

  protected ApiTestClient adminClient(String email) throws Exception {
    accounts.admin(email);
    return accounts.login(port, email);
  }

  protected ApiTestClient studentClient(String rollNo) throws Exception {
    return accounts.login(port, TestAccounts.emailOf(rollNo));
  }

  protected ApiTestClient visitor() {
    return new ApiTestClient(port);
  }

  protected static LocalDate today() {
    return LocalDate.now(CampusTime.ZONE);
  }

  /** Creates a subject through the API and returns its ID. */
  protected long subject(ApiTestClient admin, String code, String branch, int semester)
      throws Exception {
    HttpResponse<String> response =
        admin.post(
            "/api/v1/admin/subjects",
            """
            {"code":"%s","name":"Subject %s","branch":"%s","semester":%d,"credits":4,"active":true}
            """
                .formatted(code, code, branch, semester));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    return json(response).get("id").asLong();
  }

  protected static void assertProblem(HttpResponse<String> response, int status, String code) {
    assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
    assertThat(json(response).get("code").asString()).isEqualTo(code);
  }

  /** The names of the invalid fields in a 400 response. */
  protected static List<String> invalidFields(HttpResponse<String> response) {
    assertProblem(response, 400, "VALIDATION_FAILED");
    List<String> fields = new ArrayList<>();
    for (JsonNode error : json(response).get("errors")) {
      fields.add(error.get("field").asString());
    }
    return fields;
  }

  /** The values of one text field from every item of a JSON array. */
  protected static List<String> texts(JsonNode array, String field) {
    List<String> values = new ArrayList<>();
    for (JsonNode item : array) {
      values.add(item.get(field).asString());
    }
    return values;
  }

  protected int auditRows(String action, String entityType, long entityId) {
    return jdbc.queryForObject(
        "select count(*) from app.audit_logs where action = ? and entity_type = ? and entity_id = ?",
        Integer.class,
        action,
        entityType,
        String.valueOf(entityId));
  }

  /** The newest audit row for an action, as "old_value | new_value" text. */
  protected String latestAudit(String action) {
    return jdbc.queryForObject(
        "select coalesce(old_value::text, 'null') || ' | ' || coalesce(new_value::text, 'null')"
            + " from app.audit_logs where action = ? order by id desc limit 1",
        String.class,
        action);
  }
}
