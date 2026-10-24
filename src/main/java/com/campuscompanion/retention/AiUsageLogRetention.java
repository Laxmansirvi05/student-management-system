package com.campuscompanion.retention;

import com.campuscompanion.ai.AiUsageService;
import com.campuscompanion.audit.AuditAction;
import com.campuscompanion.audit.AuditService;
import com.campuscompanion.audit.AuditSnapshot;
import com.campuscompanion.common.config.AiLogRetentionProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Removes AI usage-log rows older than the retention period (T7-09). The rows hold numbers only
 * (feature, sizes, status, timing), never a prompt or a reply. It runs once a night when
 * AI_LOG_RETENTION_ENABLED is true, in small batches up to a fixed number per run, and records each
 * run that removed rows in the audit log, with the count and the cutoff only.
 *
 * <p>It lives outside the ai package because the AI code may not use the audit log
 * (AiArchitectureTest); it reaches the usage log through {@link AiUsageService}.
 */
@Component
public class AiUsageLogRetention {

  private static final Logger log = LoggerFactory.getLogger(AiUsageLogRetention.class);

  private final AiUsageService usage;
  private final AiLogRetentionProperties properties;
  private final AuditService audit;
  private final TransactionTemplate transactions;
  private final Clock clock;

  public AiUsageLogRetention(
      AiUsageService usage,
      AiLogRetentionProperties properties,
      AuditService audit,
      TransactionTemplate transactions,
      Clock clock) {
    this.usage = usage;
    this.properties = properties;
    this.audit = audit;
    this.transactions = transactions;
    this.clock = clock;
  }

  /** Every night at 03:30, campus time, when switched on. */
  @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Kolkata")
  public void nightly() {
    if (properties.enabled()) {
      purge();
    }
  }

  /** Removes what is older than the retention period and returns how many rows went. */
  public long purge() {
    Instant cutoff = clock.instant().minus(Duration.ofDays(properties.days()));
    long removed = 0;
    for (int batch = 0; batch < properties.maxBatches(); batch++) {
      int deleted = usage.removeLogsOlderThan(cutoff, properties.batchSize());
      removed += deleted;
      if (deleted < properties.batchSize()) {
        break;
      }
    }
    if (removed > 0) {
      long count = removed;
      transactions.executeWithoutResult(
          status ->
              audit.record(
                  AuditAction.AI_USAGE_LOGS_PURGED,
                  "AI_REQUEST_LOGS",
                  (long) properties.days(),
                  null,
                  new AuditSnapshot.UsageLogPurge(count, cutoff.toString())));
    }
    log.info(
        "AI usage log retention: removed {} rows older than {} days", removed, properties.days());
    return removed;
  }
}
