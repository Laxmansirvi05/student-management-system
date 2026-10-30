package com.campuscompanion.coding.platform;

import com.campuscompanion.coding.platform.PlatformException.Kind;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Sends one request to a coding site and returns the body of a successful answer. Redirects are not
 * followed: a site that sends an unknown user to its home page (CodeChef) answers "not found". The
 * timeout covers the whole answer, body included.
 */
public class PlatformHttp {

  /** Some sites refuse a request without a browser-like product name. */
  static final String USER_AGENT =
      "Mozilla/5.0 (compatible; CampusCompanion/0.1; +https://campus-companion.azurewebsites.net)";

  private final HttpClient http;
  private final Duration timeout;

  public PlatformHttp(Duration timeout) {
    this.timeout = timeout;
    this.http =
        HttpClient.newBuilder()
            .connectTimeout(timeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  public String get(String url, Map<String, String> headers) throws PlatformException {
    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).GET();
    return send(request, headers);
  }

  public String postJson(String url, String json, Map<String, String> headers)
      throws PlatformException {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
    return send(request, headers);
  }

  private String send(HttpRequest.Builder request, Map<String, String> headers)
      throws PlatformException {
    request.timeout(timeout).header("User-Agent", USER_AGENT);
    headers.forEach(request::header);
    CompletableFuture<HttpResponse<String>> pending =
        http.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    HttpResponse<String> response;
    try {
      response = pending.get(timeout.toMillis() + 2000, TimeUnit.MILLISECONDS);
    } catch (TimeoutException | ExecutionException failed) {
      pending.cancel(true);
      throw new PlatformException(Kind.UNAVAILABLE);
    } catch (InterruptedException interrupted) {
      pending.cancel(true);
      Thread.currentThread().interrupt();
      throw new PlatformException(Kind.UNAVAILABLE);
    }
    return bodyOrFailure(response.statusCode(), response.body());
  }

  /** The body of a 2xx answer, or the failure an answer means. */
  static String bodyOrFailure(int status, String body) throws PlatformException {
    if (status >= 200 && status < 300) {
      return body;
    }
    boolean saysNotFound = body != null && body.toLowerCase(Locale.ROOT).contains("not found");
    if (status == 404 || (status >= 300 && status < 400) || (status == 400 && saysNotFound)) {
      throw new PlatformException(Kind.NOT_FOUND);
    }
    throw new PlatformException(Kind.UNAVAILABLE);
  }
}
