package com.campuscompanion.coding.platform;

/**
 * The public numbers read from one coding site. A site fills only what it publishes; the rest is
 * null. Only numbers and one short label are kept: nothing a site returns is shown as HTML.
 */
public record PlatformStats(
    Integer problemsSolved,
    Integer easySolved,
    Integer mediumSolved,
    Integer hardSolved,
    Integer contestRating,
    Integer maxRating,
    Integer contestsAttended,
    String rankLabel,
    Integer score,
    Integer publicRepos,
    Integer stars,
    Integer followers) {

  /** Only problems solved, for the sites that publish nothing else of use. */
  static PlatformStats solved(Integer problemsSolved) {
    return new PlatformStats(
        problemsSolved, null, null, null, null, null, null, null, null, null, null, null);
  }
}
