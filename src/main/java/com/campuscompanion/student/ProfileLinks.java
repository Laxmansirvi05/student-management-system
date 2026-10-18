package com.campuscompanion.student;

import com.campuscompanion.common.error.FieldErrorDetail;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Checks and tidies the links and the personal email a student puts on their profile. Plain Java,
 * so it can be tested on its own.
 *
 * <p>A link must be https, with no user name or password in it, so that a link on the page can only
 * ever open a normal web page. "linkedin.com/in/name" is accepted and becomes
 * "https://linkedin.com/in/name". LinkedIn and GitHub links must point to those sites.
 */
public final class ProfileLinks {

  static final int MAX_LINK_LENGTH = 200;
  static final int MAX_EMAIL_LENGTH = 254;
  private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

  private ProfileLinks() {}

  /** The email in lower case, or null when blank. Adds a problem when it is not an email. */
  public static String email(String value, String field, List<FieldErrorDetail> problems) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String email = value.strip().toLowerCase(Locale.ROOT);
    if (email.length() > MAX_EMAIL_LENGTH || !EMAIL.matcher(email).matches()) {
      problems.add(new FieldErrorDetail(field, "Enter an email address such as name@example.com."));
      return null;
    }
    return email;
  }

  /**
   * The link as a full https address, or null when blank. Adds a problem when it is not a usable
   * https link, or when site is given and the link is not on that site.
   */
  public static String link(
      String value, String field, String site, List<FieldErrorDetail> problems) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String text = value.strip();
    if (!text.contains("://")) {
      text = "https://" + text;
    }
    String what = site == null ? "a web address" : "a " + site + " link";
    URI uri;
    try {
      uri = new URI(text);
    } catch (URISyntaxException notALink) {
      problems.add(
          new FieldErrorDetail(field, "Enter " + what + ", such as " + example(site) + "."));
      return null;
    }
    String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
    if (!"https".equalsIgnoreCase(uri.getScheme()) || host.isEmpty() || !host.contains(".")) {
      problems.add(new FieldErrorDetail(field, "Enter " + what + " that starts with https://."));
      return null;
    }
    if (uri.getUserInfo() != null) {
      problems.add(new FieldErrorDetail(field, "A link cannot contain a user name or password."));
      return null;
    }
    if (site != null && !host.equals(site) && !host.endsWith("." + site)) {
      problems.add(
          new FieldErrorDetail(field, "This must be " + what + ", such as " + example(site) + "."));
      return null;
    }
    if (text.length() > MAX_LINK_LENGTH) {
      problems.add(new FieldErrorDetail(field, "Use at most " + MAX_LINK_LENGTH + " characters."));
      return null;
    }
    return text;
  }

  private static String example(String site) {
    if ("linkedin.com".equals(site)) {
      return "https://linkedin.com/in/your-name";
    }
    if ("github.com".equals(site)) {
      return "https://github.com/your-name";
    }
    return "https://your-site.com";
  }
}
