package com.campuscompanion.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campuscompanion.ai.provider.AiProvider;
import com.campuscompanion.ai.provider.AiProviderConfig;
import com.campuscompanion.ai.provider.GeminiAiProvider;
import com.campuscompanion.ai.provider.MockAiProvider;
import com.campuscompanion.ai.provider.OpenAiCompatibleAiProvider;
import com.campuscompanion.ai.provider.RoutingAiProvider;
import com.campuscompanion.common.config.AiProperties;
import com.campuscompanion.common.config.AiRoutingProperties;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** Which provider the application starts with, and the messages when the choice cannot be met. */
class AiProviderConfigTest {

  private static final String SECRET = "sk-secret-value";

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

  private static AiRoutingProperties routing(String routes) {
    return new AiRoutingProperties(routes, "", 3, Duration.ofSeconds(15));
  }

  private final AiProviderConfig config = new AiProviderConfig();

  private AiProvider start(String provider, String key, String routes, MockEnvironment keys) {
    return config.aiProvider(
        properties(provider, key, ""), routing(routes), keys, Clock.systemUTC());
  }

  private AiProvider start(String provider, String key) {
    return start(provider, key, "", new MockEnvironment());
  }

  @Test
  void mockIsTheDefaultAndNeedsNoKey() {
    assertThat(start("mock", "")).isInstanceOf(MockAiProvider.class);
    assertThat(start("MOCK", null)).isInstanceOf(MockAiProvider.class);
  }

  @Test
  void geminiNeedsAKeyFromTheEnvironment() {
    assertThat(start("gemini", "a-made-up-key")).isInstanceOf(GeminiAiProvider.class);
    assertThatThrownBy(() -> start("gemini", "  "))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("AI_API_KEY");
  }

  @Test
  void anUnknownProviderStopsTheStartAndNeverShowsTheKey() {
    assertThatThrownBy(() -> start("openai", SECRET))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("'openai'")
        .hasMessageNotContaining(SECRET);
  }

  @Test
  void theKeyIsNotInThePropertiesText() {
    assertThat(properties("gemini", SECRET, "m").toString()).doesNotContain(SECRET);
  }

  @Test
  void theRouterTriesTheListedModelsInOrderAndEachProvidersKeysInNumberOrder() {
    MockEnvironment keys =
        new MockEnvironment()
            .withProperty("GROQ_API_KEY", "groq-one")
            .withProperty("GROQ_API_KEY_3", "groq-three")
            .withProperty("OLLAMA_API_KEY_2", "ollama-two")
            .withProperty("GEMINI_API_KEY", "gemini-one");
    AiProvider provider =
        start(
            "router",
            "",
            "groq:openai/gpt-oss-120b, ollama:gpt-oss:20b ,gemini:gemini-3.5-flash-lite,"
                + " openrouter:some/model:free",
            keys);

    assertThat(provider).isInstanceOf(RoutingAiProvider.class);
    var routes = ((RoutingAiProvider) provider).routes();
    // OpenRouter has no key, so its entry is skipped; the mock is never a route.
    assertThat(routes)
        .extracting(RoutingAiProvider.Route::label)
        .containsExactly(
            "groq:openai/gpt-oss-120b (key 1)",
            "groq:openai/gpt-oss-120b (key 3)",
            "ollama:gpt-oss:20b (key 2)",
            "gemini:gemini-3.5-flash-lite (key 1)");
    assertThat(routes.get(0).adapter()).isInstanceOf(OpenAiCompatibleAiProvider.class);
    assertThat(routes.get(3).adapter()).isInstanceOf(GeminiAiProvider.class);
    assertThat(routes)
        .extracting(RoutingAiProvider.Route::label)
        .noneMatch(label -> label.contains("groq-one") || label.contains("gemini-one"));
  }

  @Test
  void aRouterWithoutAnyUsableRouteStopsTheStart() {
    assertThatThrownBy(() -> start("router", "", "", new MockEnvironment()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("AI_ROUTES");
    assertThatThrownBy(() -> start("router", "", "groq:openai/gpt-oss-20b", new MockEnvironment()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no model whose provider has a key");
  }

  @Test
  void aMistypedRouteStopsTheStartWithoutShowingAnyKey() {
    MockEnvironment keys = new MockEnvironment().withProperty("GROQ_API_KEY", SECRET);
    assertThatThrownBy(() -> start("router", "", "groq-openai-model", keys))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("'provider:model'")
        .hasMessageNotContaining(SECRET);
    assertThatThrownBy(() -> start("router", "", "openai:gpt-9", keys))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("'openai'")
        .hasMessageNotContaining(SECRET);
  }

  // --- the models that read pictures --------------------------------------------------------

  private com.campuscompanion.ai.provider.VisionProvider vision(
      String provider, String routes, String visionRoutes, MockEnvironment keys) {
    AiProvider main = start("mock", "");
    return config.visionProvider(
        properties(provider, "", ""),
        new AiRoutingProperties(routes, visionRoutes, 3, Duration.ofSeconds(15)),
        keys,
        Clock.systemUTC(),
        main);
  }

  @Test
  void withTheMockOrOneGeminiModelThePictureModelIsTheMainOne() {
    assertThat(vision("mock", "", "", new MockEnvironment()).provider())
        .isInstanceOf(MockAiProvider.class);
    assertThat(vision("mock", "", "", new MockEnvironment()).available()).isTrue();
  }

  @Test
  void theRouterUsesTheListedPictureModels() {
    MockEnvironment keys = new MockEnvironment().withProperty("GROQ_API_KEY", SECRET);
    var vision = vision("router", "groq:text-only", "groq:vl-model", keys);

    assertThat(vision.available()).isTrue();
    RoutingAiProvider router = (RoutingAiProvider) vision.provider();
    assertThat(router.routes()).extracting(route -> route.model()).containsExactly("vl-model");
  }

  @Test
  void withoutAPictureListTheGeminiModelsOfTheRouteListAreUsed() {
    MockEnvironment keys =
        new MockEnvironment()
            .withProperty("GROQ_API_KEY", SECRET)
            .withProperty("GEMINI_API_KEY", SECRET);
    var vision = vision("router", "groq:text-only,gemini:flash-test", "", keys);

    RoutingAiProvider router = (RoutingAiProvider) vision.provider();
    assertThat(router.routes()).extracting(route -> route.provider()).containsExactly("gemini");
  }

  @Test
  void withNoPictureModelOrNoKeyPictureQuestionsAreSwitchedOffNotTheStartUp() {
    MockEnvironment groqOnly = new MockEnvironment().withProperty("GROQ_API_KEY", SECRET);
    assertThat(vision("router", "groq:text-only", "", groqOnly).available()).isFalse();
    assertThat(vision("router", "groq:text-only", "gemini:flash", groqOnly).available()).isFalse();
  }
}
