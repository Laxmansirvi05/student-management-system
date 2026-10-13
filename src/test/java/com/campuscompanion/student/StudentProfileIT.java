package com.campuscompanion.student;

import static com.campuscompanion.support.ApiTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.auth.Role;
import com.campuscompanion.auth.User;
import com.campuscompanion.auth.UserRepository;
import com.campuscompanion.student.dto.StudentCreateRequest;
import com.campuscompanion.support.ApiTestClient;
import com.campuscompanion.support.PostgresIntegrationTest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;

/**
 * Task T2-13: the student's own profile over real HTTP (FR-STU-002, AT-STU-002). Covers success, a
 * visitor (401), the wrong role (403), scenario Z5 (a student identifier sent by the browser
 * changes nothing) and that a student cannot change the profile. All data is synthetic.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StudentProfileIT extends PostgresIntegrationTest {

  private static final String FIRST = "prf23cse001@university.example";
  private static final String SECOND = "prf23cse002@university.example";
  private static final String ADMIN = "prf.admin@university.example";
  private static final String PASSWORD = "a-chosen-password";

  @Value("${local.server.port}")
  private int port;

  @Autowired private StudentProvisioningService provisioning;
  @Autowired private UserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    if (!users.existsByLoginEmail(FIRST)) {
      provisioning.provision(
          new StudentCreateRequest("PRF23CSE001", "Demo Student One", "CSE", 3, "A", 2023, null));
      provisioning.provision(
          new StudentCreateRequest(
              "PRF23CSE002", "Demo Student Two", "ECE", 5, "B", 2022, "0000000000"));
      users.save(new User(ADMIN, passwordEncoder.encode(PASSWORD), Role.ADMIN, "Profile Admin"));
      // These accounts have already chosen their own password.
      jdbcTemplate.update(
          "update app.users set must_change_password = false, password_hash = ?"
              + " where login_email in (?, ?, ?)",
          passwordEncoder.encode(PASSWORD),
          FIRST,
          SECOND,
          ADMIN);
    }
  }

  @Test
  void aStudentSeesTheirOwnProfile() throws Exception {
    HttpResponse<String> response = loggedIn(FIRST).get("/api/v1/students/me");

    assertThat(response.statusCode()).isEqualTo(200);
    JsonNode profile = json(response);
    assertThat(profile.get("rollNo").asString()).isEqualTo("PRF23CSE001");
    assertThat(profile.get("email").asString()).isEqualTo(FIRST);
    assertThat(profile.get("fullName").asString()).isEqualTo("Demo Student One");
    assertThat(profile.get("branch").asString()).isEqualTo("CSE");
    assertThat(profile.get("currentSemester").asInt()).isEqualTo(3);
    assertThat(profile.get("section").asString()).isEqualTo("A");
    assertThat(profile.get("admissionYear").asInt()).isEqualTo(2023);
    assertThat(profile.get("phone").isNull()).isTrue();
  }

  @Test
  void theResponseHoldsOnlyTheProfileFields() throws Exception {
    JsonNode profile = json(loggedIn(SECOND).get("/api/v1/students/me"));

    List<String> fields = new ArrayList<>(profile.propertyNames());
    assertThat(fields)
        .containsExactlyInAnyOrder(
            "rollNo",
            "email",
            "fullName",
            "branch",
            "currentSemester",
            "section",
            "admissionYear",
            "phone");
    assertThat(profile.get("phone").asString()).isEqualTo("0000000000");
  }

  @Test
  void aVisitorIsRefused() throws Exception {
    HttpResponse<String> response = new ApiTestClient(port).get("/api/v1/students/me");

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(json(response).get("code").asString()).isEqualTo("AUTH_REQUIRED");
  }

  @Test
  void anAdminIsRefused() throws Exception {
    HttpResponse<String> response = loggedIn(ADMIN).get("/api/v1/students/me");

    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(json(response).get("code").asString()).isEqualTo("ACCESS_DENIED");
  }

  @Test
  void aStudentIdentifierSentByTheBrowserChangesNothing() throws Exception {
    ApiTestClient first = loggedIn(FIRST);
    Long otherStudentId =
        jdbcTemplate.queryForObject(
            "select id from app.student_profiles where roll_no = 'PRF23CSE002'", Long.class);

    for (String path :
        new String[] {
          "/api/v1/students/me?studentId=" + otherStudentId,
          "/api/v1/students/me?rollNo=PRF23CSE002",
          "/api/v1/students/me?userId=" + otherStudentId + "&email=" + SECOND
        }) {
      HttpResponse<String> response = first.get(path);
      assertThat(response.statusCode()).as(path).isEqualTo(200);
      assertThat(json(response).get("rollNo").asString()).as(path).isEqualTo("PRF23CSE001");
    }
    for (String path :
        new String[] {"/api/v1/students/" + otherStudentId, "/api/v1/students/PRF23CSE002"}) {
      HttpResponse<String> response = first.get(path);
      assertThat(response.statusCode()).as(path).isEqualTo(404);
      assertThat(response.body()).as(path).doesNotContain("Demo Student Two");
    }
  }

  @Test
  void aStudentCannotChangeTheProfile() throws Exception {
    ApiTestClient first = loggedIn(FIRST);

    HttpResponse<String> response =
        first.post("/api/v1/students/me", "{\"fullName\":\"Changed Name\",\"branch\":\"ECE\"}");

    assertThat(response.statusCode()).isBetween(400, 499);
    JsonNode profile = json(first.get("/api/v1/students/me"));
    assertThat(profile.get("fullName").asString()).isEqualTo("Demo Student One");
    assertThat(profile.get("branch").asString()).isEqualTo("CSE");
  }

  @Test
  void theProfilePageIsServedToAStudentOnly() throws Exception {
    HttpResponse<String> page = loggedIn(FIRST).get("/student/profile.html");
    assertThat(page.statusCode()).isEqualTo(200);
    assertThat(page.body()).contains("Roll number").contains("Institutional email");

    assertThat(new ApiTestClient(port).get("/student/profile.html").statusCode()).isEqualTo(302);
    assertThat(loggedIn(ADMIN).get("/student/profile.html").statusCode()).isEqualTo(302);
  }

  private ApiTestClient loggedIn(String email) throws Exception {
    ApiTestClient client = new ApiTestClient(port);
    assertThat(client.login(email, PASSWORD).statusCode()).isEqualTo(200);
    return client;
  }
}
