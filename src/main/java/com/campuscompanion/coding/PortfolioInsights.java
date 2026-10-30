package com.campuscompanion.coding;

import com.campuscompanion.coding.dto.ActivitySummary;
import com.campuscompanion.coding.dto.DevelopmentInsights;
import com.campuscompanion.coding.dto.ProblemSolvingInsights;
import com.campuscompanion.coding.dto.ProblemSolvingInsights.Award;
import com.campuscompanion.coding.dto.ProblemSolvingInsights.Contest;
import com.campuscompanion.coding.dto.ProblemSolvingInsights.Count;
import com.campuscompanion.coding.dto.ProblemSolvingInsights.Distribution;
import com.campuscompanion.coding.platform.CodingPlatform;
import com.campuscompanion.coding.platform.PlatformDetails;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The plain rules behind the portfolio's views (FR-COD-002): calendars merged and added up into
 * streaks, topics merged across sites, and solved problems split by kind. Only verified accounts
 * are given to it.
 */
public final class PortfolioInsights {

  /** Topic names that mean the same thing on different sites, in lower case. */
  private static final Map<String, String> SAME_TOPIC =
      Map.ofEntries(
          Map.entry("array", "Arrays"),
          Map.entry("arrays", "Arrays"),
          Map.entry("dp", "Dynamic Programming"),
          Map.entry("dynamic programming", "Dynamic Programming"),
          Map.entry("strings", "String"),
          Map.entry("string", "String"),
          Map.entry("sortings", "Sorting"),
          Map.entry("sorting", "Sorting"),
          Map.entry("hashing", "HashMap and Set"),
          Map.entry("hash table", "HashMap and Set"),
          Map.entry("math", "Math"),
          Map.entry("maths", "Math"),
          Map.entry("graphs", "Graph"),
          Map.entry("graph", "Graph"),
          Map.entry("trees", "Tree"),
          Map.entry("tree", "Tree"),
          Map.entry("greedy", "Greedy"),
          Map.entry("binary search", "Binary Search"),
          Map.entry("two pointers", "Two Pointers"),
          Map.entry("bitmasks", "Bit Manipulation"),
          Map.entry("bit manipulation", "Bit Manipulation"),
          Map.entry("number theory", "Number Theory"),
          Map.entry("implementation", "Implementation"),
          Map.entry("brute force", "Brute Force"));

  private PortfolioInsights() {}

  /** One verified account and its stored details. */
  public record Source(CodingAccount account, PlatformDetails details) {}

  public static ProblemSolvingInsights problemSolving(List<Source> verified, LocalDate today) {
    List<Source> counted =
        verified.stream()
            .filter(source -> source.account().getPlatform().countsProblems())
            .toList();
    Map<String, Integer> calendar = new TreeMap<>();
    Map<String, Integer> topicCounts = new HashMap<>();
    Map<String, String> topicNames = new HashMap<>();
    List<Award> awards = new ArrayList<>();
    List<Count> fundamentals = new ArrayList<>();
    List<Count> competitive = new ArrayList<>();
    List<Contest> contests = new ArrayList<>();
    int easy = 0;
    int medium = 0;
    int hard = 0;
    for (Source source : counted) {
      CodingAccount account = source.account();
      CodingPlatform platform = account.getPlatform();
      PlatformDetails details = source.details();
      details.calendar().forEach((day, count) -> calendar.merge(day, count, Integer::sum));
      details
          .topics()
          .forEach(
              (name, count) -> {
                String key = SAME_TOPIC.getOrDefault(name.toLowerCase(Locale.ROOT), name);
                topicNames.putIfAbsent(key.toLowerCase(Locale.ROOT), key);
                topicCounts.merge(key.toLowerCase(Locale.ROOT), count, Integer::sum);
              });
      details
          .badges()
          .forEach(badge -> awards.add(new Award(platform, badge.name(), badge.stars())));
      switch (platform) {
        case GEEKSFORGEEKS -> {
          fundamentals.add(new Count(platform.displayName(), orZero(details.basicSolved())));
          easy += orZero(account.getEasySolved());
          medium += orZero(account.getMediumSolved());
          hard += orZero(account.getHardSolved());
        }
        case HACKERRANK ->
            fundamentals.add(new Count(platform.displayName(), account.countedProblems()));
        case LEETCODE -> {
          easy += orZero(account.getEasySolved());
          medium += orZero(account.getMediumSolved());
          hard += orZero(account.getHardSolved());
        }
        case CODEFORCES, CODECHEF ->
            competitive.add(new Count(platform.displayName(), account.countedProblems()));
        default -> {}
      }
      if (account.getContestRating() != null) {
        contests.add(
            new Contest(
                platform,
                account.getContestRating(),
                account.getMaxRating(),
                account.getContestsAttended(),
                account.getRankLabel()));
      }
    }
    List<Count> topics =
        topicCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .map(entry -> new Count(topicNames.get(entry.getKey()), entry.getValue()))
            .toList();
    return new ProblemSolvingInsights(
        counted.stream().mapToInt(source -> source.account().countedProblems()).sum(),
        summarise(calendar, today),
        calendar,
        awards,
        topics,
        new Distribution(fundamentals, easy, medium, hard, competitive),
        contests);
  }

  /** The development view, or null when the student has no verified GitHub account. */
  public static DevelopmentInsights development(List<Source> verified, LocalDate today) {
    return verified.stream()
        .filter(source -> source.account().getPlatform() == CodingPlatform.GITHUB)
        .findFirst()
        .map(source -> development(source, today))
        .orElse(null);
  }

  private static DevelopmentInsights development(Source source, LocalDate today) {
    CodingAccount account = source.account();
    PlatformDetails details = source.details();
    Map<String, Integer> calendar = new TreeMap<>(details.calendar());
    return new DevelopmentInsights(
        account.getHandle(),
        account.getPlatform().profileUrl(account.getHandle()),
        summarise(calendar, today),
        calendar,
        percentages(details.languages()),
        account.getStars(),
        details.commits(),
        details.pullRequests(),
        details.issues(),
        account.getPublicRepos(),
        account.getFollowers(),
        details.projects());
  }

  /**
   * Adds a calendar up. A streak is a run of days in a row with activity; the current streak ends
   * today, or yesterday when nothing has happened yet today.
   */
  public static ActivitySummary summarise(Map<String, Integer> calendar, LocalDate today) {
    TreeMap<LocalDate, Integer> active = new TreeMap<>();
    int total = 0;
    for (Map.Entry<String, Integer> day : calendar.entrySet()) {
      total += day.getValue();
      if (day.getValue() > 0) {
        active.put(LocalDate.parse(day.getKey()), day.getValue());
      }
    }
    int longest = 0;
    int run = 0;
    LocalDate previous = null;
    for (LocalDate day : active.keySet()) {
      run = previous != null && previous.plusDays(1).equals(day) ? run + 1 : 1;
      longest = Math.max(longest, run);
      previous = day;
    }
    LocalDate cursor = active.containsKey(today) ? today : today.minusDays(1);
    int current = 0;
    while (active.containsKey(cursor)) {
      current++;
      cursor = cursor.minusDays(1);
    }
    return new ActivitySummary(total, active.size(), longest, current);
  }

  /** Each part's share in whole percent, largest first; the shares add up to 100. */
  public static Map<String, Integer> percentages(Map<String, Integer> counts) {
    int total = counts.values().stream().mapToInt(Integer::intValue).sum();
    Map<String, Integer> shares = new LinkedHashMap<>();
    if (total == 0) {
      return shares;
    }
    List<Map.Entry<String, Integer>> largestFirst =
        counts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .toList();
    int given = 0;
    for (int i = 0; i < largestFirst.size(); i++) {
      Map.Entry<String, Integer> part = largestFirst.get(i);
      int share =
          i == largestFirst.size() - 1
              ? 100 - given
              : (int) Math.round(part.getValue() * 100.0 / total);
      share = Math.max(0, Math.min(share, 100 - given));
      shares.put(part.getKey(), share);
      given += share;
    }
    return shares;
  }

  private static int orZero(Integer value) {
    return value == null ? 0 : value;
  }
}
