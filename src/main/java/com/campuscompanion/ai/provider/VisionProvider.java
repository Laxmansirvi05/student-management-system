package com.campuscompanion.ai.provider;

/**
 * The way to the models that read pictures, for questions with an image (Quick Assistant). Kept
 * apart from the main {@link AiProvider} because not every model can read a picture: sending one to
 * a text-only model fails. The provider is null when none is configured, and the caller then tells
 * the student that picture questions are not set up.
 */
public record VisionProvider(AiProvider provider) {

  public boolean available() {
    return provider != null;
  }
}
