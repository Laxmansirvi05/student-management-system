package com.campuscompanion.ai.provider;

/**
 * The one way the application reaches an AI model. Everything else in the application works with
 * this interface, so the model can be replaced, or stood in for by {@link MockAiProvider}, without
 * touching any other class. An implementation makes one call and returns the model's raw text. It
 * does not read or judge the text.
 */
public interface AiProvider {

  AiCompletion complete(AiRequest request) throws AiProviderException;
}
