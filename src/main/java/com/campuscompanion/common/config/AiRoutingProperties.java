package com.campuscompanion.common.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * How the AI router chooses a model when AI_PROVIDER is "router" (docs/12 section 2b).
 *
 * @param routes AI_ROUTES: "provider:model" entries in priority order, separated by commas, for
 *     example "groq:openai/gpt-oss-120b,ollama:gpt-oss:20b". The keys are not here: each provider's
 *     keys are read from its own variables (GROQ_API_KEY, GROQ_API_KEY_2, ...).
 * @param maxAttempts the most calls one student request may cause, fallbacks included
 * @param attemptTimeout the longest one call may take, so that a slow model leaves time for the
 *     next one within the overall budget (app.ai.total-budget)
 */
@Validated
@ConfigurationProperties(prefix = "app.ai.routing")
public record AiRoutingProperties(
    String routes, @Min(1) @Max(5) int maxAttempts, @NotNull Duration attemptTimeout) {}
