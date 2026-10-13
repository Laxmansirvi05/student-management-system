package com.campuscompanion.ai.provider;

/**
 * The call to the AI provider failed. It carries only the kind of failure, never the provider's own
 * message, because that message can repeat the student's text.
 */
public class AiProviderException extends Exception {

  public enum Kind {
    TIMEOUT,
    RATE_LIMITED,
    UNAVAILABLE,
    BAD_REQUEST
  }

  private final Kind kind;

  public AiProviderException(Kind kind) {
    super("The AI provider call failed: " + kind);
    this.kind = kind;
  }

  public Kind getKind() {
    return kind;
  }
}
