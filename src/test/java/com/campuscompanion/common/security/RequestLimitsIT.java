package com.campuscompanion.common.security;

import static com.campuscompanion.support.ApiTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.support.ApiIntegrationTest;
import com.campuscompanion.support.ApiTestClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * The general limits of the API (docs/09-api-contract.md section 6): calls per session per minute,
 * and the size of a JSON body. The call limit is lowered to 5 here so the test stays short; the
 * rule is the same at 120.
 */
@TestPropertySource(properties = "app.security.api.max-requests-per-session=5")
class RequestLimitsIT extends ApiIntegrationTest {

  @Test
  void theSixthCallOfASessionInAMinuteIsRefusedAndOtherSessionsAreNotAffected() throws Exception {
    accounts.student("23LIM001", "LIM", 3, "A");
    ApiTestClient first = studentClient("23LIM001");
    ApiTestClient second = studentClient("23LIM001");

    for (int call = 1; call <= 5; call++) {
      assertThat(first.get("/api/v1/students/me").statusCode()).as("call %s", call).isEqualTo(200);
    }
    HttpResponse<String> refused = first.get("/api/v1/students/me");

    assertProblem(refused, 429, "RATE_LIMITED");
    long retryAfter = Long.parseLong(refused.headers().firstValue("Retry-After").orElseThrow());
    assertThat(retryAfter).isBetween(1L, 60L);
    // The limit belongs to one session. The same student logged in elsewhere is not held back.
    assertThat(second.get("/api/v1/students/me").statusCode()).isEqualTo(200);
  }

  @Test
  void callsWithoutASessionAreTurnedAwayBeforeTheyAreCounted() throws Exception {
    ApiTestClient visitor = visitor();

    for (int call = 1; call <= 8; call++) {
      assertProblem(visitor.get("/api/v1/students/me"), 401, "AUTH_REQUIRED");
    }
  }

  @Test
  void aJsonBodyOverSixtyFourKilobytesIsRefusedBeforeItIsRead() throws Exception {
    accounts.student("23LIM002", "LIM", 3, "A");
    ApiTestClient student = studentClient("23LIM002");
    String tooLarge = "{\"phone\":null,\"bio\":\"" + "x".repeat(64 * 1024) + "\"}";

    HttpResponse<String> response = student.put("/api/v1/students/me", tooLarge);

    assertProblem(response, 413, "REQUEST_TOO_LARGE");
    assertThat(json(response).get("detail").asString())
        .isEqualTo("The request is larger than 64 KB.");
    // Just under the limit, the request is read and judged on its content instead.
    String justUnder = "{\"phone\":null,\"bio\":\"" + "x".repeat(60 * 1024) + "\"}";
    assertThat(invalidFields(student.put("/api/v1/students/me", justUnder))).containsExactly("bio");
  }

  @Test
  void aBodySentInPiecesWithoutALengthIsAlsoCutOff() throws Exception {
    accounts.student("23LIM003", "LIM", 3, "A");
    ApiTestClient student = studentClient("23LIM003");
    String tooLarge = "{\"phone\":null,\"bio\":\"" + "x".repeat(80 * 1024) + "\"}";
    // A body from a stream has no Content-Length, so the server cannot judge it before reading.
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/students/me"))
            .header("Content-Type", "application/json")
            .header(
                "Cookie",
                "JSESSIONID=" + student.cookie("JSESSIONID") + "; XSRF-TOKEN=" + csrf(student))
            .header("X-XSRF-TOKEN", csrf(student))
            .PUT(
                HttpRequest.BodyPublishers.ofInputStream(
                    () -> new java.io.ByteArrayInputStream(tooLarge.getBytes())))
            .build();

    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
    // Refused as unreadable, not judged field by field: had the whole body been read, the answer
    // would have named the over-long "bio" field.
    assertThat(response.body()).doesNotContain("\"bio\"");
    assertThat(json(student.get("/api/v1/students/me")).get("bio").isNull()).isTrue();
  }

  private static String csrf(ApiTestClient client) throws Exception {
    if (client.cookie("XSRF-TOKEN") == null) {
      client.get("/api/v1/auth/csrf");
    }
    return client.cookie("XSRF-TOKEN");
  }
}
