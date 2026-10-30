package com.campuscompanion.coding.platform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HandlesTest {

  @Test
  void aUsernameOrAProfileLinkGivesTheHandle() {
    assertThat(Handles.normalise(CodingPlatform.LEETCODE, "  demo_user ")).contains("demo_user");
    assertThat(Handles.normalise(CodingPlatform.LEETCODE, "https://leetcode.com/u/demo_user/"))
        .contains("demo_user");
    assertThat(Handles.normalise(CodingPlatform.GITHUB, "github.com/demo-user?tab=repos"))
        .contains("demo-user");
    assertThat(Handles.normalise(CodingPlatform.HACKERRANK, "@demo.user")).contains("demo.user");
    assertThat(
            Handles.normalise(
                CodingPlatform.CODEFORCES, "https://codeforces.com/profile/Demo_User"))
        .contains("Demo_User");
  }

  @Test
  void anythingThatCannotBeAHandleIsRefused() {
    assertThat(Handles.normalise(CodingPlatform.LEETCODE, null)).isEmpty();
    assertThat(Handles.normalise(CodingPlatform.LEETCODE, "")).isEmpty();
    assertThat(Handles.normalise(CodingPlatform.LEETCODE, "two words")).isEmpty();
    // Only the last part of a path is kept, so a path can never reach another address.
    assertThat(Handles.normalise(CodingPlatform.LEETCODE, "../admin")).contains("admin");
    assertThat(Handles.normalise(CodingPlatform.LEETCODE, "a%2Fb")).isEmpty();
    assertThat(Handles.normalise(CodingPlatform.LEETCODE, "x".repeat(51))).isEmpty();
    // GitHub allows no dots or underscores.
    assertThat(Handles.normalise(CodingPlatform.GITHUB, "demo_user")).isEmpty();
    assertThat(Handles.normalise(CodingPlatform.LEETCODE, "https://leetcode.com/")).isEmpty();
  }
}
