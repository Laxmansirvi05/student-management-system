package com.campuscompanion.ai.provider;

import java.time.Duration;

/**
 * The call to the AI provider failed. It carries only the kind of failure (and, for a rate limit,
 * how long the provider asked us to wait), never the provider's own message, because that message
 * can repeat the student's text or part of a key.
 */
public class AiProviderException extends Exception {

  public enum Kind {
    /** The answer did not come in time. */
    TIMEOUT,
    /** Too many requests, or the account's quota is used up: wait before asking this provider. */
    RATE_LIMITED,
    /** The provider is down or unreachable, or answered with something that is not its format. */
    UNAVAILABLE,
    /** The provider refused the request as malformed. */
    BAD_REQUEST,
    /** The key is wrong, revoked or refused (401, 403). Another key may still work. */
    AUTH_FAILED,
    /** The model does not exist, or is no longer offered to this account (404). */
    MODEL_UNAVAILABLE,
    /** Every provider answered, but no answer passed the application's checks. */
    INVALID_OUTPUT
  }

  private final Kind kind;
  private final Duration retryAfter;

  public AiProviderException(Kind kind) {
    this(kind, null);
  }

  public AiProviderException(Kind kind, Duration retryAfter) {
    super("The AI provider call failed: " + kind);
    this.kind = kind;
    this.retryAfter = retryAfter;
  }

  public Kind getKind() {
    return kind;
  }

  /** How long the provider asked us to wait, or null when it did not say. */
  public Duration getRetryAfter() {
    return retryAfter;
  }
}
