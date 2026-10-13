package com.campuscompanion.auth;

import com.campuscompanion.common.error.ConflictException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the login account of a new student. It is called only by student provisioning, inside
 * that transaction. The role is always STUDENT, so an admin account can never be created this way
 * (SECURITY_RULES.md SR-56).
 */
@Service
public class StudentAccountService {

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;

  public StudentAccountService(UserRepository users, PasswordEncoder passwordEncoder) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
  }

  /**
   * Saves a student account that must change its password at first login. Only the BCrypt hash of
   * the temporary password is stored.
   *
   * @throws ConflictException if an account with this email already exists
   */
  @Transactional
  public User createStudentAccount(String email, String temporaryPassword, String displayName) {
    String loginEmail = EmailNormalizer.normalise(email);
    if (users.existsByLoginEmail(loginEmail)) {
      throw new ConflictException("An account with this email address already exists.");
    }
    return users.save(
        new User(loginEmail, passwordEncoder.encode(temporaryPassword), Role.STUDENT, displayName));
  }
}
