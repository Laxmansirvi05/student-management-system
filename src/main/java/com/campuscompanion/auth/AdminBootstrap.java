package com.campuscompanion.auth;

import com.campuscompanion.common.config.BootstrapProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Creates the first administrator when the application starts and no admin exists yet. The email
 * and a strong password come from configuration (ADMIN_BOOTSTRAP_EMAIL and
 * ADMIN_BOOTSTRAP_PASSWORD), chosen by the owner. An admin account never receives a roll-number
 * password, and the bootstrap password must be changed at first login.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

  private final BootstrapProperties properties;
  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final PasswordPolicy passwordPolicy;

  public AdminBootstrap(
      BootstrapProperties properties,
      UserRepository users,
      PasswordEncoder passwordEncoder,
      PasswordPolicy passwordPolicy) {
    this.properties = properties;
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.passwordPolicy = passwordPolicy;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (users.existsByRole(Role.ADMIN)) {
      return;
    }
    String email = EmailNormalizer.normalise(properties.adminEmail());
    String password = properties.adminPassword();
    if (!StringUtils.hasText(email) || !StringUtils.hasText(password) || !looksLikeEmail(email)) {
      log.warn(
          "No administrator exists and ADMIN_BOOTSTRAP_EMAIL / ADMIN_BOOTSTRAP_PASSWORD are not"
              + " set to usable values, so none was created.");
      return;
    }
    List<String> problems = passwordPolicy.violations(password, email, null, false);
    if (!problems.isEmpty()) {
      log.warn(
          "ADMIN_BOOTSTRAP_PASSWORD was rejected, so no administrator was created: {}", problems);
      return;
    }
    if (users.existsByLoginEmail(email)) {
      log.warn(
          "ADMIN_BOOTSTRAP_EMAIL belongs to an existing non-admin account; no administrator was created.");
      return;
    }
    User admin =
        users.save(new User(email, passwordEncoder.encode(password), Role.ADMIN, "Administrator"));
    log.info(
        "Created the first administrator, account id {}. The password must be changed at first login.",
        admin.getId());
  }

  private static boolean looksLikeEmail(String value) {
    return value.matches("^[^@\\s<>]+@[^@\\s<>]+$");
  }
}
