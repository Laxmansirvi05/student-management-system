package com.campuscompanion.coding.platform;

import com.campuscompanion.coding.platform.PlatformException.Kind;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * GeeksforGeeks has no public API; its profile page loads the user from these endpoints, which are
 * read the same way: problems solved and the coding score, the solved problems by level, and the
 * name, designation, organisation, bio and headline the owner chose. The bio and headline are only
 * on the public profile page, so it is read too: that is where a student puts the code.
 */
public class GeeksforGeeksReader implements PlatformReader {

  private static final String API =
      "https://authapi.geeksforgeeks.org/api-get/user-profile-info/?article_count=false&handle=";
  private static final String SUBMISSIONS =
      "https://practiceapi.geeksforgeeks.org/api/v1/user/problems/submissions/";
  private static final String PAGE = "https://www.geeksforgeeks.org/profile/";

  /** The bio and headline in the page's embedded data, where quotes are escaped. */
  private static final Pattern PAGE_TEXT =
      Pattern.compile("\\\\?\"(?:bio|headline)\\\\?\":\\\\?\"((?:[^\"\\\\]|\\\\.){0,600}?)\\\\?\"");

  private final PlatformHttp http;

  public GeeksforGeeksReader(PlatformHttp http) {
    this.http = http;
  }

  @Override
  public CodingPlatform platform() {
    return CodingPlatform.GEEKSFORGEEKS;
  }

  @Override
  public PlatformProfile read(String handle) throws PlatformException {
    String profile = http.get(API + handle, Map.of());
    ObjectNode request = JsonNodeFactory.instance.objectNode();
    request.put("handle", handle).put("requestType", "").put("year", "").put("month", "");
    String levels = http.postJson(SUBMISSIONS, request.toString(), Map.of());
    String page = http.get(PAGE + handle, Map.of());
    return parse(profile, levels, page);
  }

  static PlatformProfile parse(String profileBody, String levelsBody, String page)
      throws PlatformException {
    JsonNode data = Json.parse(profileBody).path("data");
    if (data.isMissingNode() || data.isNull()) {
      throw new PlatformException(Kind.NOT_FOUND);
    }
    JsonNode levels = Json.parse(levelsBody).path("result");
    Integer school = size(levels.get("School"));
    Integer basic = size(levels.get("Basic"));
    PlatformStats stats =
        new PlatformStats(
            Json.integer(data.get("total_problems_solved")),
            size(levels.get("Easy")),
            size(levels.get("Medium")),
            size(levels.get("Hard")),
            null,
            null,
            null,
            null,
            Json.integer(data.get("score")),
            null,
            null,
            null);
    List<String> text =
        new ArrayList<>(
            Arrays.asList(
                Json.text(data.get("name")),
                Json.text(data.get("designation")),
                Json.text(data.get("organization_name")),
                Json.text(data.get("institute_name"))));
    Matcher found = PAGE_TEXT.matcher(page == null ? "" : page);
    while (found.find()) {
      text.add(found.group(1));
    }
    Integer fundamentals =
        school == null && basic == null
            ? null
            : (school == null ? 0 : school) + (basic == null ? 0 : basic);
    Map<LocalDate, Integer> days = new HashMap<>();
    Map<String, Integer> languages = new HashMap<>();
    for (JsonNode level : levels) {
      for (JsonNode problem : level) {
        String when = Json.text(problem.get("user_subtime"));
        if (when != null && when.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
          days.merge(LocalDate.parse(when.substring(0, 10)), 1, Integer::sum);
        }
        String language = Json.text(problem.get("lang"));
        if (language != null) {
          languages.merge(language, 1, Integer::sum);
        }
      }
    }
    PlatformDetails details =
        new PlatformDetails(
            Json.recentDays(days),
            Map.of(),
            List.of(),
            Json.largestFirst(languages, PlatformDetails.MAX_LANGUAGES),
            List.of(),
            fundamentals,
            null,
            null,
            null);
    return new PlatformProfile(stats, text, details);
  }

  /** The number of problems listed under one level; the site lists each solved problem once. */
  private static Integer size(JsonNode level) {
    return level == null || !level.isObject() ? null : level.size();
  }
}
