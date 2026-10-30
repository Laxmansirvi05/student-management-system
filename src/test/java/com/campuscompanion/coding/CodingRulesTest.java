package com.campuscompanion.coding;

import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.coding.CodingRules.DayCount;
import com.campuscompanion.coding.dto.JourneyPoint;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CodingRulesTest {

  private static final LocalDate DAY = LocalDate.of(2026, 10, 1);

  @Test
  void equalTotalsShareARankAndTheNextRankSkips() {
    assertThat(CodingRules.ranks(List.of(30, 20, 20, 10))).containsExactly(1, 2, 2, 4);
    assertThat(CodingRules.ranks(List.of(5, 5, 5))).containsExactly(1, 1, 1);
    assertThat(CodingRules.ranks(List.of())).isEmpty();
  }

  @Test
  void theJourneyAddsEachAccountsLatestCountOnEveryDay() {
    List<JourneyPoint> journey =
        CodingRules.journey(
            List.of(
                new DayCount(1L, DAY, 10),
                new DayCount(2L, DAY.plusDays(2), 5),
                new DayCount(1L, DAY.plusDays(3), 14),
                new DayCount(2L, DAY.plusDays(3), 6)));

    assertThat(journey)
        .containsExactly(
            new JourneyPoint(DAY, 10),
            new JourneyPoint(DAY.plusDays(2), 15),
            new JourneyPoint(DAY.plusDays(3), 20));
    assertThat(CodingRules.journey(List.of())).isEmpty();
  }

  @Test
  void solvedSinceIsNeverNegative() {
    assertThat(CodingRules.solvedSince(50, 40)).isEqualTo(10);
    assertThat(CodingRules.solvedSince(40, 50)).isZero();
    assertThat(CodingRules.solvedSince(40, null)).isZero();
  }

  @Test
  void theWaitIsWhatIsLeftOfTheCooldown() {
    Instant now = Instant.parse("2026-10-30T10:00:00Z");
    Duration cooldown = Duration.ofSeconds(20);
    assertThat(CodingRules.waitLeft(null, cooldown, now)).isZero();
    assertThat(CodingRules.waitLeft(now.minusSeconds(5), cooldown, now))
        .isEqualTo(Duration.ofSeconds(15));
    assertThat(CodingRules.waitLeft(now.minusSeconds(60), cooldown, now)).isZero();
  }
}
