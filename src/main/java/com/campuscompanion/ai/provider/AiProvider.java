package com.campuscompanion.ai.provider;

import java.util.function.Predicate;

/**
 * The one way the application reaches an AI model. Everything else in the application works with
 * this interface, so the model can be replaced, or stood in for by {@link MockAiProvider}, without
 * touching any other class. An implementation makes one call and returns the model's raw text. It
 * does not read or judge the text.
 */
public interface AiProvider {

  AiCompletion complete(AiRequest request) throws AiProviderException;

  /**
   * The same call, with the caller's check of the reply. A single provider ignores the check and
   * the caller judges the reply as before; {@link RoutingAiProvider} uses it to try the next model
   * when a reply cannot be used.
   */
  default AiCompletion complete(AiRequest request, Predicate<String> usable)
      throws AiProviderException {
    return complete(request);
  }
}
