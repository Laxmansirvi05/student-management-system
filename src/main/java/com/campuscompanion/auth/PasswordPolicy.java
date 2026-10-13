package com.campuscompanion.auth;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The one rule for what a user may choose as a password (FR-AUTH-003, SECURITY_RULES.md SR-53).
 * Every path that sets a user-chosen password goes through this class, so the rule "never the roll
 * number" cannot be bypassed by one of them.
 *
 * <p>A password must have 8 to 72 characters and, ignoring letter case, must not be the user's roll
 * number, the user's email address, the part of the email before the at sign, or the current
 * password.
 */
@Component
public class PasswordPolicy {

  public static final int MIN_LENGTH = 8;

  /** BCrypt reads at most 72 bytes, so anything longer is refused instead of silently cut. */
  public static final int MAX_BYTES = 72;

  /**
   * Lists what is wrong with a proposed password. An empty list means it is acceptable.
   *
   * @param newPassword the proposed password
   * @param loginEmail the user's login email
   * @param rollNo the user's roll number, or null for an account that has none (an admin)
   * @param sameAsCurrent whether the proposed password equals the current one
   */
  public List<String> violations(
      String newPassword, String loginEmail, String rollNo, boolean sameAsCurrent) {
    List<String> problems = new ArrayList<>();
    if (newPassword.length() < MIN_LENGTH) {
      problems.add("Use at least " + MIN_LENGTH + " characters.");
    }
    if (newPassword.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
      problems.add("Use at most " + MAX_BYTES + " characters.");
    }
    if (rollNo != null && newPassword.equalsIgnoreCase(rollNo)) {
      problems.add("Your password cannot be your roll number.");
    }
    if (newPassword.equalsIgnoreCase(loginEmail)
        || newPassword.equalsIgnoreCase(localPart(loginEmail))) {
      problems.add("Your password cannot be your email address.");
    }
    if (sameAsCurrent) {
      problems.add("Choose a password different from your current one.");
    }
    return problems;
  }

  private static String localPart(String email) {
    int at = email.indexOf('@');
    return at < 0 ? email : email.substring(0, at);
  }
}
