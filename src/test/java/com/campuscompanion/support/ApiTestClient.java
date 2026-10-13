package com.campuscompanion.support;

import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * A small browser stand-in for tests that talk to the running server over real HTTP. It keeps
 * cookies between requests like a browser does, and sends the CSRF token the way the frontend will:
 * read from the XSRF-TOKEN cookie, sent back in the X-XSRF-TOKEN header.
 */
public class ApiTestClient {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final String baseUrl;
  private final CookieManager cookies = new CookieManager();
  private final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();

  public ApiTestClient(int port) {
    this.baseUrl = "http://localhost:" + port;
  }

  public HttpResponse<String> get(String path) throws IOException, InterruptedException {
    return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET());
  }

  /** POST with the CSRF header, fetching a token first if this client has none yet. */
  public HttpResponse<String> post(String path, String json)
      throws IOException, InterruptedException {
    if (cookie("XSRF-TOKEN") == null) {
      get("/api/v1/auth/csrf");
    }
    return send(postBuilder(path, json).header("X-XSRF-TOKEN", cookie("XSRF-TOKEN")));
  }

  public HttpResponse<String> postWithoutCsrf(String path, String json)
      throws IOException, InterruptedException {
    return send(postBuilder(path, json));
  }

  public HttpResponse<String> login(String email, String password)
      throws IOException, InterruptedException {
    return post(
        "/api/v1/auth/login",
        JSON.writeValueAsString(java.util.Map.of("email", email, "password", password)));
  }

  /** The value of a cookie this client currently holds, or null. */
  public String cookie(String name) {
    return cookies.getCookieStore().getCookies().stream()
        .filter(cookie -> cookie.getName().equals(name))
        .map(HttpCookie::getValue)
        .findFirst()
        .orElse(null);
  }

  public static JsonNode json(HttpResponse<String> response) {
    return JSON.readTree(response.body());
  }

  private HttpRequest.Builder postBuilder(String path, String json) {
    return HttpRequest.newBuilder(URI.create(baseUrl + path))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(json));
  }

  private HttpResponse<String> send(HttpRequest.Builder request)
      throws IOException, InterruptedException {
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }
}
