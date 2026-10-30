package com.campuscompanion.coding.platform;

import com.campuscompanion.coding.platform.PlatformException.Kind;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

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
  private static final Pattern BADGE = Pattern.compile("badge__title'?\"?>\\s*([^<]{1,80})<");
  private static final Pattern BADGE_IMAGE =
      Pattern.compile(
          "<img\\s+src=['\"](https://cdn\\.codechef\\.com/images/badges/[A-Za-z0-9_/-]{1,80}\\.svg)['\"]\\s+alt=['\"]([^'\"<]{1,80})['\"]");
  private static final Pattern DAILY =
      Pattern.compile("userDailySubmissionsStats\\s*=\\s*(\\[.{0,200000}?\\]);");

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
    return new PlatformProfile(stats, List.of(text), details(page));
  }

  /** The badges ("Problem Solver - Gold Badge") and the submissions by day from the page. */
  private static PlatformDetails details(String page) {
    List<PlatformDetails.Badge> badges = new ArrayList<>();
    Matcher pictured = BADGE_IMAGE.matcher(page);
    while (pictured.find()) {
      String name = badgeName(pictured.group(2));
      if (name != null) {
        badges.add(new PlatformDetails.Badge(name, null, pictured.group(1), null));
      }
    }
    // Without pictures (the page changed), the badge titles alone still name them.
    Matcher badge = BADGE.matcher(page);
    boolean pictures = !badges.isEmpty();
    while (!pictures && badge.find()) {
      String name = badgeName(badge.group(1));
      if (name != null) {
        badges.add(new PlatformDetails.Badge(name, null));
      }
    }
    Map<LocalDate, Integer> days = new HashMap<>();
    Matcher daily = DAILY.matcher(page);
    if (daily.find()) {
      try {
        for (JsonNode day : Json.parse(daily.group(1))) {
          LocalDate date = date(Json.text(day.get("date")));
          Integer count = Json.integer(day.get("value"));
          if (date != null && count != null) {
            days.merge(date, count, Integer::sum);
          }
        }
      } catch (PlatformException unreadable) {
        // The page changed its daily data: the badges and numbers still count; the calendar is
        // left empty until this reader is fixed.
        days.clear();
      }
    }
    return new PlatformDetails(
        Json.recentDays(days), Map.of(), badges, Map.of(), List.of(), null, null, null, null);
  }

  /** "Problem Solver - Gold Badge" becomes "Problem Solver · Gold". */
  private static String badgeName(String title) {
    return Json.label(title.replace(" Badge", "").replaceAll("\\s+-\\s+", " · "));
  }

  /** "2026-1-5" or "2026-01-05" as a date, or null. */
  private static LocalDate date(String value) {
    if (value == null || !value.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) {
      return null;
    }
    String[] parts = value.split("-");
    try {
      return LocalDate.of(
          Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    } catch (DateTimeException impossible) {
      return null;
    }
  }

  private static Integer first(Pattern pattern, String page) {
    Matcher matcher = pattern.matcher(page);
    return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
  }
}
