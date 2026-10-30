package com.campuscompanion.coding.platform;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * HackerRank has no public API; its profile page loads the hacker and their badges from these
 * endpoints, which are read the same way. Problems solved are added up over the badges (each badge
 * is one track, so a challenge is counted once); the Problem Solving stars are the rank.
 */
public class HackerRankReader implements PlatformReader {

  private static final String API = "https://www.hackerrank.com/rest/";

  private final PlatformHttp http;

  public HackerRankReader(PlatformHttp http) {
    this.http = http;
  }

  @Override
  public CodingPlatform platform() {
    return CodingPlatform.HACKERRANK;
  }

  @Override
  public PlatformProfile read(String handle) throws PlatformException {
    String profile = http.get(API + "contests/master/hackers/" + handle + "/profile", Map.of());
    String badges = http.get(API + "hackers/" + handle + "/badges", Map.of());
    return parse(profile, badges);
  }

  static PlatformProfile parse(String profileBody, String badgesBody) throws PlatformException {
    JsonNode model = Json.parse(profileBody).path("model");
    if (model.isMissingNode() || model.isNull()) {
      throw new PlatformException(PlatformException.Kind.NOT_FOUND);
    }
    int solved = 0;
    String rank = null;
    List<PlatformDetails.Badge> badges = new ArrayList<>();
    for (JsonNode badge : Json.parse(badgesBody).path("models")) {
      Integer count = Json.integer(badge.get("solved"));
      solved += count == null ? 0 : count;
      String name = Json.label(Json.text(badge.get("badge_name")));
      Integer badgeStars = Json.integer(badge.get("stars"));
      if (name != null && badgeStars != null && badgeStars > 0) {
        badges.add(new PlatformDetails.Badge(name, badgeStars));
      }
      if ("problem-solving".equals(Json.text(badge.get("badge_type")))) {
        Integer stars = Json.integer(badge.get("stars"));
        rank = stars == null ? null : stars + "★ Problem Solving";
      }
    }
    PlatformStats stats =
        new PlatformStats(solved, null, null, null, null, null, null, rank, null, null, null, null);
    PlatformDetails details =
        new PlatformDetails(
            Map.of(), Map.of(), badges, Map.of(), List.of(), solved, null, null, null);
    return new PlatformProfile(
        stats,
        Arrays.asList(
            Json.text(model.get("short_bio")),
            Json.text(model.get("name")),
            Json.text(model.get("personal_first_name"))),
        details);
  }
}
