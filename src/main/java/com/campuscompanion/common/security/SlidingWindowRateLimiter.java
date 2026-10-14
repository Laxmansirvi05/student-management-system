package com.campuscompanion.common.security;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Limits how many requests one key may make in a sliding time window. The login limit uses the IP
 * address as the key, and the general API limit uses the session. It is kept in memory, which is
 * enough while the application runs as a single process.
 */
public class SlidingWindowRateLimiter {

  /** Above this many tracked keys, those with no recent attempt are forgotten. */
  private static final int CLEANUP_THRESHOLD = 1_000;

  private final int maxAttempts;
  private final Duration window;
  private final Map<String, Deque<Instant>> attemptsByKey = new HashMap<>();

  public SlidingWindowRateLimiter(int maxAttempts, Duration window) {
    this.maxAttempts = maxAttempts;
    this.window = window;
  }

  /**
   * Records an attempt if the key is still within its limit.
   *
   * @return true if the attempt may go ahead, false if the limit has been reached
   */
  public synchronized boolean tryAcquire(String key, Instant now) {
    if (attemptsByKey.size() >= CLEANUP_THRESHOLD) {
      forgetIdleKeys(now);
    }
    Deque<Instant> attempts = attemptsByKey.computeIfAbsent(key, k -> new ArrayDeque<>());
    dropExpired(attempts, now);
    if (attempts.size() >= maxAttempts) {
      return false;
    }
    attempts.addLast(now);
    return true;
  }

  /** How long until the key may try again: the time until its oldest counted attempt expires. */
  public synchronized Duration retryAfter(String key, Instant now) {
    Deque<Instant> attempts = attemptsByKey.get(key);
    if (attempts == null || attempts.isEmpty()) {
      return Duration.ZERO;
    }
    Duration remaining = Duration.between(now, attempts.peekFirst().plus(window));
    return remaining.isNegative() ? Duration.ZERO : remaining;
  }

  synchronized int trackedKeys() {
    return attemptsByKey.size();
  }

  /** Drops keys with no attempt left inside the window, so the map cannot grow forever. */
  private void forgetIdleKeys(Instant now) {
    attemptsByKey
        .values()
        .removeIf(
            attempts -> {
              dropExpired(attempts, now);
              return attempts.isEmpty();
            });
  }

  private void dropExpired(Deque<Instant> attempts, Instant now) {
    Instant cutoff = now.minus(window);
    while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(cutoff)) {
      attempts.removeFirst();
    }
  }
}
