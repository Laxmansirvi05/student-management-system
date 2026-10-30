package com.campuscompanion.coding.platform;

import com.campuscompanion.coding.platform.PlatformException.Kind;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * GitHub's official REST API: the user (bio, name, company, repositories, followers) and the first
 * 100 of their own repositories, whose stars are added up. GitHub has no problem count.
 */
public class GitHubReader implements PlatformReader {

  private static final String API = "https://api.github.com/users/";

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
    Map<String, String> headers = new HashMap<>();
    headers.put("Accept", "application/vnd.github+json");
    if (token != null && !token.isBlank()) {
      headers.put("Authorization", "Bearer " + token.strip());
    }
    String user = http.get(API + handle, headers);
    String repos = http.get(API + handle + "/repos?per_page=100&type=owner", headers);
    return parse(user, repos);
  }

  static PlatformProfile parse(String userBody, String reposBody) throws PlatformException {
    JsonNode user = Json.parse(userBody);
    if (!user.has("login")) {
      throw new PlatformException(Kind.UNAVAILABLE);
    }
    int stars = 0;
    JsonNode repos = Json.parse(reposBody);
    if (repos.isArray()) {
      for (JsonNode repo : repos) {
        Integer count = Json.integer(repo.get("stargazers_count"));
        stars += count == null ? 0 : count;
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
    return new PlatformProfile(
        stats,
        List.of(
            nullToEmpty(Json.text(user.get("bio"))),
            nullToEmpty(Json.text(user.get("name"))),
            nullToEmpty(Json.text(user.get("company")))));
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
