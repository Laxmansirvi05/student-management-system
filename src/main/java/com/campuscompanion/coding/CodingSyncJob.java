package com.campuscompanion.coding;

import com.campuscompanion.common.config.CodingProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reads every verified account once a night (FR-COD-003), one at a time with a pause between reads
 * so no site sees a burst, the least recently read first and up to a fixed number per run.
 */
@Component
public class CodingSyncJob {

  private static final Logger log = LoggerFactory.getLogger(CodingSyncJob.class);

  /** An account read within this time, for example by the student's own Refresh, is skipped. */
  private static final Duration FRESH_FOR = Duration.ofHours(20);

  private final CodingAccountRepository accounts;
  private final CodingSync sync;
  private final CodingProperties properties;
  private final Clock clock;

  public CodingSyncJob(
      CodingAccountRepository accounts, CodingSync sync, CodingProperties properties, Clock clock) {
    this.accounts = accounts;
    this.sync = sync;
    this.properties = properties;
    this.clock = clock;
  }

  /** Every night at 02:15, campus time, when switched on. */
  @Scheduled(cron = "0 15 2 * * *", zone = "Asia/Kolkata")
  public void nightly() {
    if (properties.syncEnabled()) {
      run();
    }
  }

  /** Reads the accounts that are due and returns how many were read successfully. */
  public int run() {
    Instant cutoff = clock.instant().minus(FRESH_FOR);
    List<Long> due = accounts.dueForSync(cutoff, PageRequest.of(0, properties.syncMaxAccounts()));
    int read = 0;
    for (Long accountId : due) {
      try {
        if (sync.refresh(accountId)) {
          read++;
        }
      } catch (RuntimeException failure) {
        log.warn("Coding sync failed for account {}: {}", accountId, failure.getClass().getName());
      }
      if (!pause()) {
        break;
      }
    }
    log.info("Coding sync: read {} of {} due accounts", read, due.size());
    return read;
  }

  private boolean pause() {
    try {
      Thread.sleep(properties.syncPause());
      return true;
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      return false;
    }
  }
}
