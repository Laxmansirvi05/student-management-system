package com.campuscompanion.ai.provider;

import com.campuscompanion.common.config.AiProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses the one AiProvider the application uses, from AI_PROVIDER. Only "mock" exists so far. The
 * adapter for a real provider is added here when the owner has chosen the provider (task T5-06).
 * Any other value stops the start with a clear message, instead of failing on the first request of
 * a student.
 */
@Configuration
public class AiProviderConfig {

  @Bean
  public AiProvider aiProvider(AiProperties properties) {
    if ("mock".equalsIgnoreCase(properties.provider())) {
      return new MockAiProvider();
    }
    throw new IllegalStateException(
        "AI_PROVIDER is set to '"
            + properties.provider()
            + "', but this build has only the 'mock' provider. Set AI_PROVIDER=mock, or add the"
            + " adapter for the chosen provider first (task T5-06).");
  }
}
