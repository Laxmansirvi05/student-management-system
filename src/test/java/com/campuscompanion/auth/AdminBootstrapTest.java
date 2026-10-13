package com.campuscompanion.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campuscompanion.audit.AuditService;
import com.campuscompanion.common.config.BootstrapProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Task T1-08: the first administrator is created once, from configuration, never weakly. */
class AdminBootstrapTest {

  private static final String STRONG_PASSWORD = "a-Strong-bootstrap-password-7";

  private final UserRepository users = mock(UserRepository.class);
  private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

  @Test
  void createsOneAdminThatMustChangeItsPassword() {
    when(users.existsByRole(Role.ADMIN)).thenReturn(false);
    when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

    bootstrap("  Head.Admin@University.Example ", STRONG_PASSWORD).run(null);

    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(users).save(saved.capture());
    User admin = saved.getValue();
    assertThat(admin.getLoginEmail()).isEqualTo("head.admin@university.example");
    assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
    assertThat(admin.isMustChangePassword()).isTrue();
    assertThat(admin.getPasswordHash()).isNotEqualTo(STRONG_PASSWORD).startsWith("$2");
    assertThat(encoder.matches(STRONG_PASSWORD, admin.getPasswordHash())).isTrue();
  }

  @Test
  void doesNothingWhenAnAdminAlreadyExists() {
    when(users.existsByRole(Role.ADMIN)).thenReturn(true);

    bootstrap("head.admin@university.example", STRONG_PASSWORD).run(null);

    verify(users, never()).save(any());
  }

  @Test
  void createsNobodyWhenTheSettingsAreMissingOrStillPlaceholders() {
    when(users.existsByRole(Role.ADMIN)).thenReturn(false);

    bootstrap("", STRONG_PASSWORD).run(null);
    bootstrap("head.admin@university.example", "").run(null);
    bootstrap("<administrator-email>", STRONG_PASSWORD).run(null);
    bootstrap("not-an-email", STRONG_PASSWORD).run(null);

    verify(users, never()).save(any());
  }

  @Test
  void refusesAPasswordThatBreaksThePolicy() {
    when(users.existsByRole(Role.ADMIN)).thenReturn(false);

    bootstrap("head.admin@university.example", "short").run(null);
    bootstrap("head.admin@university.example", "HEAD.ADMIN").run(null);

    verify(users, never()).save(any());
  }

  @Test
  void neverTurnsAnExistingAccountIntoAnAdmin() {
    when(users.existsByRole(Role.ADMIN)).thenReturn(false);
    when(users.existsByLoginEmail("23cse001@university.example")).thenReturn(true);

    bootstrap("23cse001@university.example", STRONG_PASSWORD).run(null);

    verify(users, never()).save(any());
  }

  private AdminBootstrap bootstrap(String email, String password) {
    return new AdminBootstrap(
        new BootstrapProperties(email, password),
        users,
        encoder,
        new PasswordPolicy(),
        mock(AuditService.class));
  }
}
