package com.campuscompanion.coding.platform;

import com.campuscompanion.coding.platform.PlatformException.Kind;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * GitHub: the official REST API for the user (bio, name, company, repositories, followers) and the
 * first 100 of their own repositories (stars, languages, the projects shown), the public
 * contributions calendar page for the activity by day, and GitHub's search for the number of
 * commits, pull requests and issues. The search allows few calls without a token, so those three
 * numbers are left empty when it refuses. GitHub has no problem count.
 */
public class GitHubReader implements PlatformReader {

  private static final String API = "https://api.github.com/";
  private static final String CONTRIBUTIONS = "https://github.com/users/";
  private static final Pattern DAY =
      Pattern.compile(
          "data-date=\"(\\d{4}-\\d{2}-\\d{2})\"\\s+id=\"(contribution-day-component-\\d+-\\d+)\"");
  private static final Pattern TIP =
      Pattern.compile(
          "for=\"(contribution-day-component-\\d+-\\d+)\"[^>]*>\\s*(No|[\\d,]+) contributions?");
  private static final Pattern REPO_NAME = Pattern.compile("^[A-Za-z0-9_.-]{1,100}$");

  private final PlatformHttp http;
  private final String token;

  public GitHubReader(PlatformHttp http, String token) {
    this.http = http;
    this.token = token;
  }

  @Override
  public CodingPlatform platform() {
    return CodingPlatform.GITHUB;
  }

  @Override
  public PlatformProfile read(String handle) throws PlatformException {
    Map<String, String> headers = headers();
    String user = http.get(API + "users/" + handle, headers);
    String repos =
        http.get(API + "users/" + handle + "/repos?per_page=100&type=owner&sort=pushed", headers);
    String calendar = http.get(CONTRIBUTIONS + handle + "/contributions", Map.of());
    Integer commits = searchCount("search/commits?per_page=1&q=author:" + handle);
    Integer pulls = searchCount("search/issues?per_page=1&q=type:pr+author:" + handle);
    Integer issues = searchCount("search/issues?per_page=1&q=type:issue+author:" + handle);
    return parse(handle, user, repos, calendar, commits, pulls, issues);
  }

  private Map<String, String> headers() {
    Map<String, String> headers = new HashMap<>();
    headers.put("Accept", "application/vnd.github+json");
    if (token != null && !token.isBlank()) {
      headers.put("Authorization", "Bearer " + token.strip());
    }
    return headers;
  }

  /**
   * The total of one search, or null when the search refused (its limit is low without a token).
   */
  private Integer searchCount(String path) {
    try {
      return Json.integer(Json.parse(http.get(API + path, headers())).get("total_count"));
    } catch (PlatformException refused) {
      return null;
    }
  }

  static PlatformProfile parse(
      String handle,
      String userBody,
      String reposBody,
      String calendarPage,
      Integer commits,
      Integer pullRequests,
      Integer issues)
      throws PlatformException {
    JsonNode user = Json.parse(userBody);
    if (!user.has("login")) {
      throw new PlatformException(Kind.UNAVAILABLE);
    }
    int stars = 0;
    Map<String, Integer> languages = new HashMap<>();
    List<JsonNode> own = new ArrayList<>();
    JsonNode repos = Json.parse(reposBody);
    if (repos.isArray()) {
      for (JsonNode repo : repos) {
        Integer count = Json.integer(repo.get("stargazers_count"));
        stars += count == null ? 0 : count;
        String language = Json.text(repo.get("language"));
        if (language != null) {
          languages.merge(language, 1, Integer::sum);
        }
        if (!repo.path("fork").asBoolean(false)) {
          own.add(repo);
        }
      }
    }
    PlatformStats stats =
        new PlatformStats(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            Json.integer(user.get("public_repos")),
            stars,
            Json.integer(user.get("followers")));
    PlatformDetails details =
        new PlatformDetails(
            Json.recentDays(calendar(calendarPage)),
            Map.of(),
            List.of(),
            Json.largestFirst(languages, PlatformDetails.MAX_LANGUAGES),
            projects(handle, own),
            null,
            commits,
            pullRequests,
            issues);
    return new PlatformProfile(
        stats,
        List.of(
            nullToEmpty(Json.text(user.get("bio"))),
            nullToEmpty(Json.text(user.get("name"))),
            nullToEmpty(Json.text(user.get("company")))),
        details);
  }

  /**
   * Contributions by day: each day cell has an id, and its tooltip, which names that id, the count.
   */
  static Map<LocalDate, Integer> calendar(String page) {
    Map<String, LocalDate> dates = new HashMap<>();
    Matcher day = DAY.matcher(page == null ? "" : page);
    while (day.find()) {
      dates.put(day.group(2), LocalDate.parse(day.group(1)));
    }
    Map<LocalDate, Integer> counts = new HashMap<>();
    Matcher tip = TIP.matcher(page == null ? "" : page);
    while (tip.find()) {
      LocalDate date = dates.get(tip.group(1));
      if (date != null) {
        counts.put(
            date, "No".equals(tip.group(2)) ? 0 : Integer.parseInt(tip.group(2).replace(",", "")));
      }
    }
    return counts;
  }

  /** The most starred repositories, then the most recently pushed, with plain-text details. */
  private static List<PlatformDetails.Project> projects(String handle, List<JsonNode> repos) {
    return repos.stream()
        .sorted(
            Comparator.comparing((JsonNode repo) -> repo.path("stargazers_count").asInt(0))
                .reversed()
                .thenComparing(
                    repo -> String.valueOf(Json.text(repo.get("pushed_at"))),
                    Comparator.reverseOrder()))
        .map(repo -> project(handle, repo))
        .filter(Objects::nonNull)
        .limit(PlatformDetails.MAX_PROJECTS)
        .toList();
  }

  private static PlatformDetails.Project project(String handle, JsonNode repo) {
    String name = Json.text(repo.get("name"));
    if (name == null || !REPO_NAME.matcher(name).matches()) {
      return null;
    }
    String description = Json.text(repo.get("description"));
    if (description != null) {
      description = description.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").strip();
      description = description.length() > 160 ? description.substring(0, 160) : description;
    }
    return new PlatformDetails.Project(
        name,
        "https://github.com/" + handle + "/" + name,
        description,
        Json.label(Json.text(repo.get("language"))),
        Json.integer(repo.get("stargazers_count")));
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
