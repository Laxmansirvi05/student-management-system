package com.campuscompanion.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Task T1-07: the lockout rule, for an account and for an email that has no account. Time is passed
 * in, so the 15-minute block is tested without waiting.
 */
class LoginLockoutTest {

  private static final int MAX_FAILURES = 5;
  private static final Duration LOCK = Duration.ofMinutes(15);
  private static final Instant START = Instant.parse("2026-10-13T06:00:00Z");

  @Test
  void anAccountIsBlockedOnTheFifthConsecutiveFailure() {
    User user = new User("lock@university.example", "hash", Role.STUDENT, "Lock Test");

    for (int failure = 1; failure <= 4; failure++) {
      user.recordFailedLogin(START, MAX_FAILURES, LOCK);
      assertThat(user.isLocked(START)).as("after failure %s", failure).isFalse();
    }
    user.recordFailedLogin(START, MAX_FAILURES, LOCK);

    assertThat(user.isLocked(START)).isTrue();
    assertThat(user.getLockedUntil()).isEqualTo(START.plus(LOCK));
  }

  @Test
  void theBlockEndsAfterFifteenMinutesAndTheNextBlockNeedsFiveMoreFailures() {
    User user = new User("lock@university.example", "hash", Role.STUDENT, "Lock Test");
    for (int failure = 1; failure <= 5; failure++) {
      user.recordFailedLogin(START, MAX_FAILURES, LOCK);
    }

    assertThat(user.isLocked(START.plus(LOCK).minusSeconds(1))).isTrue();
    assertThat(user.isLocked(START.plus(LOCK))).isFalse();

    Instant later = START.plus(LOCK).plusSeconds(60);
    for (int failure = 1; failure <= 4; failure++) {
      user.recordFailedLogin(later, MAX_FAILURES, LOCK);
    }
    assertThat(user.isLocked(later)).isFalse();
    user.recordFailedLogin(later, MAX_FAILURES, LOCK);
    assertThat(user.isLocked(later)).isTrue();
  }

  @Test
  void aSuccessfulLoginResetsTheCounterAndClearsABlock() {
    User user = new User("lock@university.example", "hash", Role.STUDENT, "Lock Test");
    for (int failure = 1; failure <= 4; failure++) {
      user.recordFailedLogin(START, MAX_FAILURES, LOCK);
    }

    user.recordSuccessfulLogin(START);

    assertThat(user.getFailedLoginCount()).isZero();
    assertThat(user.getLastLoginAt()).isEqualTo(START);
    for (int failure = 1; failure <= 4; failure++) {
      user.recordFailedLogin(START, MAX_FAILURES, LOCK);
    }
    assertThat(user.isLocked(START)).isFalse();
  }

  @Test
  void anEmailWithNoAccountIsBlockedInExactlyTheSameWay() {
    UnknownEmailAttempts attempts = new UnknownEmailAttempts();
    String email = "nobody@university.example";

    for (int failure = 1; failure <= 4; failure++) {
      attempts.recordFailure(email, START, MAX_FAILURES, LOCK);
      assertThat(attempts.isBlocked(email, START)).as("after failure %s", failure).isFalse();
    }
    attempts.recordFailure(email, START, MAX_FAILURES, LOCK);

    assertThat(attempts.isBlocked(email, START)).isTrue();
    assertThat(attempts.isBlocked(email, START.plus(LOCK).minusSeconds(1))).isTrue();
    assertThat(attempts.isBlocked(email, START.plus(LOCK))).isFalse();
    assertThat(attempts.isBlocked("someone.else@university.example", START)).isFalse();
  }

  @Test
  void oldEntriesForUnknownEmailsAreDroppedSoMemoryCannotGrowWithoutLimit() {
    UnknownEmailAttempts attempts = new UnknownEmailAttempts();
    for (int i = 0; i < 1_000; i++) {
      attempts.recordFailure("flood" + i + "@university.example", START, MAX_FAILURES, LOCK);
    }
    assertThat(attempts.size()).isEqualTo(1_000);

    attempts.recordFailure(
        "later@university.example", START.plus(LOCK).plusSeconds(1), MAX_FAILURES, LOCK);

    assertThat(attempts.size()).isEqualTo(1);
  }
}
