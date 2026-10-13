package com.campuscompanion.auth;

import static com.campuscompanion.support.ApiTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.support.ApiTestClient;
import com.campuscompanion.support.PostgresIntegrationTest;
import java.net.http.HttpResponse;
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
 * Task T1-06: login, logout, "who am I" and CSRF over real HTTP against the running server, with a
 * real session cookie. Covers scenarios AU1, AU2, AU4, AU7, AU11 and AU13 of
 * docs/17-testing-strategy.md. All accounts are synthetic.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthFlowIT extends PostgresIntegrationTest {

  private static final String STUDENT_EMAIL = "23cse101@university.example";
  private static final String STUDENT_ROLL_NO = "23CSE101";
  private static final String ADMIN_EMAIL = "auth.admin@university.example";
  private static final String ADMIN_PASSWORD = "a-strong-admin-password";
  private static final String INACTIVE_EMAIL = "23cse199@university.example";

  @Value("${local.server.port}")
  private int port;

  @Autowired private UserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JdbcTemplate jdbcTemplate;

  private ApiTestClient client;

  @BeforeEach
  void setUp() {
    client = new ApiTestClient(port);
    createAccount(STUDENT_EMAIL, STUDENT_ROLL_NO, Role.STUDENT, "Demo Student One");
    createAccount(ADMIN_EMAIL, ADMIN_PASSWORD, Role.ADMIN, "Demo Admin");
    createAccount(INACTIVE_EMAIL, "23CSE199", Role.STUDENT, "Deactivated Student");
    jdbcTemplate.update(
        "update app.users set active = false where login_email = ?", INACTIVE_EMAIL);
  }

  @Test
  void aVisitorIsToldToLogIn() throws Exception {
    HttpResponse<String> response = client.get("/api/v1/auth/me");

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.headers().firstValue("Content-Type").orElse(""))
        .contains("application/problem+json");
    assertThat(json(response).get("code").asString()).isEqualTo("AUTH_REQUIRED");
    assertThat(json(response).get("traceId").asString()).isNotBlank();
  }

  @Test
  void aProvisionedStudentLogsInWithTheInstitutionalEmailAndTheRollNumber() throws Exception {
    HttpResponse<String> response = client.login(STUDENT_EMAIL, STUDENT_ROLL_NO);

    assertThat(response.statusCode()).isEqualTo(200);
    JsonNode body = json(response);
    assertThat(body.get("email").asString()).isEqualTo(STUDENT_EMAIL);
    assertThat(body.get("displayName").asString()).isEqualTo("Demo Student One");
    assertThat(body.get("role").asString()).isEqualTo("STUDENT");
    assertThat(body.get("mustChangePassword").asBoolean()).isTrue();
    assertThat(body.propertyNames())
        .containsExactlyInAnyOrder("email", "displayName", "role", "mustChangePassword");
    assertThat(response.body()).doesNotContain(STUDENT_ROLL_NO).doesNotContain("$2a$");

    HttpResponse<String> me = client.get("/api/v1/auth/me");
    assertThat(me.statusCode()).isEqualTo(200);
    assertThat(json(me).get("email").asString()).isEqualTo(STUDENT_EMAIL);

    Integer logins =
        jdbcTemplate.queryForObject(
            "select count(*) from app.users where login_email = ? and last_login_at is not null",
            Integer.class,
            STUDENT_EMAIL);
    assertThat(logins).isEqualTo(1);
  }

  @Test
  void theSessionCookieIsHttpOnlyAndSameSiteLax() throws Exception {
    HttpResponse<String> response = client.login(STUDENT_EMAIL, STUDENT_ROLL_NO);

    String sessionCookie =
        response.headers().allValues("Set-Cookie").stream()
            .filter(value -> value.startsWith("JSESSIONID="))
            .findFirst()
            .orElseThrow();
    assertThat(sessionCookie).containsIgnoringCase("HttpOnly").containsIgnoringCase("SameSite=Lax");
  }

  @Test
  void loggingInAgainGivesTheSessionANewIdentifier() throws Exception {
    client.login(STUDENT_EMAIL, STUDENT_ROLL_NO);
    String first = client.cookie("JSESSIONID");

    client.login(STUDENT_EMAIL, STUDENT_ROLL_NO);
    String second = client.cookie("JSESSIONID");

    assertThat(first).isNotBlank();
    assertThat(second).isNotBlank().isNotEqualTo(first);
  }

  @Test
  void everyKindOfFailedLoginGetsTheIdenticalResponse() throws Exception {
    HttpResponse<String> wrongPassword = client.login(STUDENT_EMAIL, "not-the-password");
    HttpResponse<String> unknownEmail = client.login("nobody@university.example", STUDENT_ROLL_NO);
    HttpResponse<String> deactivated = client.login(INACTIVE_EMAIL, "23CSE199");

    for (HttpResponse<String> response : List.of(wrongPassword, unknownEmail, deactivated)) {
      assertThat(response.statusCode()).isEqualTo(401);
      assertThat(json(response).get("code").asString()).isEqualTo("AUTH_INVALID_CREDENTIALS");
      assertThat(json(response).get("detail").asString()).isEqualTo("Incorrect email or password.");
      assertThat(response.headers().allValues("Set-Cookie"))
          .noneMatch(value -> value.startsWith("JSESSIONID="));
    }
    assertThat(withoutTraceId(unknownEmail)).isEqualTo(withoutTraceId(wrongPassword));
    assertThat(withoutTraceId(deactivated)).isEqualTo(withoutTraceId(wrongPassword));
    assertThat(client.get("/api/v1/auth/me").statusCode()).isEqualTo(401);
  }

  @Test
  void logoutEndsTheSessionOnTheServer() throws Exception {
    client.login(STUDENT_EMAIL, STUDENT_ROLL_NO);
    assertThat(client.get("/api/v1/auth/me").statusCode()).isEqualTo(200);

    HttpResponse<String> logout = client.post("/api/v1/auth/logout", "");

    assertThat(logout.statusCode()).isEqualTo(204);
    assertThat(client.get("/api/v1/auth/me").statusCode()).isEqualTo(401);
  }

  @Test
  void emailCaseAndSurroundingSpacesDoNotMatter() throws Exception {
    HttpResponse<String> response =
        client.login("  23CSE101@University.Example  ", STUDENT_ROLL_NO);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(json(response).get("email").asString()).isEqualTo(STUDENT_EMAIL);
  }

  @Test
  void textThatIsNotAnEmailAddressIsRejectedAsInvalidInputWhateverItIs() throws Exception {
    HttpResponse<String> notAnEmail = client.login("not-an-email", STUDENT_ROLL_NO);
    HttpResponse<String> rollNumberOnly = client.login(STUDENT_ROLL_NO, STUDENT_ROLL_NO);
    HttpResponse<String> empty = client.login("", STUDENT_ROLL_NO);

    for (HttpResponse<String> response : List.of(notAnEmail, rollNumberOnly, empty)) {
      assertThat(response.statusCode()).isEqualTo(400);
      assertThat(json(response).get("code").asString()).isEqualTo("VALIDATION_FAILED");
      assertThat(json(response).get("errors").get(0).get("field").asString()).isEqualTo("email");
    }
    assertThat(withoutTraceId(rollNumberOnly)).isEqualTo(withoutTraceId(notAnEmail));
  }

  @Test
  void anUnknownFieldInTheBodyIsRejected() throws Exception {
    HttpResponse<String> response =
        client.post(
            "/api/v1/auth/login",
            "{\"email\":\""
                + STUDENT_EMAIL
                + "\",\"password\":\""
                + STUDENT_ROLL_NO
                + "\",\"role\":\"ADMIN\"}");

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(json(response).get("code").asString()).isEqualTo("VALIDATION_FAILED");
    assertThat(client.get("/api/v1/auth/me").statusCode()).isEqualTo(401);
  }

  @Test
  void stateChangingRequestsNeedTheCsrfToken() throws Exception {
    String credentials =
        "{\"email\":\"" + STUDENT_EMAIL + "\",\"password\":\"" + STUDENT_ROLL_NO + "\"}";

    HttpResponse<String> loginWithoutToken =
        client.postWithoutCsrf("/api/v1/auth/login", credentials);
    assertThat(loginWithoutToken.statusCode()).isEqualTo(403);
    assertThat(json(loginWithoutToken).get("code").asString()).isEqualTo("CSRF_INVALID");

    HttpResponse<String> csrf = client.get("/api/v1/auth/csrf");
    assertThat(json(csrf).get("headerName").asString()).isEqualTo("X-XSRF-TOKEN");
    assertThat(json(csrf).get("token").asString()).isEqualTo(client.cookie("XSRF-TOKEN"));

    assertThat(client.post("/api/v1/auth/login", credentials).statusCode()).isEqualTo(200);

    HttpResponse<String> logoutWithoutToken = client.postWithoutCsrf("/api/v1/auth/logout", "");
    assertThat(logoutWithoutToken.statusCode()).isEqualTo(403);
    assertThat(json(logoutWithoutToken).get("code").asString()).isEqualTo("CSRF_INVALID");
    assertThat(client.get("/api/v1/auth/me").statusCode()).isEqualTo(200);
  }

  @Test
  void aStudentIsDeniedAdminPathsAndAnAdminIsNot() throws Exception {
    client.login(STUDENT_EMAIL, STUDENT_ROLL_NO);
    HttpResponse<String> asStudent = client.get("/api/v1/admin/students");
    assertThat(asStudent.statusCode()).isEqualTo(403);
    assertThat(json(asStudent).get("code").asString()).isEqualTo("ACCESS_DENIED");

    ApiTestClient adminClient = new ApiTestClient(port);
    HttpResponse<String> adminLogin = adminClient.login(ADMIN_EMAIL, ADMIN_PASSWORD);
    assertThat(json(adminLogin).get("role").asString()).isEqualTo("ADMIN");
    // No admin endpoint exists yet, so "allowed" shows as not found, not as forbidden.
    assertThat(adminClient.get("/api/v1/admin/students").statusCode()).isEqualTo(404);
  }

  @Test
  void responsesCarryTheSecurityHeadersAndAreNotCached() throws Exception {
    HttpResponse<String> response = client.get("/api/v1/auth/me");

    assertThat(response.headers().firstValue("Content-Security-Policy").orElse(""))
        .contains("default-src 'self'")
        .contains("script-src 'self'")
        .contains("frame-ancestors 'none'");
    assertThat(response.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
    assertThat(response.headers().firstValue("Referrer-Policy")).contains("no-referrer");
    assertThat(response.headers().firstValue("Permissions-Policy").orElse(""))
        .contains("camera=()");
    assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
  }

  private void createAccount(String email, String password, Role role, String displayName) {
    if (!users.existsByLoginEmail(email)) {
      users.save(new User(email, passwordEncoder.encode(password), role, displayName));
    }
  }

  /** The response with the parts that legitimately differ between two requests removed. */
  private static String withoutTraceId(HttpResponse<String> response) {
    return response.statusCode()
        + " "
        + response.body().replaceAll("\"traceId\":\"[0-9a-f]+\"", "");
  }
}
