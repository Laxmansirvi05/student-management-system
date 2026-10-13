package com.campuscompanion.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Task T1-08: the password policy, including the rule that a roll number is never a password. */
class PasswordPolicyTest {

  private static final String EMAIL = "23cse001@university.example";
  private static final String ROLL_NO = "23CSE001";

  private final PasswordPolicy policy = new PasswordPolicy();

  @Test
  void acceptsAnOrdinaryPassword() {
    assertThat(policy.violations("blue-Lamp-42", EMAIL, ROLL_NO, false)).isEmpty();
  }

  @Test
  void enforcesTheLengthLimits() {
    assertThat(check("a".repeat(7))).containsExactly("Use at least 8 characters.");
    assertThat(check("a".repeat(8))).isEmpty();
    assertThat(check("a".repeat(72))).isEmpty();
    assertThat(check("a".repeat(73))).containsExactly("Use at most 72 characters.");
  }

  @Test
  void countsBytesNotCharactersForTheUpperLimit() {
    // 30 characters, but 90 bytes in UTF-8: more than BCrypt would read.
    assertThat(check("ह".repeat(30))).containsExactly("Use at most 72 characters.");
  }

  @Test
  void rejectsTheRollNumberInAnyLetterCase() {
    String otherEmail = "someone@university.example";

    for (String attempt : List.of("23CSE001", "23cse001", "23Cse001")) {
      assertThat(policy.violations(attempt, otherEmail, ROLL_NO, false))
          .as(attempt)
          .containsExactly("Your password cannot be your roll number.");
    }
  }

  @Test
  void rejectsTheEmailAndThePartBeforeTheAtSignInAnyLetterCase() {
    for (String attempt : List.of(EMAIL, "23CSE001@University.Example")) {
      assertThat(policy.violations(attempt, EMAIL, null, false))
          .as(attempt)
          .containsExactly("Your password cannot be your email address.");
    }
    assertThat(policy.violations("ADMIN.user", "admin.user@university.example", null, false))
        .containsExactly("Your password cannot be your email address.");
  }

  @Test
  void rejectsTheCurrentPassword() {
    assertThat(policy.violations("blue-Lamp-42", EMAIL, ROLL_NO, true))
        .containsExactly("Choose a password different from your current one.");
  }

  @Test
  void anAdminAccountHasNoRollNumberToCompareWith() {
    assertThat(policy.violations("blue-Lamp-42", "admin@university.example", null, false))
        .isEmpty();
  }

  @Test
  void reportsEveryProblemAtOnce() {
    // A new student's temporary password is the roll number, which is also the current password.
    assertThat(policy.violations(ROLL_NO, EMAIL, ROLL_NO, true))
        .containsExactly(
            "Your password cannot be your roll number.",
            "Your password cannot be your email address.",
            "Choose a password different from your current one.");
  }

  private List<String> check(String password) {
    return policy.violations(password, EMAIL, ROLL_NO, false);
  }
}
