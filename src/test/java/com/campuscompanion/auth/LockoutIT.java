package com.campuscompanion.auth;

import static com.campuscompanion.support.ApiTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.support.ApiTestClient;
import com.campuscompanion.support.PostgresIntegrationTest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Task T1-07, scenario AU3: five consecutive failures block an email, and the block looks exactly
 * the same for an email that has an account and for one that has none.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LockoutIT extends PostgresIntegrationTest {

  @Value("${local.server.port}")
  private int port;

  @Autowired private UserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JdbcTemplate jdbcTemplate;

  private ApiTestClient client;

  @BeforeEach
  void setUp() {
    client = new ApiTestClient(port);
  }

  @Test
  void fiveFailuresBlockAnEmailIdenticallyWithOrWithoutAnAccount() throws Exception {
    String known = "lock23cse001@university.example";
    String unknown = "lock.nobody@university.example";
    createAccount(known, "LOCK23CSE001");

    for (int attempt = 1; attempt <= 5; attempt++) {
      assertThat(client.login(known, "wrong-password").statusCode())
          .as("known, attempt %s", attempt)
          .isEqualTo(401);
      assertThat(client.login(unknown, "wrong-password").statusCode())
          .as("unknown, attempt %s", attempt)
          .isEqualTo(401);
    }

    HttpResponse<String> blockedKnown = client.login(known, "wrong-password");
    HttpResponse<String> blockedUnknown = client.login(unknown, "wrong-password");

    assertThat(blockedKnown.statusCode()).isEqualTo(423);
    assertThat(json(blockedKnown).get("code").asString()).isEqualTo("AUTH_ACCOUNT_LOCKED");
    assertThat(json(blockedKnown).get("detail").asString())
        .isEqualTo("Too many attempts. Try again in a few minutes.");
    assertThat(comparable(blockedUnknown)).isEqualTo(comparable(blockedKnown));
  }

  @Test
  void theCorrectPasswordStillFailsDuringTheBlock() throws Exception {
    String email = "lock23cse002@university.example";
    createAccount(email, "LOCK23CSE002");
    for (int attempt = 1; attempt <= 5; attempt++) {
      client.login(email, "wrong-password");
    }

    HttpResponse<String> response = client.login(email, "LOCK23CSE002");

    assertThat(response.statusCode()).isEqualTo(423);
    assertThat(client.get("/api/v1/auth/me").statusCode()).isEqualTo(401);
    Integer locked =
        jdbcTemplate.queryForObject(
            "select count(*) from app.users where login_email = ? and locked_until > now()",
            Integer.class,
            email);
    assertThat(locked).isEqualTo(1);
  }

  @Test
  void aSuccessfulLoginResetsTheCounter() throws Exception {
    String email = "lock23cse003@university.example";
    createAccount(email, "LOCK23CSE003");
    for (int attempt = 1; attempt <= 4; attempt++) {
      client.login(email, "wrong-password");
    }
    assertThat(client.login(email, "LOCK23CSE003").statusCode()).isEqualTo(200);

    // A new browser: the one above now holds a session that may only change its password.
    client = new ApiTestClient(port);
    for (int attempt = 1; attempt <= 4; attempt++) {
      assertThat(client.login(email, "wrong-password").statusCode()).isEqualTo(401);
    }

    assertThat(client.login(email, "LOCK23CSE003").statusCode()).isEqualTo(200);
  }

  @Test
  void aDeactivatedAccountIsCountedAndBlockedLikeAnyOther() throws Exception {
    String email = "lock23cse004@university.example";
    createAccount(email, "LOCK23CSE004");
    jdbcTemplate.update("update app.users set active = false where login_email = ?", email);
    for (int attempt = 1; attempt <= 5; attempt++) {
      assertThat(client.login(email, "LOCK23CSE004").statusCode()).isEqualTo(401);
    }

    assertThat(client.login(email, "LOCK23CSE004").statusCode()).isEqualTo(423);
  }

  private void createAccount(String email, String password) {
    if (!users.existsByLoginEmail(email)) {
      users.save(new User(email, passwordEncoder.encode(password), Role.STUDENT, "Lockout Test"));
    }
  }

  private static String comparable(HttpResponse<String> response) {
    return response.statusCode()
        + " "
        + response.body().replaceAll("\"traceId\":\"[0-9a-f]+\"", "");
  }
}
