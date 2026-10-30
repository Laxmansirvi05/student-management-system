package com.campuscompanion.coding.platform;

/**
 * The coding sites a student can link (ADR 0017). Each knows how its public profile address is
 * built, whether it counts problems for the leaderboard, and where a student can put the
 * verification code.
 */
public enum CodingPlatform {
  GITHUB("GitHub", "https://github.com/%s", false, "your bio, name or company"),
  LEETCODE(
      "LeetCode", "https://leetcode.com/u/%s/", true, "the About me (summary) of your profile"),
  CODEFORCES(
      "Codeforces", "https://codeforces.com/profile/%s", true, "your first name or organisation"),
  CODECHEF("CodeChef", "https://www.codechef.com/users/%s", true, "your name or institution"),
  GEEKSFORGEEKS(
      "GeeksforGeeks",
      "https://www.geeksforgeeks.org/user/%s/",
      true,
      "your name, designation or organisation"),
  HACKERRANK("HackerRank", "https://www.hackerrank.com/profile/%s", true, "your short bio or name");

  private final String displayName;
  private final String profileUrl;
  private final boolean countsProblems;
  private final String codeHint;

  CodingPlatform(String displayName, String profileUrl, boolean countsProblems, String codeHint) {
    this.displayName = displayName;
    this.profileUrl = profileUrl;
    this.countsProblems = countsProblems;
    this.codeHint = codeHint;
  }

  public String displayName() {
    return displayName;
  }

  /** The public profile page of a handle. The handle has already passed the handle pattern. */
  public String profileUrl(String handle) {
    return profileUrl.formatted(handle);
  }

  /** Whether the site's problems solved count towards the leaderboard (GitHub has none). */
  public boolean countsProblems() {
    return countsProblems;
  }

  /** Where on the site a student can put the verification code, in words. */
  public String codeHint() {
    return codeHint;
  }
}
