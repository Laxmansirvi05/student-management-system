package com.campuscompanion.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.support.PostgresIntegrationTest;
import com.campuscompanion.support.TestAccounts;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Task T6-01: the application with the prod profile, as Heroku runs it. Heroku ends HTTPS at its
 * router and passes the request on with "X-Forwarded-Proto: https"; the tests send that header to
 * stand in for the router. The test client connects from this machine, an address Tomcat trusts,
 * just as it trusts the router's private address.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
class ProductionProfileIT extends PostgresIntegrationTest {

  @Value("${local.server.port}")
  private int port;

  @Autowired private TestAccounts accounts;

  private final HttpClient http = HttpClient.newHttpClient();

  private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpRequest.Builder overHttps(String path) {
    return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
        .header("X-Forwarded-Proto", "https");
  }

  private static String cookie(HttpResponse<String> response, String name) {
    return response.headers().allValues("Set-Cookie").stream()
        .filter(header -> header.startsWith(name + "="))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No " + name + " cookie was set"));
  }

  private static String valueOf(String setCookie) {
    return setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
  }

  @Test
  void plainHttpIsSentToHttps() throws Exception {
    // What the router passes on when someone opens the http:// address: port 80, plain HTTP.
    HttpResponse<String> response =
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/login.html"))
                .header("X-Forwarded-Proto", "http")
                .header("X-Forwarded-Port", "80"));

    assertThat(response.statusCode()).isEqualTo(302);
    assertThat(response.headers().firstValue("Location").orElse(""))
        .isEqualTo("https://localhost/login.html");
  }

  @Test
  void anHttpsResponseCarriesHstsAndTheOtherSecurityHeaders() throws Exception {
    HttpResponse<String> response = send(overHttps("/login.html"));

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Strict-Transport-Security").orElse(""))
        .contains("max-age=31536000")
        .contains("includeSubDomains");
    assertThat(response.headers().firstValue("Content-Security-Policy").orElse(""))
        .contains("script-src 'self'");
    assertThat(response.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
    assertThat(response.headers().firstValue("Referrer-Policy")).contains("no-referrer");
  }

  @Test
  void theSessionAndTheSecurityTokenTravelOnlyOverHttps() throws Exception {
    accounts.student("23PRD001", "PRD", 3, "A");
    HttpResponse<String> csrf = send(overHttps("/api/v1/auth/csrf"));
    String token = cookie(csrf, "XSRF-TOKEN");

    HttpResponse<String> login =
        send(
            overHttps("/api/v1/auth/login")
                .header("Content-Type", "application/json")
                .header("Cookie", "XSRF-TOKEN=" + valueOf(token))
                .header("X-XSRF-TOKEN", valueOf(token))
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"23prd001@university.example\",\"password\":\""
                            + TestAccounts.PASSWORD
                            + "\"}")));

    assertThat(login.statusCode()).as(login.body()).isEqualTo(200);
    String session = cookie(login, "JSESSIONID");
    assertThat(List.of(session.split(";\\s*"))).contains("Secure", "HttpOnly", "SameSite=Lax");
    assertThat(token).contains("Secure");
  }

  @Test
  void errorsShowNoInternalDetails() throws Exception {
    HttpResponse<String> response = send(overHttps("/no-such-page.html"));

    assertThat(response.body()).doesNotContain("Exception", "trace", "at com.campuscompanion");
  }

  @Test
  void onlyHealthIsExposedAndItSaysNoMoreThanUp() throws Exception {
    HttpResponse<String> health = send(overHttps("/actuator/health"));

    assertThat(health.statusCode()).isEqualTo(200);
    assertThat(health.body()).contains("\"status\":\"UP\"").doesNotContain("components", "db");
    assertThat(send(overHttps("/actuator/env")).statusCode()).isNotEqualTo(200);
    assertThat(send(overHttps("/actuator/beans")).statusCode()).isNotEqualTo(200);
  }
}
