package com.campuscompanion.web;

import static com.campuscompanion.support.ApiTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.support.ApiIntegrationTest;
import com.campuscompanion.support.ApiTestClient;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Scenarios Z1, Z2 and Z11 of docs/17-testing-strategy.md, for every endpoint at once. The list of
 * endpoints is read from the running application, so an endpoint added later is covered without
 * anyone remembering to add it here.
 *
 * <ul>
 *   <li>Z1: a visitor gets 401 from every endpoint except the three that must work before login.
 *   <li>Z2: a student gets 403 from every admin endpoint.
 *   <li>Z11: an admin gets 403 from every endpoint that belongs to students only.
 * </ul>
 */
class EndpointAccessIT extends ApiIntegrationTest {

  /**
   * What a visitor may call: the CSRF token, logging in, and logging out (which then does nothing).
   */
  private static final Set<String> PUBLIC =
      Set.of("GET /api/v1/auth/csrf", "POST /api/v1/auth/login", "POST /api/v1/auth/logout");

  /** The paths that belong to students only (SecurityConfig.STUDENT_ONLY_API). */
  private static final List<String> STUDENT_ONLY =
      List.of(
          "/api/v1/students/",
          "/api/v1/attendance/",
          "/api/v1/marks/",
          "/api/v1/results/",
          "/api/v1/timetable/",
          "/api/v1/bookmarks/",
          "/api/v1/pyqs",
          "/api/v1/complaints",
          "/api/v1/quiz-attempts",
          "/api/v1/progress/",
          "/api/v1/ai/");

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping mappings;

  private List<Endpoint> endpoints;

  /** One method on one path, with every path variable filled in with 1. */
  private record Endpoint(String method, String pattern) {

    String path() {
      return pattern.replaceAll("\\{[^}]+}", "1");
    }

    @Override
    public String toString() {
      return method + " " + pattern;
    }
  }

  @BeforeEach
  void readTheEndpointsOfTheApplication() {
    Set<String> seen = new TreeSet<>();
    endpoints = new ArrayList<>();
    for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
      for (String pattern : info.getPatternValues()) {
        if (!pattern.startsWith("/api/")) {
          continue;
        }
        for (RequestMethod method : info.getMethodsCondition().getMethods()) {
          if (seen.add(method + " " + pattern)) {
            endpoints.add(new Endpoint(method.name(), pattern));
          }
        }
      }
    }
  }

  private static HttpResponse<String> call(ApiTestClient client, Endpoint endpoint)
      throws Exception {
    return switch (endpoint.method()) {
      case "GET" -> client.get(endpoint.path());
      case "POST" -> client.post(endpoint.path(), "{}");
      case "PUT" -> client.put(endpoint.path(), "{}");
      case "DELETE" -> client.delete(endpoint.path());
      default -> throw new IllegalStateException("No test for " + endpoint);
    };
  }

  private static void assertRefused(
      HttpResponse<String> response, Endpoint endpoint, int status, String code) {
    assertThat(response.statusCode()).as("%s: %s", endpoint, response.body()).isEqualTo(status);
    assertThat(json(response).get("code").asString()).as(endpoint.toString()).isEqualTo(code);
  }

  @Test
  void theApplicationHasTheEndpointsThisTestExpectsToFind() {
    // A guard for the test itself: if the list came back empty, every check below would pass.
    assertThat(endpoints).hasSizeGreaterThan(60);
    assertThat(endpoints.stream().map(Endpoint::toString))
        .contains(
            "GET /api/v1/students/me",
            "PUT /api/v1/admin/attendance",
            "POST /api/v1/quiz-attempts/{id}/submit",
            "POST /api/v1/ai/explain",
            "DELETE /api/v1/admin/resources/{id}");
  }

  @Test
  void aVisitorIsTurnedAwayByEveryEndpointThatNeedsALogin() throws Exception {
    ApiTestClient visitor = visitor();
    int checked = 0;
    for (Endpoint endpoint : endpoints) {
      if (PUBLIC.contains(endpoint.toString())) {
        continue;
      }
      assertRefused(call(visitor, endpoint), endpoint, 401, "AUTH_REQUIRED");
      checked++;
    }
    assertThat(checked).isEqualTo(endpoints.size() - PUBLIC.size());
  }

  @Test
  void aStudentIsRefusedByEveryAdminEndpoint() throws Exception {
    accounts.student("23ACC001", "ACC", 3, "A");
    ApiTestClient student = studentClient("23ACC001");
    List<Endpoint> admin =
        endpoints.stream()
            .filter(endpoint -> endpoint.pattern().startsWith("/api/v1/admin/"))
            .toList();

    assertThat(admin).hasSizeGreaterThan(30);
    for (Endpoint endpoint : admin) {
      assertRefused(call(student, endpoint), endpoint, 403, "ACCESS_DENIED");
    }
  }

  @Test
  void anAdminIsRefusedByEveryEndpointThatBelongsToStudents() throws Exception {
    ApiTestClient admin = adminClient("access-suite-admin@university.example");
    List<Endpoint> studentOnly =
        endpoints.stream()
            .filter(endpoint -> STUDENT_ONLY.stream().anyMatch(endpoint.pattern()::startsWith))
            .toList();

    assertThat(studentOnly).hasSizeGreaterThan(15);
    for (Endpoint endpoint : studentOnly) {
      assertRefused(call(admin, endpoint), endpoint, 403, "ACCESS_DENIED");
    }
  }
}
