package com.campuscompanion.coding.platform;

import com.campuscompanion.coding.platform.PlatformException.Kind;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CodeChef has no public API, so the public profile page is read: problems solved, the current and
 * highest rating, the stars and the number of contests. The owner's name and institution are in the
 * page's user details block. If CodeChef changes the page, this reader stops finding the numbers
 * and the account shows its last good read.
 */
public class CodeChefReader implements PlatformReader {

  private static final String PAGE = "https://www.codechef.com/users/";
  private static final Pattern SOLVED = Pattern.compile("Total Problems Solved:\\s*(\\d+)");
  private static final Pattern RATING = Pattern.compile("class=\"rating-number\"[^>]*>\\s*(\\d+)");
  private static final Pattern HIGHEST = Pattern.compile("Highest Rating\\s*(\\d+)");
  private static final Pattern STARS = Pattern.compile("(\\d)\\s*(?:★|&#9733;)");
  private static final Pattern CONTESTS =
      Pattern.compile("No\\. of Contests Participated:\\s*(?:<[^>]+>\\s*)*(\\d+)");
  private static final Pattern DETAILS = Pattern.compile("user-details-container");

  private final PlatformHttp http;

  public CodeChefReader(PlatformHttp http) {
    this.http = http;
  }

  @Override
  public CodingPlatform platform() {
    return CodingPlatform.CODECHEF;
  }

  @Override
  public PlatformProfile read(String handle) throws PlatformException {
    return parse(http.get(PAGE + handle, Map.of()));
  }

  static PlatformProfile parse(String page) throws PlatformException {
    Matcher details = DETAILS.matcher(page);
    if (!details.find()) {
      throw new PlatformException(Kind.NOT_FOUND);
    }
    Integer solved = first(SOLVED, page);
    if (solved == null) {
      throw new PlatformException(Kind.UNAVAILABLE);
    }
    Integer stars = first(STARS, page);
    PlatformStats stats =
        new PlatformStats(
            solved,
            null,
            null,
            null,
            first(RATING, page),
            first(HIGHEST, page),
            first(CONTESTS, page),
            stars == null ? null : stars + "★",
            null,
            null,
            null,
            null);
    // The name and institution the owner chose are in the details block, as plain text.
    String block = page.substring(details.start(), Math.min(page.length(), details.start() + 4000));
    String text = block.replaceAll("<[^>]+>", " ").replaceAll("&[a-z#0-9]+;", " ");
    return new PlatformProfile(stats, List.of(text));
  }

  private static Integer first(Pattern pattern, String page) {
    Matcher matcher = pattern.matcher(page);
    return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
  }
}
