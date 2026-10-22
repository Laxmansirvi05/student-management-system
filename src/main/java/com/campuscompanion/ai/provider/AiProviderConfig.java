package com.campuscompanion.ai.provider;

import com.campuscompanion.common.config.AiProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses the one AiProvider the application uses, from AI_PROVIDER: "mock" (the default, needs no
 * key) or "gemini" (needs AI_API_KEY and, optionally, AI_MODEL). Any other value, or "gemini" with
 * no key, stops the start with a clear message, instead of failing on the first request of a
 * student. The key is read from the environment only and is never written into a message.
 */
@Configuration
public class AiProviderConfig {

  @Bean
  public AiProvider aiProvider(AiProperties properties) {
    if ("mock".equalsIgnoreCase(properties.provider())) {
      return new MockAiProvider();
    }
    if ("gemini".equalsIgnoreCase(properties.provider())) {
      if (properties.apiKey() == null || properties.apiKey().isBlank()) {
        throw new IllegalStateException(
            "AI_PROVIDER is 'gemini', but AI_API_KEY is empty. Set the key in the environment, or"
                + " set AI_PROVIDER=mock.");
      }
      return new GeminiAiProvider(
          properties.apiKey().strip(), properties.model(), properties.connectTimeout());
    }
    throw new IllegalStateException(
        "AI_PROVIDER is set to '"
            + properties.provider()
            + "', which is not 'mock' or 'gemini'. Set AI_PROVIDER to one of them.");
  }
}
