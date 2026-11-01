package com.campuscompanion.ai.provider;

import java.time.Duration;
import java.util.List;

/**
 * One request to the model. The instructions and the student's content are kept in two separate
 * fields all the way to the provider, and nothing here says who the student is.
 *
 * @param promptVersion which template produced the instructions, for example "explain-v1"
 * @param systemInstructions the rules, from a template file in the repository
 * @param userContent the student's text, already wrapped in its tags
 * @param images the pictures that go with the text, usually none
 */
public record AiRequest(
    String promptVersion,
    String systemInstructions,
    String userContent,
    int maxOutputTokens,
    Duration timeout,
    List<AiImage> images) {

  public AiRequest {
    images = images == null ? List.of() : List.copyOf(images);
  }

  /** A request without pictures. */
  public AiRequest(
      String promptVersion,
      String systemInstructions,
      String userContent,
      int maxOutputTokens,
      Duration timeout) {
    this(promptVersion, systemInstructions, userContent, maxOutputTokens, timeout, List.of());
  }

  /** The same request with these pictures. */
  public AiRequest withImages(List<AiImage> pictures) {
    return new AiRequest(
        promptVersion, systemInstructions, userContent, maxOutputTokens, timeout, pictures);
  }

  /** The same request with another time limit, as the router sets for each call. */
  public AiRequest withTimeout(Duration limit) {
    return new AiRequest(
        promptVersion, systemInstructions, userContent, maxOutputTokens, limit, images);
  }
}
