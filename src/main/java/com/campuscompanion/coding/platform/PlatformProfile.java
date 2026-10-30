package com.campuscompanion.coding.platform;

import java.util.List;
import java.util.Locale;

/**
 * What one read of a public profile gives: the numbers, and the text fields the owner controls
 * (bio, about me, name and the like), which are searched for the verification code.
 */
public record PlatformProfile(PlatformStats stats, List<String> ownerText) {

  public PlatformProfile {
    ownerText = ownerText.stream().filter(text -> text != null && !text.isBlank()).toList();
  }

  /** Whether the code appears in any of the owner's text fields, ignoring letter case. */
  public boolean mentions(String code) {
    String wanted = code.toUpperCase(Locale.ROOT);
    return ownerText.stream().anyMatch(text -> text.toUpperCase(Locale.ROOT).contains(wanted));
  }
}
