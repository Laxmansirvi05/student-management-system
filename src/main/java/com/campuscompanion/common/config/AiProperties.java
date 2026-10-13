package com.campuscompanion.common.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** AI study assistant settings. Used from task T5-01. The API key is a server-side secret. */
@Validated
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
    boolean enabled,
    @NotBlank String provider,
    String apiKey,
    String model,
    @Min(0) int dailyLimitPerUser,
    @Min(0) int dailyLimitGlobal,
    @Min(1) int maxOutputTokens) {

  @Override
  public String toString() {
    return "AiProperties[enabled=" + enabled + ", provider=" + provider + ", model=" + model + "]";
  }
}
