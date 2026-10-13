package com.campuscompanion.common.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Limits on login attempts: how many consecutive failures block an email and for how long, and how
 * many attempts one IP address may make in a time window.
 */
@Validated
@ConfigurationProperties(prefix = "app.security.login")
public record LoginProtectionProperties(
    @Min(1) int maxFailedAttempts,
    @NotNull Duration lockDuration,
    @Min(1) int maxAttemptsPerIp,
    @NotNull Duration ipWindow) {}
