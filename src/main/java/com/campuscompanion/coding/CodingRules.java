package com.campuscompanion.coding;

import com.campuscompanion.coding.dto.JourneyPoint;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** The plain rules of the coding portfolio and leaderboard (FR-COD-002 to FR-COD-005). */
public final class CodingRules {

  private CodingRules() {}

  /** One account's problem count on one day. */
  public record DayCount(Long accountId, LocalDate day, int solved) {}

  /**
   * Ranks for totals sorted from highest to lowest: equal totals share a rank and the next rank
   * skips, so 30, 20, 20, 10 rank 1, 2, 2, 4.
   */
  public static List<Integer> ranks(List<Integer> totalsHighestFirst) {
    List<Integer> ranks = new ArrayList<>(totalsHighestFirst.size());
    for (int i = 0; i < totalsHighestFirst.size(); i++) {
      boolean tied = i > 0 && totalsHighestFirst.get(i).equals(totalsHighestFirst.get(i - 1));
      ranks.add(tied ? ranks.get(i - 1) : i + 1);
    }
    return ranks;
  }

  /**
   * The student's journey: for every day on which any account was read, the total of each account's
   * latest count on or before that day. An account not read yet adds nothing.
   */
  public static List<JourneyPoint> journey(List<DayCount> counts) {
    TreeSet<LocalDate> days = new TreeSet<>();
    counts.forEach(count -> days.add(count.day()));
    List<DayCount> inOrder = counts.stream().sorted((a, b) -> a.day().compareTo(b.day())).toList();
    Map<Long, Integer> latest = new HashMap<>();
    List<JourneyPoint> points = new ArrayList<>();
    int next = 0;
    for (LocalDate day : days) {
      while (next < inOrder.size() && !inOrder.get(next).day().isAfter(day)) {
        latest.put(inOrder.get(next).accountId(), inOrder.get(next).solved());
        next++;
      }
      points.add(new JourneyPoint(day, latest.values().stream().mapToInt(Integer::intValue).sum()));
    }
    return points;
  }

  /** Problems solved since the baseline count; never negative (a site may lower its count). */
  public static int solvedSince(int current, Integer baseline) {
    return baseline == null ? 0 : Math.max(0, current - baseline);
  }

  /** How long until the next attempt is allowed; zero when it is allowed now. */
  public static Duration waitLeft(Instant lastAttempt, Duration cooldown, Instant now) {
    if (lastAttempt == null) {
      return Duration.ZERO;
    }
    Duration left = Duration.between(now, lastAttempt.plus(cooldown));
    return left.isNegative() ? Duration.ZERO : left;
  }
}
