package com.campuscompanion.common.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Limits on ordinary API calls: how many one session may make in a time window, and how large a
 * JSON request body may be. Login and file uploads have limits of their own.
 */
@Validated
@ConfigurationProperties(prefix = "app.security.api")
public record ApiProtectionProperties(
    @Min(1) int maxRequestsPerSession, @NotNull Duration window, @NotNull DataSize maxJsonBody) {}
