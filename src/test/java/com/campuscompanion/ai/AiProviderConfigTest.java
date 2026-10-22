package com.campuscompanion.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campuscompanion.ai.provider.AiProviderConfig;
import com.campuscompanion.ai.provider.GeminiAiProvider;
import com.campuscompanion.ai.provider.MockAiProvider;
import com.campuscompanion.common.config.AiProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Which provider the application starts with, and the messages when the choice cannot be met. */
class AiProviderConfigTest {

  private static AiProperties properties(String provider, String key, String model) {
    return new AiProperties(
        true,
        provider,
        key,
        model,
        30,
        500,
        1200,
        5,
        2,
        Duration.ofSeconds(5),
        Duration.ofSeconds(20),
        Duration.ofSeconds(25));
  }

  private final AiProviderConfig config = new AiProviderConfig();

  @Test
  void mockIsTheDefaultAndNeedsNoKey() {
    assertThat(config.aiProvider(properties("mock", "", ""))).isInstanceOf(MockAiProvider.class);
    assertThat(config.aiProvider(properties("MOCK", null, null)))
        .isInstanceOf(MockAiProvider.class);
  }

  @Test
  void geminiNeedsAKeyFromTheEnvironment() {
    assertThat(config.aiProvider(properties("gemini", "a-made-up-key", "")))
        .isInstanceOf(GeminiAiProvider.class);
    assertThatThrownBy(() -> config.aiProvider(properties("gemini", "  ", "")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("AI_API_KEY");
  }

  @Test
  void anUnknownProviderStopsTheStartAndNeverShowsTheKey() {
    assertThatThrownBy(() -> config.aiProvider(properties("openai", "sk-secret-value", "")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("'openai'")
        .hasMessageNotContaining("sk-secret-value");
  }

  @Test
  void theKeyIsNotInThePropertiesText() {
    assertThat(properties("gemini", "sk-secret-value", "m").toString())
        .doesNotContain("sk-secret-value");
  }
}
