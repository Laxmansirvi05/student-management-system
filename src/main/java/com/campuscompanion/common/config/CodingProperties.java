package com.campuscompanion.common.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The coding portfolio's reads of outside sites (ADR 0017).
 *
 * @param githubToken GITHUB_TOKEN: optional; a token with no scopes raises GitHub's limit of 60
 *     reads an hour
 * @param syncEnabled CODING_SYNC_ENABLED: the nightly refresh of every verified account
 * @param syncMaxAccounts the most accounts one nightly run reads
 * @param syncPause the wait between two accounts in the nightly run, to be polite to the sites
 * @param timeout the most one read of a site may take
 */
@Validated
@ConfigurationProperties(prefix = "app.coding")
public record CodingProperties(
    String githubToken,
    boolean syncEnabled,
    @Min(1) @Max(5000) int syncMaxAccounts,
    @NotNull Duration syncPause,
    @NotNull Duration timeout) {}
