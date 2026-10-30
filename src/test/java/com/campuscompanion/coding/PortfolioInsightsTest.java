package com.campuscompanion.coding;

import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.coding.dto.ActivitySummary;
import com.campuscompanion.coding.dto.ProblemSolvingInsights;
import com.campuscompanion.coding.dto.ProblemSolvingInsights.Count;
import com.campuscompanion.coding.platform.CodingPlatform;
import com.campuscompanion.coding.platform.PlatformDetails;
import com.campuscompanion.coding.platform.PlatformStats;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PortfolioInsightsTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 30);

  private static PortfolioInsights.Source verified(
      CodingPlatform platform, PlatformStats stats, PlatformDetails details) {
    CodingAccount account = new CodingAccount(1L, platform, "demo", "CC-ABCDEFGH");
    account.verify(Instant.parse("2026-10-01T00:00:00Z"));
    account.apply(stats, null, Instant.parse("2026-10-30T00:00:00Z"));
    return new PortfolioInsights.Source(account, details);
  }

  private static PlatformDetails details(
      Map<String, Integer> calendar, Map<String, Integer> topics, Integer basic) {
    return new PlatformDetails(
        calendar,
        topics,
        List.of(new PlatformDetails.Badge("Gold", 5)),
        Map.of(),
        List.of(),
        basic,
        null,
        null,
        null);
  }

  @Test
  void streaksCountDaysInARowAndTheCurrentOneMayEndYesterday() {
    ActivitySummary summary =
        PortfolioInsights.summarise(
            Map.of(
                "2026-10-20",
                1,
                "2026-10-21",
                2,
                "2026-10-22",
                1,
                "2026-10-23",
                0,
                "2026-10-28",
                3,
                "2026-10-29",
                1),
            TODAY);
    assertThat(summary).isEqualTo(new ActivitySummary(8, 5, 3, 2));
    assertThat(PortfolioInsights.summarise(Map.of("2026-10-30", 1), TODAY).currentStreak())
        .isEqualTo(1);
    assertThat(PortfolioInsights.summarise(Map.of("2026-10-27", 1), TODAY).currentStreak())
        .isZero();
  }

  @Test
  void sitesAreAddedTogetherByKindWithTopicsMergedAcrossNames() {
    ProblemSolvingInsights insights =
        PortfolioInsights.problemSolving(
            List.of(
                verified(
                    CodingPlatform.LEETCODE,
                    new PlatformStats(100, 60, 30, 10, 1600, null, 5, null, null, null, null, null),
                    details(
                        Map.of("2026-10-29", 2),
                        Map.of("Array", 40, "Dynamic Programming", 5),
                        null)),
                verified(
                    CodingPlatform.GEEKSFORGEEKS,
                    new PlatformStats(
                        39, 19, 6, null, null, null, null, null, 73, null, null, null),
                    details(Map.of("2026-10-29", 1, "2026-10-30", 1), Map.of(), 14)),
                verified(
                    CodingPlatform.CODEFORCES,
                    PlatformStats.solved(20),
                    details(Map.of(), Map.of("Dp", 3, "Math", 2), null)),
                verified(
                    CodingPlatform.GITHUB,
                    PlatformStats.solved(null),
                    details(Map.of("2026-10-30", 9), Map.of(), null))),
            TODAY);

    assertThat(insights.questionsSolved()).isEqualTo(159);
    assertThat(insights.calendar()).containsEntry("2026-10-29", 3).containsEntry("2026-10-30", 1);
    assertThat(insights.activity()).isEqualTo(new ActivitySummary(4, 2, 2, 2));
    assertThat(insights.topics())
        .containsExactly(
            new Count("Arrays", 40), new Count("Dynamic Programming", 8), new Count("Math", 2));
    assertThat(insights.distribution().easy()).isEqualTo(79);
    assertThat(insights.distribution().medium()).isEqualTo(36);
    assertThat(insights.distribution().hard()).isEqualTo(10);
    assertThat(insights.distribution().fundamentals())
        .containsExactly(new Count("GeeksforGeeks", 14));
    assertThat(insights.distribution().competitive()).containsExactly(new Count("Codeforces", 20));
    assertThat(insights.awards()).hasSize(3);
    assertThat(insights.contests()).hasSize(1);
  }

  @Test
  void languageSharesAddUpToAHundred() {
    assertThat(
            PortfolioInsights.percentages(Map.of("TypeScript", 5, "HTML", 5, "Java", 1))
                .values()
                .stream()
                .mapToInt(Integer::intValue)
                .sum())
        .isEqualTo(100);
    assertThat(PortfolioInsights.percentages(Map.of())).isEmpty();
  }
}
