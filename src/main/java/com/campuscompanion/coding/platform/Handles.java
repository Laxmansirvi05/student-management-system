package com.campuscompanion.coding.platform;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Turns what a student types (a handle, "@handle" or a profile link) into a handle that is safe to
 * put in an address. The same pattern is checked by the database.
 */
public final class Handles {

  private static final Pattern HANDLE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_.-]{0,49}$");
  private static final Pattern GITHUB = Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$");

  private Handles() {}

  /** The handle, or empty when the input cannot be a handle on that site. */
  public static Optional<String> normalise(CodingPlatform platform, String input) {
    if (input == null) {
      return Optional.empty();
    }
    String handle = input.strip();
    if (handle.contains("/")) {
      handle = lastPathSegment(handle);
    }
    if (handle.startsWith("@")) {
      handle = handle.substring(1);
    }
    Pattern pattern = platform == CodingPlatform.GITHUB ? GITHUB : HANDLE;
    return pattern.matcher(handle).matches() ? Optional.of(handle) : Optional.empty();
  }

  /**
   * "https://leetcode.com/u/name/" gives "name": the scheme and host are dropped, then the last
   * non-empty part of the path is taken. A link with no path gives an empty string.
   */
  private static String lastPathSegment(String link) {
    String path = link.split("[?#]", 2)[0];
    int scheme = path.indexOf("://");
    if (scheme >= 0) {
      path = path.substring(scheme + 3);
    }
    String[] parts = path.split("/");
    // The first part is the host when it has a dot, as in "leetcode.com/u/name".
    int first = parts.length > 0 && parts[0].contains(".") ? 1 : 0;
    for (int i = parts.length - 1; i >= first; i--) {
      String part = parts[i].strip();
      if (!part.isEmpty() && !part.equals("u")) {
        return part;
      }
    }
    return "";
  }
}
