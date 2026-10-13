package com.campuscompanion.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.common.config.StorageProperties;
import org.junit.jupiter.api.Test;

/** The Content-Security-Policy header, with and without file storage set up. */
class ContentSecurityPolicyTest {

  private static String policyFor(String supabaseUrl) {
    return SecurityConfig.contentSecurityPolicy(
        new StorageProperties(supabaseUrl, "key", "bucket"));
  }

  @Test
  void withoutStorageNothingComesFromAnotherHost() {
    String policy = policyFor("");

    assertThat(policy)
        .contains("default-src 'self'")
        .contains("script-src 'self'")
        .contains("style-src 'self'")
        .contains("img-src 'self' data:;")
        .contains("frame-src 'none'")
        .contains("object-src 'none'")
        .contains("frame-ancestors 'none'")
        .contains("form-action 'self'")
        .doesNotContain("unsafe-inline")
        .doesNotContain("unsafe-eval");
  }

  @Test
  void withStorageOnlyFramesAndImagesMayComeFromTheStorageHost() {
    String policy = policyFor("https://abcdefgh.supabase.co");

    assertThat(policy)
        .contains("img-src 'self' data: https://abcdefgh.supabase.co;")
        .contains("frame-src https://abcdefgh.supabase.co;")
        // Scripts, styles and requests made by scripts still come from our own origin only.
        .contains("default-src 'self'; script-src 'self'; style-src 'self';")
        .doesNotContain("connect-src");
  }

  @Test
  void aValueThatIsNotAPlainHttpsHostIsNeverWrittenIntoTheHeader() {
    for (String bad :
        new String[] {
          "http://abcdefgh.supabase.co",
          "https://abcdefgh.supabase.co/path",
          "https://abcdefgh.supabase.co; script-src *",
          "https://abcdefgh.supabase.co https://evil.example",
          "<your-project-url>",
          "*"
        }) {
      String policy = policyFor(bad);

      assertThat(policy).as(bad).contains("img-src 'self' data:;").contains("frame-src 'none'");
      assertThat(policy).as(bad).doesNotContain("evil").doesNotContain("script-src *");
    }
  }
}
