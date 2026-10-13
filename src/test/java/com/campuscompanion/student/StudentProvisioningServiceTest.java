package com.campuscompanion.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campuscompanion.auth.Role;
import com.campuscompanion.auth.StudentAccountService;
import com.campuscompanion.auth.User;
import com.campuscompanion.auth.UserRepository;
import com.campuscompanion.common.config.IdentityProperties;
import com.campuscompanion.common.error.ConflictException;
import com.campuscompanion.common.error.ValidationException;
import com.campuscompanion.student.dto.AdminStudentResponse;
import com.campuscompanion.student.dto.StudentCreateRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Task T1-10: the provisioning rules without a database. Covers scenario PV2 (the domain comes from
 * configuration) and the order of the checks in PV1 and PV3.
 */
class StudentProvisioningServiceTest {

  private final UserRepository users = mock(UserRepository.class);
  private final StudentProfileRepository profiles = mock(StudentProfileRepository.class);
  private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

  @BeforeEach
  void savingReturnsWhatWasSaved() {
    when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
    when(profiles.save(any(StudentProfile.class))).thenAnswer(call -> call.getArgument(0));
  }

  @Test
  void generatesTheEmailFromTheRollNumberAndTheConfiguredDomain() {
    AdminStudentResponse first = service("university.example").provision(student("23cse001"));
    AdminStudentResponse second = service("college.example").provision(student("23CSE002"));

    assertThat(first.rollNo()).isEqualTo("23CSE001");
    assertThat(first.email()).isEqualTo("23cse001@university.example");
    assertThat(second.email()).isEqualTo("23cse002@college.example");
  }

  @Test
  void theTemporaryPasswordIsTheUpperCaseRollNumberStoredOnlyAsAHash() {
    AdminStudentResponse response = service("university.example").provision(student("23cse001"));

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(users).save(saved.capture());
    User account = saved.getValue();
    assertThat(account.getRole()).isEqualTo(Role.STUDENT);
    assertThat(account.isMustChangePassword()).isTrue();
    assertThat(account.getPasswordHash()).startsWith("$2").doesNotContain("23CSE001");
    assertThat(encoder.matches("23CSE001", account.getPasswordHash())).isTrue();
    assertThat(encoder.matches("23cse001", account.getPasswordHash())).isFalse();
    assertThat(response.mustChangePassword()).isTrue();
    assertThat(response.active()).isTrue();
    assertThat(response.locked()).isFalse();
    assertThat(response.lastLoginAt()).isNull();
  }

  @Test
  void tidiesTheTextFieldsAndTreatsABlankPhoneAsNone() {
    service("university.example")
        .provision(
            new StudentCreateRequest(
                "23CSE001", "  Demo Student One ", " CSE ", 3, " A ", 2023, " "));

    ArgumentCaptor<StudentProfile> saved = ArgumentCaptor.forClass(StudentProfile.class);
    verify(profiles).save(saved.capture());
    assertThat(saved.getValue().getFullName()).isEqualTo("Demo Student One");
    assertThat(saved.getValue().getBranch()).isEqualTo("CSE");
    assertThat(saved.getValue().getSection()).isEqualTo("A");
    assertThat(saved.getValue().getPhone()).isNull();
  }

  @Test
  void savesNothingForAnInvalidRecord() {
    assertThatThrownBy(() -> service("university.example").provision(student("23 CSE 001")))
        .isInstanceOf(ValidationException.class);

    verify(users, never()).save(any());
    verify(profiles, never()).save(any());
  }

  @Test
  void refusesARollNumberThatExistsInAnyLetterCase() {
    when(profiles.existsByRollNo("23CSE001")).thenReturn(true);

    assertThatThrownBy(() -> service("university.example").provision(student("23cse001")))
        .isInstanceOf(ConflictException.class)
        .hasMessage("A student with this roll number already exists.");

    verify(users, never()).save(any());
    verify(profiles, never()).save(any());
  }

  @Test
  void refusesAGeneratedEmailThatAlreadyBelongsToAnAccount() {
    when(users.existsByLoginEmail("23cse001@university.example")).thenReturn(true);

    assertThatThrownBy(() -> service("university.example").provision(student("23CSE001")))
        .isInstanceOf(ConflictException.class);

    verify(users, never()).save(any());
    verify(profiles, never()).save(any());
  }

  private StudentProvisioningService service(String domain) {
    return new StudentProvisioningService(
        new StudentRosterValidator(),
        profiles,
        new StudentAccountService(users, encoder),
        new IdentityProperties(domain),
        Clock.fixed(Instant.parse("2026-10-13T06:00:00Z"), ZoneOffset.UTC));
  }

  private static StudentCreateRequest student(String rollNo) {
    return new StudentCreateRequest(rollNo, "Demo Student One", "CSE", 3, "A", 2023, null);
  }
}
