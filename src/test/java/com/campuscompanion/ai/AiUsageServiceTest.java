package com.campuscompanion.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campuscompanion.ai.dto.AiUsageResponse;
import com.campuscompanion.common.config.AiProperties;
import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import com.campuscompanion.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * The limits of the AI assistant, one at a time and in their order (docs/12-AI-features-spec.md
 * section 2). The usage log is a mock: these tests are about the rules, and the counting queries
 * are checked against a real database in AiApiIT.
 */
class AiUsageServiceTest {

  private static final long USER = 7L;
  private static final long OTHER_USER = 8L;

  /** 10:00 in India on 10 October 2026. The day there started at 18:30 UTC the day before. */
  private static final Instant NOW = Instant.parse("2026-10-10T04:30:00Z");

  private static final Instant DAY_START = Instant.parse("2026-10-09T18:30:00Z");

  private final AiRequestLogRepository logs = mock(AiRequestLogRepository.class);
  private final MutableClock clock = new MutableClock(NOW);

  private AiUsageService service(boolean enabled, int perUser, int global) {
    AiProperties properties =
        new AiProperties(
            enabled,
            "mock",
            "",
            "",
            perUser,
            global,
            1200,
            5,
            2,
            Duration.ofSeconds(5),
            Duration.ofSeconds(20),
            Duration.ofSeconds(25));
    return new AiUsageService(logs, properties, clock);
  }

  private void usedToday(long byUser, long byEveryone) {
    when(logs.countByUserIdAndStatusInAndCreatedAtGreaterThanEqual(
            eq(USER), anyCollection(), any()))
        .thenReturn(byUser);
    when(logs.countByStatusInAndCreatedAtGreaterThanEqual(anyCollection(), any()))
        .thenReturn(byEveryone);
  }

  private static void assertRefused(Runnable call, ErrorCode code, String message) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            ApiException.class,
            failure -> {
              assertThat(failure.getCode()).isEqualTo(code);
              assertThat(failure.getMessage()).isEqualTo(message);
            });
  }

  @Test
  void aRequestWithinEveryLimitGoesAhead() {
    usedToday(29, 499);

    assertThatCode(() -> service(true, 30, 500).begin(USER)).doesNotThrowAnyException();
  }

  @Test
  void theKillSwitchStopsEverythingBeforeAnythingIsCounted() {
    assertRefused(
        () -> service(false, 30, 500).begin(USER),
        ErrorCode.AI_UNAVAILABLE,
        AiUsageService.SWITCHED_OFF);

    verify(logs, never()).countByStatusInAndCreatedAtGreaterThanEqual(anyCollection(), any());
  }

  @Test
  void theCeilingForEveryoneStopsAStudentWhoHasUsedNothing() {
    usedToday(0, 500);

    assertRefused(
        () -> service(true, 30, 500).begin(USER),
        ErrorCode.AI_UNAVAILABLE,
        AiUsageService.CEILING_REACHED);
  }

  @Test
  void theCeilingIsCheckedBeforeTheStudentsOwnLimit() {
    usedToday(30, 500);

    assertRefused(
        () -> service(true, 30, 500).begin(USER),
        ErrorCode.AI_UNAVAILABLE,
        AiUsageService.CEILING_REACHED);
  }

  @Test
  void theStudentsOwnDailyLimitStopsOnlyThatStudent() {
    usedToday(30, 30);
    AiUsageService service = service(true, 30, 500);

    assertRefused(
        () -> service.begin(USER), ErrorCode.AI_QUOTA_EXCEEDED, AiUsageService.QUOTA_REACHED);
    assertThatCode(() -> service.begin(OTHER_USER)).doesNotThrowAnyException();
  }

  @Test
  void theDailyCountsStartAtMidnightInIndia() {
    usedToday(0, 0);

    service(true, 30, 500).begin(USER);

    verify(logs).countByStatusInAndCreatedAtGreaterThanEqual(anyCollection(), eq(DAY_START));
    verify(logs)
        .countByUserIdAndStatusInAndCreatedAtGreaterThanEqual(
            eq(USER), anyCollection(), eq(DAY_START));
  }

  @Test
  void theSixthRequestInAMinuteIsRefusedAndTheNextMinuteIsFreeAgain() {
    usedToday(0, 0);
    AiUsageService service = service(true, 30, 500);
    for (int i = 0; i < 5; i++) {
      service.begin(USER);
      service.end(USER);
      clock.advance(Duration.ofSeconds(5));
    }

    assertRefused(() -> service.begin(USER), ErrorCode.RATE_LIMITED, AiUsageService.TOO_FAST);
    // Another student is not held back by this one.
    assertThatCode(() -> service.begin(OTHER_USER)).doesNotThrowAnyException();

    // 61 seconds after the first request, one place is free again.
    clock.advance(Duration.ofSeconds(36));
    assertThatCode(() -> service.begin(USER)).doesNotThrowAnyException();
  }

  @Test
  void aThirdRequestAtTheSameTimeIsRefusedUntilOneEnds() {
    usedToday(0, 0);
    AiUsageService service = service(true, 30, 500);
    service.begin(USER);
    service.begin(USER);

    assertRefused(() -> service.begin(USER), ErrorCode.RATE_LIMITED, AiUsageService.TOO_FAST);

    service.end(USER);
    assertThatCode(() -> service.begin(USER)).doesNotThrowAnyException();
  }

  @Test
  void aRequestRefusedByADailyLimitDoesNotTakeAPlace() {
    usedToday(30, 30);
    AiUsageService service = service(true, 30, 500);
    for (int i = 0; i < 10; i++) {
      assertThatThrownBy(() -> service.begin(USER)).isInstanceOf(AiQuotaExceededException.class);
    }

    // The next day the student can ask at once: none of the refusals was counted as a request.
    usedToday(0, 0);
    assertThatCode(() -> service.begin(USER)).doesNotThrowAnyException();
  }

  @Test
  void usageShowsWhatIsLeftAndWhenTheDayEnds() {
    usedToday(4, 4);

    AiUsageResponse usage = service(true, 30, 500).usageFor(USER);

    assertThat(usage.dailyLimit()).isEqualTo(30);
    assertThat(usage.usedToday()).isEqualTo(4);
    assertThat(usage.remainingToday()).isEqualTo(26);
    assertThat(usage.resetsAt()).isEqualTo(Instant.parse("2026-10-10T18:30:00Z"));
    assertThat(usage.enabled()).isTrue();
    assertThat(usage.demo()).isTrue();
  }

  @Test
  void usageSaysWhenTheAssistantIsOffOrTheCeilingIsReached() {
    usedToday(4, 500);
    assertThat(service(true, 30, 500).usageFor(USER).enabled()).isFalse();

    usedToday(4, 4);
    assertThat(service(false, 30, 500).usageFor(USER).enabled()).isFalse();
  }

  @Test
  void onlySuccessAndDeclineCountTowardTheLimits() {
    assertThat(AiRequestStatus.SUCCESS.countsTowardLimits()).isTrue();
    assertThat(AiRequestStatus.DECLINED.countsTowardLimits()).isTrue();
    assertThat(AiRequestStatus.FAILED.countsTowardLimits()).isFalse();
    assertThat(AiRequestStatus.TIMEOUT.countsTowardLimits()).isFalse();
  }
}
