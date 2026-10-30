package com.campuscompanion.coding.dto;

import com.campuscompanion.coding.platform.CodingPlatform;
import java.util.List;
import java.util.Map;

/**
 * The problem-solving view of a portfolio: every verified account that counts problems, together.
 *
 * @param calendar submissions by ISO date across the sites that publish them
 * @param topics solved problems by topic, largest first, names merged across sites
 */
public record ProblemSolvingInsights(
    int questionsSolved,
    ActivitySummary activity,
    Map<String, Integer> calendar,
    List<Award> awards,
    List<Count> topics,
    Distribution distribution,
    List<Contest> contests) {

  /**
   * A badge from one site; stars are its level where the site has one, and image the key of the
   * kept copy of its picture (GET /coding-badges/{image}), or null.
   */
  public record Award(CodingPlatform platform, String name, Integer stars, String image) {}

  /** A named count: a topic, a site's share, a level. */
  public record Count(String name, int count) {}

  /**
   * Solved problems by kind: fundamentals (GeeksforGeeks' School and Basic, HackerRank), DSA by
   * difficulty (LeetCode and GeeksforGeeks), and competitive programming (Codeforces, CodeChef).
   */
  public record Distribution(
      List<Count> fundamentals, int easy, int medium, int hard, List<Count> competitive) {}

  /** A contest rating on one site. */
  public record Contest(
      CodingPlatform platform, Integer rating, Integer maxRating, Integer contests, String rank) {}
}
