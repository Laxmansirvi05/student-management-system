package com.campuscompanion.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Counts failed logins for email addresses that have no account. Real accounts keep their counter
 * in the database; an unknown email has no row to count in, so it is tracked here in memory with
 * the same limits. The effect is that the "too many attempts" response is the same whether or not
 * an account exists, and so reveals nothing.
 */
public class UnknownEmailAttempts {

  /** Above this many tracked emails, entries that no longer matter are dropped. */
  private static final int CLEANUP_THRESHOLD = 1_000;

  private final Map<String, Entry> entries = new HashMap<>();

  public synchronized boolean isBlocked(String email, Instant now) {
    Entry entry = entries.get(email);
    return entry != null && entry.blockedUntil != null && entry.blockedUntil.isAfter(now);
  }

  public synchronized void recordFailure(
      String email, Instant now, int maxFailedAttempts, Duration lockDuration) {
    if (entries.size() >= CLEANUP_THRESHOLD) {
      removeStale(now, lockDuration);
    }
    Entry entry = entries.computeIfAbsent(email, key -> new Entry());
    entry.lastFailure = now;
    entry.failures++;
    if (entry.failures >= maxFailedAttempts) {
      entry.blockedUntil = now.plus(lockDuration);
      entry.failures = 0;
    }
  }

  synchronized int size() {
    return entries.size();
  }

  /** Drops entries whose block has ended and whose last failure is older than one lock period. */
  private void removeStale(Instant now, Duration lockDuration) {
    Instant cutoff = now.minus(lockDuration);
    entries
        .values()
        .removeIf(
            entry ->
                entry.lastFailure.isBefore(cutoff)
                    && (entry.blockedUntil == null || !entry.blockedUntil.isAfter(now)));
  }

  private static final class Entry {
    private int failures;
    private Instant lastFailure;
    private Instant blockedUntil;
  }
}
