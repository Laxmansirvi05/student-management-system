package com.campuscompanion.pyq;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PyqSearchTermsTest {

  @Test
  void blankSearchHasNoTerms() {
    assertThat(PyqSearchTerms.of(null)).isEqualTo(new PyqSearchTerms(null, null, null));
    assertThat(PyqSearchTerms.of("   ")).isEqualTo(new PyqSearchTerms(null, null, null));
  }

  @Test
  void eachWordBecomesALowerCaseContainsPattern() {
    assertThat(PyqSearchTerms.of("  Machine   2025 "))
        .isEqualTo(new PyqSearchTerms("%machine%", "%2025%", null));
  }

  @Test
  void wildcardsAreMadeOrdinary() {
    assertThat(PyqSearchTerms.of("50%_a\\b").first()).isEqualTo("%50\\%\\_a\\\\b%");
  }

  @Test
  void wordsBeyondTheThirdAreIgnored() {
    assertThat(PyqSearchTerms.of("a b c d e")).isEqualTo(new PyqSearchTerms("%a%", "%b%", "%c%"));
  }
}
