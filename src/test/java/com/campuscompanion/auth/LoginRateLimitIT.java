package com.campuscompanion.auth;

import static com.campuscompanion.support.ApiTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.support.ApiTestClient;
import com.campuscompanion.support.PostgresIntegrationTest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Task T1-07, scenario AU12: one address may make only so many login attempts in a window. The
 * limit is lowered to three here so the test stays short; the real value is 20 in 10 minutes and is
 * covered by SlidingWindowRateLimiterTest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "app.security.login.max-attempts-per-ip=3")
class LoginRateLimitIT extends PostgresIntegrationTest {

  @Value("${local.server.port}")
  private int port;

  @Test
  void attemptsBeyondTheLimitAreRefusedWithARetryAfterHeader() throws Exception {
    ApiTestClient client = new ApiTestClient(port);
    for (int attempt = 1; attempt <= 3; attempt++) {
      assertThat(
              client.login("rate" + attempt + "@university.example", "wrong-password").statusCode())
          .as("attempt %s", attempt)
          .isEqualTo(401);
    }

    HttpResponse<String> refused = client.login("rate4@university.example", "wrong-password");

    assertThat(refused.statusCode()).isEqualTo(429);
    assertThat(json(refused).get("code").asString()).isEqualTo("RATE_LIMITED");
    assertThat(Long.parseLong(refused.headers().firstValue("Retry-After").orElseThrow()))
        .isBetween(1L, 600L);
  }

  @Test
  void aRequestWithoutTheCsrfTokenIsRefusedBeforeItIsCounted() throws Exception {
    ApiTestClient client = new ApiTestClient(port);

    for (int attempt = 1; attempt <= 10; attempt++) {
      assertThat(client.postWithoutCsrf("/api/v1/auth/login", "{}").statusCode()).isEqualTo(403);
    }
  }
}
