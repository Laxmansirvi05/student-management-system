package com.campuscompanion.common.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The clean-up of the AI usage log (T7-09, docs/14). Off unless switched on, so that no row is
 * deleted from a database until its owner decides.
 *
 * @param enabled AI_LOG_RETENTION_ENABLED
 * @param days AI_LOG_RETENTION_DAYS: rows older than this are removed (at least 30)
 * @param batchSize rows per delete; each batch is its own short transaction
 * @param maxBatches the most batches in one run, so a first run on a large table stays bounded
 */
@Validated
@ConfigurationProperties(prefix = "app.ai.log-retention")
public record AiLogRetentionProperties(
    boolean enabled,
    @Min(30) @Max(3650) int days,
    @Min(1) @Max(10000) int batchSize,
    @Min(1) @Max(1000) int maxBatches) {}
