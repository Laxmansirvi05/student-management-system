package com.campuscompanion.coding.platform;

import com.campuscompanion.coding.platform.PlatformException.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * LeetCode's public data endpoint (the one its own pages use): problems solved by difficulty, the
 * contest rating, and the profile's About me and real name.
 */
public class LeetCodeReader implements PlatformReader {

  private static final String API = "https://leetcode.com/graphql";
  private static final String QUERY =
      "query($u:String!){matchedUser(username:$u){username profile{aboutMe realName}"
          + " submitStatsGlobal{acSubmissionNum{difficulty count}}}"
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
    return new PlatformProfile(stats, text);
  }
}
