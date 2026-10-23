package com.campuscompanion.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.support.ApiIntegrationTest;
import com.campuscompanion.support.ApiTestClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Static files are compressed and kept by the browser where that is safe, while pages and API
 * answers are never kept and JSON is never compressed (CC-04).
 */
class StaticResourceCachingIT extends ApiIntegrationTest {

  private final HttpClient http = HttpClient.newHttpClient();

  private HttpResponse<byte[]> fetch(String path, String... headers) throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    if (headers.length > 0) {
      request.headers(headers);
    }
    return http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
  }

  private static String cacheControl(HttpResponse<?> response) {
    return response.headers().firstValue("Cache-Control").orElse("");
  }

  private String landingScript() throws Exception {
    String page = new String(fetch("/").body());
    Matcher script = Pattern.compile("/js/landing/[A-Za-z0-9_-]+\\.js").matcher(page);
    assertThat(script.find()).as("the landing page names its script").isTrue();
    return script.group();
  }

  @Test
  void theLandingPagesHashedFilesAreKeptForAYear() throws Exception {
    HttpResponse<byte[]> script = fetch(landingScript());
    assertThat(script.statusCode()).isEqualTo(200);
    assertThat(cacheControl(script))
        .contains("max-age=31536000")
        .contains("public")
        .contains("immutable");
  }

  @Test
  void otherStaticFilesAreKeptButCheckedAgainSoAnUnchangedOneCostsA304() throws Exception {
    HttpResponse<byte[]> first = fetch("/js/api.js");
    assertThat(first.statusCode()).isEqualTo(200);
    assertThat(cacheControl(first))
        .contains("no-cache")
        .contains("private")
        .doesNotContain("no-store");
    String lastModified = first.headers().firstValue("Last-Modified").orElseThrow();

    HttpResponse<byte[]> again = fetch("/js/api.js", "If-Modified-Since", lastModified);
    assertThat(again.statusCode()).isEqualTo(304);
    assertThat(again.body()).isEmpty();
  }

  @Test
  void theRobotsFilesAreKeptButStillNeedALogin() throws Exception {
    HttpResponse<byte[]> anonymous = fetch("/vendor/spline/runtime.js");
    assertThat(anonymous.statusCode()).isNotEqualTo(200);

    accounts.student("CCHA0001", "CSE", 3, "A");
    ApiTestClient student = studentClient("CCHA0001");
    HttpResponse<String> runtime = student.get("/vendor/spline/runtime.js");
    assertThat(runtime.statusCode()).isEqualTo(200);
    assertThat(cacheControl(runtime)).contains("no-cache").contains("private");
  }

  @Test
  void pagesAndApiAnswersAreNeverKept() throws Exception {
    assertThat(cacheControl(fetch("/login.html"))).contains("no-store");
    assertThat(cacheControl(fetch("/"))).contains("no-store");
    accounts.student("CCHA0002", "CSE", 3, "A");
    ApiTestClient student = studentClient("CCHA0002");
    assertThat(cacheControl(student.get("/api/v1/auth/me"))).contains("no-store");
    assertThat(cacheControl(student.get("/student/dashboard.html"))).contains("no-store");
  }

  @Test
  void scriptsAreCompressedButJsonNever() throws Exception {
    HttpResponse<byte[]> script = fetch(landingScript(), "Accept-Encoding", "gzip");
    assertThat(script.headers().firstValue("Content-Encoding")).hasValue("gzip");
    assertThat(
            fetch("/css/base.css", "Accept-Encoding", "gzip")
                .headers()
                .firstValue("Content-Encoding"))
        .hasValue("gzip");

    // The CSRF answer holds a secret: it must never be compressed (BREACH).
    HttpResponse<byte[]> csrf = fetch("/api/v1/auth/csrf", "Accept-Encoding", "gzip");
    assertThat(csrf.statusCode()).isEqualTo(200);
    assertThat(csrf.headers().firstValue("Content-Encoding")).isEmpty();
  }
}
