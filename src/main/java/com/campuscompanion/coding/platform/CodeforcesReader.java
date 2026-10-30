package com.campuscompanion.coding.platform;

import com.campuscompanion.coding.platform.PlatformException.Kind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * Codeforces' official API: the user (rating, highest rating, rank, first name, organisation),
 * their accepted submissions (counted once per problem) and their rated contests.
 */
public class CodeforcesReader implements PlatformReader {

  private static final String API = "https://codeforces.com/api/";

  private final PlatformHttp http;

  public CodeforcesReader(PlatformHttp http) {
    this.http = http;
  }

  @Override
  public CodingPlatform platform() {
    return CodingPlatform.CODEFORCES;
  }

  @Override
  public PlatformProfile read(String handle) throws PlatformException {
    String info = http.get(API + "user.info?handles=" + handle, Map.of());
    String status =
        http.get(API + "user.status?handle=" + handle + "&from=1&count=10000", Map.of());
    String rating = http.get(API + "user.rating?handle=" + handle, Map.of());
    return parse(info, status, rating);
  }

  static PlatformProfile parse(String infoBody, String statusBody, String ratingBody)
      throws PlatformException {
    JsonNode info = ok(infoBody).path("result").path(0);
    if (info.isMissingNode()) {
      throw new PlatformException(Kind.NOT_FOUND);
    }
    Set<String> solved = new HashSet<>();
    for (JsonNode submission : ok(statusBody).path("result")) {
      if ("OK".equals(Json.text(submission.get("verdict")))) {
        JsonNode problem = submission.path("problem");
        solved.add(Json.text(problem.get("contestId")) + "/" + Json.text(problem.get("index")));
      }
    }
    int contests = ok(ratingBody).path("result").size();
    PlatformStats stats =
        new PlatformStats(
            solved.size(),
            null,
            null,
            null,
            Json.integer(info.get("rating")),
            Json.integer(info.get("maxRating")),
            contests,
            Json.label(Json.text(info.get("rank"))),
            null,
            null,
            null,
            null);
    List<String> text = new ArrayList<>();
    text.add(Json.text(info.get("firstName")));
    text.add(Json.text(info.get("lastName")));
    text.add(Json.text(info.get("organization")));
    return new PlatformProfile(stats, text);
  }

  private static JsonNode ok(String body) throws PlatformException {
    JsonNode node = Json.parse(body);
    if (!"OK".equals(Json.text(node.get("status")))) {
      throw new PlatformException(Kind.UNAVAILABLE);
    }
    return node;
  }
}
