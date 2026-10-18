package com.campuscompanion.student;

import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.common.error.FieldErrorDetail;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProfileLinksTest {

  private final List<FieldErrorDetail> problems = new ArrayList<>();

  private String link(String value, String site) {
    return ProfileLinks.link(value, "f", site, problems);
  }

  @Test
  void addsHttpsWhenLeftOut() {
    assertThat(link("linkedin.com/in/demo-student", "linkedin.com"))
        .isEqualTo("https://linkedin.com/in/demo-student");
    assertThat(link("https://www.linkedin.com/in/demo", "linkedin.com"))
        .isEqualTo("https://www.linkedin.com/in/demo");
    assertThat(problems).isEmpty();
  }

  @Test
  void blankIsEmptyNotAnError() {
    assertThat(link("  ", "github.com")).isNull();
    assertThat(ProfileLinks.email("", "e", problems)).isNull();
    assertThat(problems).isEmpty();
  }

  @Test
  void refusesAnythingButAnHttpsWebPage() {
    assertThat(link("javascript:alert(1)", null)).isNull();
    assertThat(link("http://demo.example", null)).isNull();
    assertThat(link("https://user:secret@demo.example", null)).isNull();
    assertThat(link("https://localhost", null)).isNull();
    assertThat(link("data:text/html,hi", null)).isNull();
    assertThat(problems).hasSize(5);
  }

  @Test
  void aSiteLinkMustBeOnThatSite() {
    assertThat(link("https://github.com.evil.example/x", "github.com")).isNull();
    assertThat(link("https://notlinkedin.com/in/x", "linkedin.com")).isNull();
    assertThat(link("https://gist.github.com/x", "github.com"))
        .isEqualTo("https://gist.github.com/x");
    assertThat(problems).hasSize(2);
  }

  @Test
  void refusesALinkThatIsTooLong() {
    assertThat(link("https://demo.example/" + "a".repeat(200), null)).isNull();
    assertThat(problems).hasSize(1);
  }

  @Test
  void checksAndLowersAnEmail() {
    assertThat(ProfileLinks.email(" Demo.Student@Example.COM ", "e", problems))
        .isEqualTo("demo.student@example.com");
    assertThat(ProfileLinks.email("not-an-email", "e", problems)).isNull();
    assertThat(problems).extracting(FieldErrorDetail::field).containsExactly("e");
  }
}
