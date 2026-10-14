package com.campuscompanion.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Task T1-07: at most 20 login attempts per IP address in any 10 minutes. */
class SlidingWindowRateLimiterTest {

  private static final Instant START = Instant.parse("2026-10-13T06:00:00Z");

  private final SlidingWindowRateLimiter limiter =
      new SlidingWindowRateLimiter(20, Duration.ofMinutes(10));

  @Test
  void allowsTwentyAttemptsAndRefusesTheTwentyFirst() {
    for (int attempt = 1; attempt <= 20; attempt++) {
      assertThat(limiter.tryAcquire("10.0.0.1", START)).as("attempt %s", attempt).isTrue();
    }

    assertThat(limiter.tryAcquire("10.0.0.1", START)).isFalse();
    assertThat(limiter.retryAfter("10.0.0.1", START)).isEqualTo(Duration.ofMinutes(10));
  }

  @Test
  void countsEachAddressSeparately() {
    for (int attempt = 1; attempt <= 20; attempt++) {
      limiter.tryAcquire("10.0.0.1", START);
    }

    assertThat(limiter.tryAcquire("10.0.0.2", START)).isTrue();
  }

  @Test
  void attemptsStopCountingOnceTheyAreOlderThanTheWindow() {
    for (int attempt = 1; attempt <= 20; attempt++) {
      limiter.tryAcquire("10.0.0.1", START.plusSeconds(attempt));
    }
    Instant justBefore = START.plusSeconds(1).plus(Duration.ofMinutes(10)).minusSeconds(1);
    Instant justAfter = START.plusSeconds(1).plus(Duration.ofMinutes(10));

    assertThat(limiter.tryAcquire("10.0.0.1", justBefore)).isFalse();
    assertThat(limiter.retryAfter("10.0.0.1", justBefore)).isEqualTo(Duration.ofSeconds(1));
    assertThat(limiter.tryAcquire("10.0.0.1", justAfter)).isTrue();
    assertThat(limiter.tryAcquire("10.0.0.1", justAfter)).isFalse();
  }

  @Test
  void addressesWithNoRecentAttemptAreForgotten() {
    for (int address = 0; address < 1_000; address++) {
      limiter.tryAcquire("10.1." + (address / 250) + "." + (address % 250), START);
    }
    assertThat(limiter.trackedKeys()).isEqualTo(1_000);

    limiter.tryAcquire("10.9.9.9", START.plus(Duration.ofMinutes(10)).plusSeconds(1));

    assertThat(limiter.trackedKeys()).isEqualTo(1);
  }

  @Test
  void anAddressWithNoAttemptsNeedNotWait() {
    assertThat(limiter.retryAfter("10.0.0.9", START)).isEqualTo(Duration.ZERO);
  }
}
