package com.campuscompanion.auth;

import java.util.Locale;

/**
 * The single rule for normalising a login email: trim it and convert it to lower case. Every lookup
 * and every write uses this, so letter case and surrounding spaces never matter (SECURITY_RULES.md
 * SR-49).
 */
public final class EmailNormalizer {

  private EmailNormalizer() {}

  public static String normalise(String email) {
    return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
  }
}
