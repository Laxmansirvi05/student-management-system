package com.campuscompanion.coding.platform;

import com.campuscompanion.coding.platform.PlatformException.Kind;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * LeetCode's public data endpoint (the one its own pages use): problems solved by difficulty, the
 * contest rating, the submission calendar, solved problems by topic, badges, languages, and the
 * profile's About me and real name.
 */
public class LeetCodeReader implements PlatformReader {

  private static final String API = "https://leetcode.com/graphql";
  private static final String QUERY =
      "query($u:String!){matchedUser(username:$u){username profile{aboutMe realName}"
          + " submitStatsGlobal{acSubmissionNum{difficulty count}}"
          + " userCalendar{submissionCalendar}"
          + " tagProblemCounts{advanced{tagName problemsSolved} intermediate{tagName problemsSolved}"
          + " fundamental{tagName problemsSolved}}"
          + " badges{displayName icon}"
          + " languageProblemCount{languageName problemsSolved}}"
          + " userContestRanking(username:$u){rating attendedContestsCount}}";

  private final PlatformHttp http;

  public LeetCodeReader(PlatformHttp http) {
    this.http = http;
  }

  @Override
  public CodingPlatform platform() {
    return CodingPlatform.LEETCODE;
  }

  @Override
  public PlatformProfile read(String handle) throws PlatformException {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("query", QUERY);
    body.putObject("variables").put("u", handle);
    String answer = http.postJson(API, body.toString(), Map.of("Referer", "https://leetcode.com"));
    return parse(answer);
  }

  static PlatformProfile parse(String body) throws PlatformException {
    JsonNode data = Json.parse(body).path("data");
    JsonNode user = data.path("matchedUser");
    if (user.isMissingNode() || user.isNull()) {
      throw new PlatformException(data.isMissingNode() ? Kind.UNAVAILABLE : Kind.NOT_FOUND);
    }
    Integer all = null;
    Integer easy = null;
    Integer medium = null;
    Integer hard = null;
    for (JsonNode entry : user.path("submitStatsGlobal").path("acSubmissionNum")) {
      Integer count = Json.integer(entry.get("count"));
      switch (String.valueOf(Json.text(entry.get("difficulty")))) {
        case "All" -> all = count;
        case "Easy" -> easy = count;
        case "Medium" -> medium = count;
        case "Hard" -> hard = count;
        default -> {}
      }
    }
    JsonNode contest = data.path("userContestRanking");
    PlatformStats stats =
        new PlatformStats(
            all,
            easy,
            medium,
            hard,
            Json.integer(contest.get("rating")),
            null,
            Json.integer(contest.get("attendedContestsCount")),
            null,
            null,
            null,
            null,
            null);
    List<String> text = new ArrayList<>();
    text.add(Json.text(user.path("profile").get("aboutMe")));
    text.add(Json.text(user.path("profile").get("realName")));
    return new PlatformProfile(stats, text, details(user));
  }

  private static PlatformDetails details(JsonNode user) throws PlatformException {
    Map<LocalDate, Integer> days = new HashMap<>();
    String calendar = Json.text(user.path("userCalendar").get("submissionCalendar"));
    if (calendar != null && !calendar.isBlank()) {
      for (Map.Entry<String, JsonNode> day : Json.parse(calendar).properties()) {
        Integer count = Json.integer(day.getValue());
        if (count != null && day.getKey().matches("\\d{1,12}")) {
          LocalDate date =
              Instant.ofEpochSecond(Long.parseLong(day.getKey()))
                  .atZone(ZoneOffset.UTC)
                  .toLocalDate();
          days.merge(date, count, Integer::sum);
        }
      }
    }
    Map<String, Integer> topics = new HashMap<>();
    for (String level : List.of("fundamental", "intermediate", "advanced")) {
      for (JsonNode tag : user.path("tagProblemCounts").path(level)) {
        Integer count = Json.integer(tag.get("problemsSolved"));
        if (count != null) {
          topics.merge(String.valueOf(Json.text(tag.get("tagName"))), count, Integer::sum);
        }
      }
    }
    List<PlatformDetails.Badge> badges = new ArrayList<>();
    for (JsonNode badge : user.path("badges")) {
      String name = Json.label(Json.text(badge.get("displayName")));
      if (name != null) {
        String icon = Json.text(badge.get("icon"));
        String source = icon != null && icon.startsWith("/") ? "https://leetcode.com" + icon : icon;
        badges.add(new PlatformDetails.Badge(name, null, source, null));
      }
    }
    Map<String, Integer> languages = new HashMap<>();
    for (JsonNode language : user.path("languageProblemCount")) {
      Integer count = Json.integer(language.get("problemsSolved"));
      if (count != null) {
        languages.put(String.valueOf(Json.text(language.get("languageName"))), count);
      }
    }
    return new PlatformDetails(
        Json.recentDays(days),
        Json.largestFirst(topics, PlatformDetails.MAX_TOPICS),
        badges,
        Json.largestFirst(languages, PlatformDetails.MAX_LANGUAGES),
        List.of(),
        null,
        null,
        null,
        null);
  }
}
