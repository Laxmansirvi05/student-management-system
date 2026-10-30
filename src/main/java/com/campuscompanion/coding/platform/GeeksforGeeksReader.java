package com.campuscompanion.coding.platform;

import com.campuscompanion.coding.platform.PlatformException.Kind;
import java.util.Arrays;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * GeeksforGeeks has no public API; its profile page loads the user from this endpoint, which is
 * read the same way: problems solved, the coding score, and the name, designation and organisation
 * the owner chose.
 */
public class GeeksforGeeksReader implements PlatformReader {

  private static final String API =
      "https://authapi.geeksforgeeks.org/api-get/user-profile-info/?article_count=false&handle=";

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
    return parse(http.get(API + handle, Map.of()));
  }

  static PlatformProfile parse(String body) throws PlatformException {
    JsonNode data = Json.parse(body).path("data");
    if (data.isMissingNode() || data.isNull()) {
      throw new PlatformException(Kind.NOT_FOUND);
    }
    PlatformStats stats =
        new PlatformStats(
            Json.integer(data.get("total_problems_solved")),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            Json.integer(data.get("score")),
            null,
            null,
            null);
    return new PlatformProfile(
        stats,
        Arrays.asList(
            Json.text(data.get("name")),
            Json.text(data.get("designation")),
            Json.text(data.get("organization_name")),
            Json.text(data.get("institute_name"))));
  }
}
