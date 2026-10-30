package com.campuscompanion.coding.platform;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The richer public data some sites give, for the portfolio's views: activity by day, solved
 * problems by topic, badges, languages and projects. Every part may be empty. Names are trimmed
 * labels and the sizes are capped, so a site can never fill the database or the page.
 *
 * @param calendar submissions (or GitHub contributions) by ISO date, for the last year
 * @param topics solved problems by topic name
 * @param badges the badges the site awarded
 * @param languages a count per language (problems solved, or GitHub repositories)
 * @param projects GitHub repositories worth showing
 * @param basicSolved problems of GeeksforGeeks' School and Basic levels
 * @param commits GitHub commits found by GitHub's search, when it answered
 * @param pullRequests GitHub pull requests opened, when the search answered
 * @param issues GitHub issues opened, when the search answered
 */
public record PlatformDetails(
    Map<String, Integer> calendar,
    Map<String, Integer> topics,
    List<Badge> badges,
    Map<String, Integer> languages,
    List<Project> projects,
    Integer basicSolved,
    Integer commits,
    Integer pullRequests,
    Integer issues) {

  static final int MAX_DAYS = 370;
  static final int MAX_TOPICS = 60;
  static final int MAX_BADGES = 30;
  static final int MAX_LANGUAGES = 20;
  static final int MAX_PROJECTS = 6;

  public static final PlatformDetails EMPTY =
      new PlatformDetails(
          Map.of(), Map.of(), List.of(), Map.of(), List.of(), null, null, null, null);

  /**
   * A badge: its name, its level as stars where the site has one (1 to 7), the address of its
   * picture on the site, and the key of the copy this application keeps of that picture (null until
   * copied).
   */
  public record Badge(String name, Integer stars, String source, String image) {

    public Badge(String name, Integer stars) {
      this(name, stars, null, null);
    }

    public Badge withImage(String key) {
      return new Badge(name, stars, source, key);
    }
  }

  /** The same details with other badges. */
  public PlatformDetails withBadges(List<Badge> others) {
    return new PlatformDetails(
        calendar, topics, others, languages, projects, basicSolved, commits, pullRequests, issues);
  }

  /** A public repository: the address is built from the handle and the name, never taken. */
  public record Project(
      String name, String url, String description, String language, Integer stars) {}

  public PlatformDetails {
    calendar = calendar == null ? Map.of() : capped(calendar, MAX_DAYS);
    topics = topics == null ? Map.of() : capped(topics, MAX_TOPICS);
    badges = badges == null ? List.of() : badges.stream().limit(MAX_BADGES).toList();
    languages = languages == null ? Map.of() : capped(languages, MAX_LANGUAGES);
    projects = projects == null ? List.of() : projects.stream().limit(MAX_PROJECTS).toList();
  }

  private static Map<String, Integer> capped(Map<String, Integer> source, int limit) {
    Map<String, Integer> kept = new LinkedHashMap<>();
    source.forEach(
        (key, value) -> {
          if (kept.size() < limit && key != null && value != null && value >= 0) {
            kept.put(key, value);
          }
        });
    return Map.copyOf(kept);
  }
}
