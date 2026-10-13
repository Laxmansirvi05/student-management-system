package com.campuscompanion.auth;

import static com.campuscompanion.support.ApiTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.student.StudentProfile;
import com.campuscompanion.student.StudentProfileRepository;
import com.campuscompanion.support.ApiTestClient;
import com.campuscompanion.support.PostgresIntegrationTest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;

/**
 * Task T1-08: the forced first-time password change over real HTTP. Covers scenarios AU5 (nothing
 * else works until the password is changed), AU6 (a successful change and its effect on sessions),
 * AU9 (the old password stops working) and AU10 (passwords that must be refused).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PasswordChangeIT extends PostgresIntegrationTest {

  private static final String NEW_PASSWORD = "blue-Lamp-42";

  @Value("${local.server.port}")
  private int port;

  @Autowired private UserRepository users;
  @Autowired private StudentProfileRepository profiles;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void aVisitorCannotChangeAPassword() throws Exception {
    HttpResponse<String> response =
        new ApiTestClient(port)
            .post("/api/v1/auth/change-password", change("x", NEW_PASSWORD, NEW_PASSWORD));

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(json(response).get("code").asString()).isEqualTo("AUTH_REQUIRED");
  }

  @Test
  void untilThePasswordIsChangedOnlyTheFourNeededRequestsWork() throws Exception {
    String rollNo = "PWD23CSE001";
    ApiTestClient client = loggedInNewStudent(rollNo);

    assertThat(client.get("/api/v1/auth/csrf").statusCode()).isEqualTo(200);
    assertThat(client.get("/api/v1/auth/me").statusCode()).isEqualTo(200);

    for (String path :
        List.of(
            "/api/v1/students/me",
            "/api/v1/attendance/me",
            "/api/v1/resources",
            "/api/v1/admin/students",
            "/api/v1/does-not-exist")) {
      HttpResponse<String> response = client.get(path);
      assertThat(response.statusCode()).as(path).isEqualTo(403);
      assertThat(json(response).get("code").asString())
          .as(path)
          .isEqualTo("AUTH_PASSWORD_CHANGE_REQUIRED");
    }
    HttpResponse<String> write = client.post("/api/v1/quiz-attempts", "{}");
    assertThat(json(write).get("code").asString()).isEqualTo("AUTH_PASSWORD_CHANGE_REQUIRED");

    HttpResponse<String> page = client.get("/student/dashboard.html");
    assertThat(page.statusCode()).isEqualTo(302);
    assertThat(page.headers().firstValue("Location").orElse("")).endsWith("/change-password.html");

    assertThat(client.post("/api/v1/auth/logout", "").statusCode()).isEqualTo(204);
  }

  @Test
  void aSuccessfulChangeClearsTheFlagRenewsTheSessionAndEndsTheOtherSessions() throws Exception {
    String rollNo = "PWD23CSE002";
    ApiTestClient first = loggedInNewStudent(rollNo);
    ApiTestClient second = new ApiTestClient(port);
    assertThat(second.login(emailOf(rollNo), rollNo).statusCode()).isEqualTo(200);
    String sessionBefore = first.cookie("JSESSIONID");

    HttpResponse<String> response =
        first.post("/api/v1/auth/change-password", change(rollNo, NEW_PASSWORD, NEW_PASSWORD));

    assertThat(response.statusCode()).isEqualTo(204);
    assertThat(first.cookie("JSESSIONID")).isNotBlank().isNotEqualTo(sessionBefore);
    JsonNode me = json(first.get("/api/v1/auth/me"));
    assertThat(me.get("mustChangePassword").asBoolean()).isFalse();
    // No longer restricted: a path that does not exist is now simply not found.
    assertThat(first.get("/api/v1/does-not-exist").statusCode()).isEqualTo(404);
    // The other session was ended on the server.
    HttpResponse<String> other = second.get("/api/v1/auth/me");
    assertThat(other.statusCode()).isEqualTo(401);
    assertThat(json(other).get("code").asString()).isEqualTo("AUTH_REQUIRED");
    // Only a hash is stored, and it is not the hash of the roll number any more.
    String hash =
        jdbcTemplate.queryForObject(
            "select password_hash from app.users where login_email = ?",
            String.class,
            emailOf(rollNo));
    assertThat(hash).startsWith("$2").doesNotContain(NEW_PASSWORD);
    assertThat(passwordEncoder.matches(NEW_PASSWORD, hash)).isTrue();
    assertThat(passwordEncoder.matches(rollNo, hash)).isFalse();
  }

  @Test
  void afterTheChangeTheRollNumberNeverWorksAgain() throws Exception {
    String rollNo = "PWD23CSE003";
    ApiTestClient client = loggedInNewStudent(rollNo);
    assertThat(
            client
                .post("/api/v1/auth/change-password", change(rollNo, NEW_PASSWORD, NEW_PASSWORD))
                .statusCode())
        .isEqualTo(204);

    ApiTestClient fresh = new ApiTestClient(port);
    HttpResponse<String> oldPassword = fresh.login(emailOf(rollNo), rollNo);
    assertThat(oldPassword.statusCode()).isEqualTo(401);
    assertThat(json(oldPassword).get("code").asString()).isEqualTo("AUTH_INVALID_CREDENTIALS");
    HttpResponse<String> newPassword = fresh.login(emailOf(rollNo), NEW_PASSWORD);
    assertThat(newPassword.statusCode()).isEqualTo(200);
    assertThat(json(newPassword).get("mustChangePassword").asBoolean()).isFalse();

    HttpResponse<String> changeBack =
        fresh.post("/api/v1/auth/change-password", change(NEW_PASSWORD, rollNo, rollNo));
    assertThat(changeBack.statusCode()).isEqualTo(400);
    assertThat(fieldsIn(changeBack)).containsOnly("newPassword");
    assertThat(changeBack.body()).contains("Your password cannot be your roll number.");
  }

  @Test
  void unacceptableNewPasswordsAreRefusedAndNothingChanges() throws Exception {
    String rollNo = "PWD23CSE004";
    String email = emailOf(rollNo);
    ApiTestClient client = loggedInNewStudent(rollNo);

    assertRefused(client, change(rollNo, "short7!", "short7!"), "newPassword");
    assertRefused(client, change(rollNo, "a".repeat(73), "a".repeat(73)), "newPassword");
    assertRefused(client, change(rollNo, "pwd23Cse004", "pwd23Cse004"), "newPassword");
    assertRefused(
        client,
        change(rollNo, email.toUpperCase(Locale.ROOT), email.toUpperCase(Locale.ROOT)),
        "newPassword");
    assertRefused(client, change(rollNo, rollNo, rollNo), "newPassword");
    assertRefused(client, change(rollNo, NEW_PASSWORD, "blue-Lamp-43"), "confirmNewPassword");
    assertRefused(client, change(rollNo, "", ""), "newPassword");
    assertRefused(
        client, change("not-the-current-one", NEW_PASSWORD, NEW_PASSWORD), "currentPassword");

    assertThat(json(client.get("/api/v1/auth/me")).get("mustChangePassword").asBoolean()).isTrue();
    assertThat(new ApiTestClient(port).login(email, rollNo).statusCode()).isEqualTo(200);
  }

  @Test
  void noPasswordOrHashAppearsInAnyResponse() throws Exception {
    String rollNo = "PWD23CSE005";
    ApiTestClient client = loggedInNewStudent(rollNo);

    HttpResponse<String> refused =
        client.post(
            "/api/v1/auth/change-password",
            change("a-wrong-current-password", NEW_PASSWORD, "another-new-password"));
    HttpResponse<String> accepted =
        client.post("/api/v1/auth/change-password", change(rollNo, NEW_PASSWORD, NEW_PASSWORD));

    assertThat(refused.body())
        .doesNotContain(
            "a-wrong-current-password", NEW_PASSWORD, "another-new-password", "$2a$", "$2b$");
    assertThat(accepted.statusCode()).isEqualTo(204);
    assertThat(accepted.body()).isEmpty();
  }

  private ApiTestClient loggedInNewStudent(String rollNo) throws Exception {
    String email = emailOf(rollNo);
    if (!users.existsByLoginEmail(email)) {
      // Provisioned the way task T1-10 will do it: the temporary password is the roll number.
      User account =
          users.save(
              new User(email, passwordEncoder.encode(rollNo), Role.STUDENT, "Password Test"));
      profiles.save(
          new StudentProfile(account, rollNo, "Password Test", "CSE", 3, "A", 2023, null));
    }
    ApiTestClient client = new ApiTestClient(port);
    HttpResponse<String> login = client.login(email, rollNo);
    assertThat(login.statusCode()).isEqualTo(200);
    assertThat(json(login).get("mustChangePassword").asBoolean()).isTrue();
    return client;
  }

  private static void assertRefused(ApiTestClient client, String body, String expectedField)
      throws Exception {
    HttpResponse<String> response = client.post("/api/v1/auth/change-password", body);
    assertThat(response.statusCode()).as(expectedField).isEqualTo(400);
    assertThat(json(response).get("code").asString()).isEqualTo("VALIDATION_FAILED");
    assertThat(fieldsIn(response)).as(response.body()).contains(expectedField);
  }

  private static List<String> fieldsIn(HttpResponse<String> response) {
    return json(response)
        .get("errors")
        .valueStream()
        .map(error -> error.get("field").asString())
        .toList();
  }

  private static String emailOf(String rollNo) {
    return rollNo.toLowerCase(Locale.ROOT) + "@university.example";
  }

  private static String change(String current, String next, String confirm) {
    return "{\"currentPassword\":\""
        + current
        + "\",\"newPassword\":\""
        + next
        + "\",\"confirmNewPassword\":\""
        + confirm
        + "\"}";
  }
}
