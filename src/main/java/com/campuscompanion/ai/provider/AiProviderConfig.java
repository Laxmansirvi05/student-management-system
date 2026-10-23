package com.campuscompanion.ai.provider;

import com.campuscompanion.common.config.AiProperties;
import com.campuscompanion.common.config.AiRoutingProperties;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Chooses the AiProvider the application uses, from AI_PROVIDER:
 *
 * <ul>
 *   <li>"mock" (the default): canned answers, no key, no network. Every automated test uses it.
 *   <li>"gemini": one Gemini model with AI_API_KEY (and optionally AI_MODEL).
 *   <li>"router": the models listed in AI_ROUTES, tried in that order with automatic fallback
 *       ({@link RoutingAiProvider}). Each provider's keys are read from its own variables.
 * </ul>
 *
 * A setting that cannot work stops the start with a clear message instead of failing on a student's
 * first request. Keys are read from the environment only and never written into a message or a log:
 * a route is named by its provider, model and key number.
 */
@Configuration
public class AiProviderConfig {

  /** The providers the router knows, and where their chat API lives. Fixed in code on purpose. */
  static final Map<String, String> OPENAI_COMPATIBLE =
      Map.of(
          "groq", "https://api.groq.com/openai/v1",
          "openrouter", "https://openrouter.ai/api/v1",
          "ollama", "https://ollama.com/v1");

  /** A provider may have up to this many keys: NAME_API_KEY, NAME_API_KEY_2 ... NAME_API_KEY_9. */
  static final int MAX_KEYS_PER_PROVIDER = 9;

  private static final Logger log = LoggerFactory.getLogger(AiProviderConfig.class);

  @Bean
  public AiProvider aiProvider(
      AiProperties properties, AiRoutingProperties routing, Environment environment, Clock clock) {
    String choice = properties.provider().strip().toLowerCase(Locale.ROOT);
    return switch (choice) {
      case "mock" -> new MockAiProvider();
      case "gemini" -> {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
          throw new IllegalStateException(
              "AI_PROVIDER is 'gemini', but AI_API_KEY is empty. Set the key in the environment, or"
                  + " set AI_PROVIDER=mock.");
        }
        yield new GeminiAiProvider(
            properties.apiKey().strip(), properties.model(), properties.connectTimeout());
      }
      case "router" -> router(properties, routing, environment::getProperty, clock);
      default ->
          throw new IllegalStateException(
              "AI_PROVIDER is set to '"
                  + properties.provider()
                  + "', which is not 'mock', 'gemini' or 'router'. Set AI_PROVIDER to one of them.");
    };
  }

  /** Reads a setting by name; the Environment in the application, a map in tests. */
  interface Settings {
    String get(String name);
  }

  static RoutingAiProvider router(
      AiProperties properties, AiRoutingProperties routing, Settings settings, Clock clock) {
    List<RoutingAiProvider.Route> routes = new ArrayList<>();
    for (String entry : entries(routing.routes())) {
      int colon = entry.indexOf(':');
      if (colon <= 0 || colon == entry.length() - 1) {
        throw new IllegalStateException(
            "AI_ROUTES has an entry that is not 'provider:model': '" + entry + "'.");
      }
      String provider = entry.substring(0, colon).strip().toLowerCase(Locale.ROOT);
      String model = entry.substring(colon + 1).strip();
      if (!provider.equals("gemini") && !OPENAI_COMPATIBLE.containsKey(provider)) {
        throw new IllegalStateException(
            "AI_ROUTES names the provider '"
                + provider
                + "', which is not one of gemini, groq, openrouter, ollama.");
      }
      List<Integer> keyNumbers = new ArrayList<>();
      for (int number = 1; number <= MAX_KEYS_PER_PROVIDER; number++) {
        String key = settings.get(keyName(provider, number));
        if (key == null || key.isBlank()) {
          continue;
        }
        keyNumbers.add(number);
        routes.add(
            new RoutingAiProvider.Route(
                provider, model, number, adapter(provider, key.strip(), model, properties)));
      }
      if (keyNumbers.isEmpty()) {
        log.warn("AI route {}:{} is skipped: no {} is set", provider, model, keyName(provider, 1));
      }
    }
    if (routes.isEmpty()) {
      throw new IllegalStateException(
          "AI_PROVIDER is 'router', but AI_ROUTES gives no model whose provider has a key. Set"
              + " AI_ROUTES and the providers' keys, or set AI_PROVIDER=mock.");
    }
    Duration budget = properties.totalBudget();
    log.info(
        "AI router: {} routes, at most {} attempts per request within {}s",
        routes.size(),
        routing.maxAttempts(),
        budget.toSeconds());
    return new RoutingAiProvider(
        routes, routing.maxAttempts(), routing.attemptTimeout(), budget, clock);
  }

  /** The environment variable holding a provider's key: GROQ_API_KEY, GROQ_API_KEY_2, ... */
  static String keyName(String provider, int number) {
    String base = provider.toUpperCase(Locale.ROOT) + "_API_KEY";
    return number == 1 ? base : base + "_" + number;
  }

  private static List<String> entries(String routes) {
    List<String> entries = new ArrayList<>();
    if (routes == null) {
      return entries;
    }
    for (String entry : routes.split(",")) {
      if (!entry.isBlank()) {
        entries.add(entry.strip());
      }
    }
    return entries;
  }

  private static AiProvider adapter(
      String provider, String key, String model, AiProperties properties) {
    if (provider.equals("gemini")) {
      return new GeminiAiProvider(key, model, properties.connectTimeout());
    }
    return new OpenAiCompatibleAiProvider(
        OPENAI_COMPATIBLE.get(provider), key, model, properties.connectTimeout());
  }
}
