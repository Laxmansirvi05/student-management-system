package com.campuscompanion.academics;

import static com.campuscompanion.support.ApiTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.support.ApiTestClient;
import com.campuscompanion.support.TestAccounts;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Tasks T2-03 and T2-04 over real HTTP: subjects and units, and the admin's student accounts
 * (FR-ADM-001, FR-ADM-005). Covers scenarios AU14, AU15, AU17, PV6, Z7 and Z16. All data is
 * synthetic.
 */
class SubjectsAndStudentsAdminIT extends AcademicApiTest {

  private ApiTestClient admin;

  @BeforeEach
  void logIn() throws Exception {
    admin = adminClient("sub.admin@university.example");
  }

  @Test
  void anAdminManagesSubjectsAndUnits() throws Exception {
    long id = subject(admin, "sub101", "sub", 3);
    HttpResponse<String> list = admin.get("/api/v1/admin/subjects?branch=SUB");
    assertThat(texts(json(list), "code")).as("codes are stored in upper case").contains("SUB101");

    assertProblem(
        admin.post("/api/v1/admin/subjects", subjectJson("SUB101", "SUB", 4, true)),
        409,
        "CONFLICT");
    assertThat(
            invalidFields(
                admin.post("/api/v1/admin/subjects", subjectJson("SUB102", "SUB", 11, true))))
        .containsExactly("credits");

    HttpResponse<String> renamed =
        admin.put("/api/v1/admin/subjects/" + id, subjectJson("SUB101", "SUB", 3, false));
    assertThat(renamed.statusCode()).isEqualTo(200);
    assertThat(json(renamed).get("active").asBoolean()).isFalse();
    assertThat(json(renamed).get("credits").asInt()).isEqualTo(3);
    assertProblem(
        admin.put("/api/v1/admin/subjects/999999", subjectJson("SUB999", "SUB", 3, true)),
        404,
        "NOT_FOUND");

    HttpResponse<String> unit =
        admin.post(
            "/api/v1/admin/subjects/" + id + "/units", "{\"unitNo\":1,\"title\":\"First unit\"}");
    assertThat(unit.statusCode()).isEqualTo(201);
    long unitId = json(unit).get("id").asLong();
    assertProblem(
        admin.post("/api/v1/admin/subjects/" + id + "/units", "{\"unitNo\":1,\"title\":\"Again\"}"),
        409,
        "CONFLICT");
    assertThat(
            invalidFields(
                admin.post(
                    "/api/v1/admin/subjects/" + id + "/units", "{\"unitNo\":21,\"title\":\"\"}")))
        .containsExactlyInAnyOrder("unitNo", "title");
    assertThat(
            json(admin.put("/api/v1/admin/units/" + unitId, "{\"unitNo\":2,\"title\":\"Renamed\"}"))
                .get("title")
                .asString())
        .isEqualTo("Renamed");
    assertThat(admin.delete("/api/v1/admin/units/" + unitId).statusCode()).isEqualTo(204);
    assertProblem(admin.delete("/api/v1/admin/units/" + unitId), 404, "NOT_FOUND");
  }

  @Test
  void aStudentSeesOnlyTheActiveSubjectsOfTheirOwnBranch() throws Exception {
    long visible = subject(admin, "SUB201", "SUBB", 3);
    long hidden = subject(admin, "SUB202", "SUBB", 3);
    admin.put("/api/v1/admin/subjects/" + hidden, subjectJson("SUB202", "SUBB", 4, false));
    subject(admin, "OTH201", "SUBO", 3);
    admin.post(
        "/api/v1/admin/subjects/" + visible + "/units", "{\"unitNo\":1,\"title\":\"Unit one\"}");
    accounts.student("SUBB23001", "SUBB", 3, "A");
    ApiTestClient student = studentClient("SUBB23001");

    assertThat(texts(json(student.get("/api/v1/subjects")), "code")).containsExactly("SUB201");
    assertThat(json(student.get("/api/v1/subjects?semester=5")).size()).isZero();
    assertThat(texts(json(student.get("/api/v1/subjects/" + visible + "/units")), "title"))
        .containsExactly("Unit one");
    assertProblem(student.get("/api/v1/subjects/" + hidden + "/units"), 404, "NOT_FOUND");
    assertProblem(student.get("/api/v1/subjects?semester=9"), 400, "VALIDATION_FAILED");

    assertProblem(visitor().get("/api/v1/subjects"), 401, "AUTH_REQUIRED");
    assertProblem(student.get("/api/v1/admin/subjects"), 403, "ACCESS_DENIED");
    assertProblem(
        student.post("/api/v1/admin/subjects", subjectJson("SUB999", "SUBB", 4, true)),
        403,
        "ACCESS_DENIED");
    assertThat(texts(json(admin.get("/api/v1/subjects")), "code"))
        .as("an admin sees the active subjects of every branch")
        .contains("SUB201", "OTH201")
        .doesNotContain("SUB202");
  }

  @Test
  void anAdminProvisionsSearchesAndEditsStudents() throws Exception {
    HttpResponse<String> created = admin.post("/api/v1/admin/students", studentJson("adm23cse001"));
    assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
    JsonNode student = json(created);
    long id = student.get("id").asLong();
    assertThat(student.get("rollNo").asString()).isEqualTo("ADM23CSE001");
    assertThat(student.get("email").asString()).isEqualTo("adm23cse001@university.example");
    assertThat(student.get("mustChangePassword").asBoolean()).isTrue();
    assertThat(student.get("lastLoginAt").isNull()).as("awaiting first login").isTrue();
    assertThat(student.has("password") || student.has("temporaryPassword")).isFalse();
    assertThat(auditRows("ACCOUNT_CREATED", "USER", userIdOf("ADM23CSE001"))).isEqualTo(1);
    assertThat(latestAudit("ACCOUNT_CREATED")).contains("STUDENT").doesNotContain("ADM23CSE001\"");

    assertProblem(
        admin.post("/api/v1/admin/students", studentJson("ADM23CSE001")), 409, "CONFLICT");
    assertThat(invalidFields(admin.post("/api/v1/admin/students", studentJson("ADM 1"))))
        .containsExactly("rollNo");
    assertProblem(
        admin.post(
            "/api/v1/admin/students",
            studentJson("ADM23CSE002").replace("}", ",\"password\":\"chosen-by-admin\"}")),
        400,
        "VALIDATION_FAILED");

    assertThat(json(admin.get("/api/v1/admin/students?q=adm23cse001")).get("totalElements").asInt())
        .isEqualTo(1);
    assertThat(
            texts(
                json(admin.get("/api/v1/admin/students?branch=ADM&semester=3&section=A"))
                    .get("content"),
                "rollNo"))
        .contains("ADM23CSE001");
    assertThat(
            json(admin.get("/api/v1/admin/students?q=Demo%20Admin")).get("totalElements").asInt())
        .isPositive();
    assertProblem(admin.get("/api/v1/admin/students?q=a"), 400, "VALIDATION_FAILED");
    assertProblem(admin.get("/api/v1/admin/students?size=101"), 400, "VALIDATION_FAILED");

    HttpResponse<String> edited =
        admin.put("/api/v1/admin/students/" + id, updateJson("B", "\"0000000000\""));
    assertThat(edited.statusCode()).as(edited.body()).isEqualTo(200);
    assertThat(json(edited).get("section").asString()).isEqualTo("B");
    assertThat(json(edited).get("rollNo").asString()).isEqualTo("ADM23CSE001");
    assertThat(latestAudit("PROFILE_UPDATED"))
        .contains("\"section\": \"A\"")
        .contains("\"section\": \"B\"");
    int entries = auditRows("PROFILE_UPDATED", "STUDENT_PROFILE", id);
    admin.put("/api/v1/admin/students/" + id, updateJson("B", "\"0000000000\""));
    assertThat(auditRows("PROFILE_UPDATED", "STUDENT_PROFILE", id))
        .as("saving the same values again writes no entry")
        .isEqualTo(entries);

    for (String forbidden :
        new String[] {"\"role\":\"ADMIN\"", "\"rollNo\":\"ADM23CSE999\"", "\"email\":\"x@y.z\""}) {
      assertProblem(
          admin.put(
              "/api/v1/admin/students/" + id,
              updateJson("B", "null").replace("}", "," + forbidden + "}")),
          400,
          "VALIDATION_FAILED");
    }
    assertThat(json(admin.get("/api/v1/admin/students/" + id)).get("phone").asString())
        .isEqualTo("0000000000");
    assertProblem(admin.get("/api/v1/admin/students/999999"), 404, "NOT_FOUND");
  }

  @Test
  void deactivationEndsSessionsAndReactivationRestoresThePassword() throws Exception {
    long id = accounts.student("ADM23CSE010", "ADM", 3, "A");
    ApiTestClient student = studentClient("ADM23CSE010");
    assertThat(student.get("/api/v1/auth/me").statusCode()).isEqualTo(200);

    assertThat(admin.post("/api/v1/admin/students/" + id + "/deactivate", "").statusCode())
        .isEqualTo(204);

    assertProblem(student.get("/api/v1/auth/me"), 401, "AUTH_REQUIRED");
    assertProblem(
        visitor().login(TestAccounts.emailOf("ADM23CSE010"), TestAccounts.PASSWORD),
        401,
        "AUTH_INVALID_CREDENTIALS");
    assertThat(json(admin.get("/api/v1/admin/students/" + id)).get("active").asBoolean()).isFalse();

    assertThat(admin.post("/api/v1/admin/students/" + id + "/activate", "").statusCode())
        .isEqualTo(204);
    assertThat(studentClient("ADM23CSE010").get("/api/v1/auth/me").statusCode())
        .as("the password it had still works")
        .isEqualTo(200);
    long userId = userIdOf("ADM23CSE010");
    assertThat(auditRows("ACCOUNT_DEACTIVATED", "USER", userId)).isEqualTo(1);
    assertThat(auditRows("ACCOUNT_ACTIVATED", "USER", userId)).isEqualTo(1);
  }

  @Test
  void aResetIssuesARandomTemporaryPasswordAndEndsEverySession() throws Exception {
    long id = accounts.student("ADM23CSE020", "ADM", 3, "A");
    String email = TestAccounts.emailOf("ADM23CSE020");
    ApiTestClient first = studentClient("ADM23CSE020");
    ApiTestClient second = studentClient("ADM23CSE020");

    HttpResponse<String> reset = admin.post("/api/v1/admin/students/" + id + "/reset-password", "");

    assertThat(reset.statusCode()).isEqualTo(200);
    String temporary = json(reset).get("temporaryPassword").asString();
    assertThat(temporary).hasSizeGreaterThanOrEqualTo(12).doesNotContainIgnoringCase("ADM23CSE020");
    assertProblem(first.get("/api/v1/auth/me"), 401, "AUTH_REQUIRED");
    assertProblem(second.get("/api/v1/auth/me"), 401, "AUTH_REQUIRED");
    assertProblem(visitor().login(email, TestAccounts.PASSWORD), 401, "AUTH_INVALID_CREDENTIALS");
    assertProblem(visitor().login(email, "ADM23CSE020"), 401, "AUTH_INVALID_CREDENTIALS");

    HttpResponse<String> login = visitor().login(email, temporary);
    assertThat(login.statusCode()).isEqualTo(200);
    assertThat(json(login).get("mustChangePassword").asBoolean()).isTrue();

    String auditRow =
        jdbc.queryForObject(
            "select row_to_json(a)::text from app.audit_logs a where action = 'PASSWORD_RESET'"
                + " and entity_id = ? order by id desc limit 1",
            String.class,
            String.valueOf(userIdOf("ADM23CSE020")));
    assertThat(auditRow)
        .contains("\"old_value\":null")
        .contains("\"new_value\":null")
        .doesNotContain(temporary);
    assertThat(json(reset).size())
        .as("the response holds only the temporary password")
        .isEqualTo(1);
  }

  @Test
  void studentsAndVisitorsCannotUseTheAdminStudentEndpoints() throws Exception {
    long id = accounts.student("ADM23CSE030", "ADM", 3, "A");
    ApiTestClient student = studentClient("ADM23CSE030");

    assertProblem(student.get("/api/v1/admin/students"), 403, "ACCESS_DENIED");
    assertProblem(
        student.post("/api/v1/admin/students", studentJson("ADM23CSE031")), 403, "ACCESS_DENIED");
    assertProblem(
        student.put("/api/v1/admin/students/" + id, updateJson("B", "null")), 403, "ACCESS_DENIED");
    assertProblem(
        student.post("/api/v1/admin/students/" + id + "/reset-password", ""), 403, "ACCESS_DENIED");
    assertProblem(
        student.post("/api/v1/admin/students/" + id + "/deactivate", ""), 403, "ACCESS_DENIED");
    assertProblem(visitor().get("/api/v1/admin/students"), 401, "AUTH_REQUIRED");
    assertThat(json(admin.get("/api/v1/admin/students/" + id)).get("section").asString())
        .as("nothing changed")
        .isEqualTo("A");
  }

  private long userIdOf(String rollNo) {
    return jdbc.queryForObject(
        "select user_id from app.student_profiles where roll_no = ?", Long.class, rollNo);
  }

  private static String subjectJson(String code, String branch, int credits, boolean active) {
    return """
        {"code":"%s","name":"Subject %s","branch":"%s","semester":3,"credits":%d,"active":%s}
        """
        .formatted(code, code, branch, credits, active);
  }

  private static String studentJson(String rollNo) {
    return """
        {"rollNo":"%s","fullName":"Demo Admin Made","branch":"ADM","currentSemester":3,"section":"A","admissionYear":2023,"phone":null}"""
        .formatted(rollNo);
  }

  private static String updateJson(String section, String phone) {
    return """
        {"fullName":"Demo Admin Made","branch":"ADM","currentSemester":3,"section":"%s","admissionYear":2023,"phone":%s}"""
        .formatted(section, phone);
  }
}
