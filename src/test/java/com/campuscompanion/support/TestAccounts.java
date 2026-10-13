package com.campuscompanion.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.auth.Role;
import com.campuscompanion.auth.User;
import com.campuscompanion.auth.UserRepository;
import com.campuscompanion.student.StudentProvisioningService;
import com.campuscompanion.student.dto.StudentCreateRequest;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the synthetic accounts integration tests log in with. Every account here has already
 * chosen its own password, so tests can go straight to the feature they check.
 */
@Component
public class TestAccounts {

  public static final String PASSWORD = "a-chosen-password";

  private final StudentProvisioningService provisioning;
  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final JdbcTemplate jdbc;

  public TestAccounts(
      StudentProvisioningService provisioning,
      UserRepository users,
      PasswordEncoder passwordEncoder,
      JdbcTemplate jdbc) {
    this.provisioning = provisioning;
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.jdbc = jdbc;
  }

  public static String emailOf(String rollNo) {
    return rollNo.toLowerCase(Locale.ROOT) + "@university.example";
  }

  /** A student with a chosen password. Returns the student ID. Safe to call more than once. */
  public Long student(String rollNo, String branch, int semester, String section) {
    if (!users.existsByLoginEmail(emailOf(rollNo))) {
      provisioning.provision(
          new StudentCreateRequest(
              rollNo, "Demo " + rollNo, branch, semester, section, 2023, null));
      choosePassword(emailOf(rollNo));
    }
    return jdbc.queryForObject(
        "select id from app.student_profiles where roll_no = ?", Long.class, rollNo);
  }

  /** An administrator with a chosen password. Returns the account ID. */
  public Long admin(String email) {
    if (!users.existsByLoginEmail(email)) {
      users.save(new User(email, passwordEncoder.encode(PASSWORD), Role.ADMIN, "Test Admin"));
      choosePassword(email);
    }
    return jdbc.queryForObject("select id from app.users where login_email = ?", Long.class, email);
  }

  public ApiTestClient login(int port, String email) throws Exception {
    ApiTestClient client = new ApiTestClient(port);
    assertThat(client.login(email, PASSWORD).statusCode()).as("login of " + email).isEqualTo(200);
    return client;
  }

  private void choosePassword(String email) {
    jdbc.update(
        "update app.users set must_change_password = false, password_hash = ? where login_email = ?",
        passwordEncoder.encode(PASSWORD),
        email);
  }
}
