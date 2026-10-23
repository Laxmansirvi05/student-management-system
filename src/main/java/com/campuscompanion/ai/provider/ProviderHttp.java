package com.campuscompanion.ai.provider;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * What every HTTP adapter shares: sending one call and turning an unsuccessful answer into an
 * {@link AiProviderException} of the right kind. The provider's own words are never kept.
 */
final class ProviderHttp {

  /** Sent on every call. Some providers' firewalls refuse a request without a product name. */
  static final String USER_AGENT = "CampusCompanion/0.1 (server)";

  /** A provider's Retry-After is honoured, but never for longer than this. */
  private static final Duration LONGEST_WAIT = Duration.ofMinutes(10);

  private ProviderHttp() {}

  /**
   * Sends the call and returns the body of a successful (2xx) answer. The call's timeout covers the
   * whole answer, body included: Java's own request timeout stops at the headers, and some
   * providers send the headers at once and then the body slowly.
   */
  static String send(HttpClient http, HttpRequest call) throws AiProviderException {
    Duration limit = call.timeout().orElse(Duration.ofSeconds(30));
    CompletableFuture<HttpResponse<String>> pending =
        http.sendAsync(call, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    HttpResponse<String> response;
    try {
      response = pending.get(limit.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException tooSlow) {
      pending.cancel(true);
      throw new AiProviderException(AiProviderException.Kind.TIMEOUT);
    } catch (InterruptedException interrupted) {
      pending.cancel(true);
      Thread.currentThread().interrupt();
      throw new AiProviderException(AiProviderException.Kind.UNAVAILABLE);
    } catch (ExecutionException failed) {
      if (failed.getCause() instanceof HttpTimeoutException) {
        throw new AiProviderException(AiProviderException.Kind.TIMEOUT);
      }
      throw new AiProviderException(AiProviderException.Kind.UNAVAILABLE);
    }
    int status = response.statusCode();
    if (status >= 200 && status < 300) {
      return response.body();
    }
    throw new AiProviderException(
        kindOf(status), retryAfter(response.headers().firstValue("Retry-After")));
  }

  /** The kind of failure an HTTP status means. */
  static AiProviderException.Kind kindOf(int status) {
    return switch (status) {
      case 401, 403 -> AiProviderException.Kind.AUTH_FAILED;
      case 404 -> AiProviderException.Kind.MODEL_UNAVAILABLE;
      case 408 -> AiProviderException.Kind.TIMEOUT;
      case 429 -> AiProviderException.Kind.RATE_LIMITED;
      default ->
          status >= 500
              ? AiProviderException.Kind.UNAVAILABLE
              : AiProviderException.Kind.BAD_REQUEST;
    };
  }

  /** A Retry-After in seconds, capped; null when absent or not a number of seconds. */
  static Duration retryAfter(Optional<String> header) {
    if (header.isEmpty()) {
      return null;
    }
    try {
      long seconds = Long.parseLong(header.get().strip());
      if (seconds <= 0) {
        return null;
      }
      Duration wait = Duration.ofSeconds(seconds);
      return wait.compareTo(LONGEST_WAIT) > 0 ? LONGEST_WAIT : wait;
    } catch (NumberFormatException notSeconds) {
      return null;
    }
  }
}
