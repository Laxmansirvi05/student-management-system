package com.campuscompanion.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * The application clock gives whole microseconds. PostgreSQL keeps timestamps to the microsecond,
 * while a Linux clock gives nanoseconds; a time returned before saving would otherwise differ from
 * the same time read back (this broke CI on every push from 2026-10-23).
 */
class ClockConfigTest {

  @Test
  void theClockHasNoDigitsBeyondTheMicrosecond() {
    Clock clock = new ClockConfig().clock();
    for (int i = 0; i < 1000; i++) {
      Instant now = clock.instant();
      assertThat(now.getNano() % 1000).isZero();
    }
  }

  @Test
  void theClockIsUtcAndCurrent() {
    Clock clock = new ClockConfig().clock();
    assertThat(clock.getZone().getId()).isEqualTo("Z");
    assertThat(clock.instant())
        .isBetween(Instant.now().minusSeconds(5), Instant.now().plusSeconds(5));
  }
}
