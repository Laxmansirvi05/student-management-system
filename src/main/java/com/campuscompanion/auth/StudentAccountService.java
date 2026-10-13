package com.campuscompanion.auth;

import com.campuscompanion.common.error.ConflictException;
import com.campuscompanion.common.error.ResourceNotFoundException;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.common.security.UserSessions;
import java.security.SecureRandom;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The login account of a student: creating it, switching it off and on, and resetting its password.
 * It is called only by the student services, inside their transactions. The role is always STUDENT,
 * so an admin account can never be created or changed this way (SECURITY_RULES.md SR-56).
 */
@Service
public class StudentAccountService {

  /** Letters and digits that are hard to confuse with each other when read aloud or typed. */
  private static final String TEMPORARY_PASSWORD_ALPHABET =
      "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

  private static final int TEMPORARY_PASSWORD_LENGTH = 14;

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final UserSessions sessions;
  private final SecureRandom random = new SecureRandom();

  public StudentAccountService(
      UserRepository users, PasswordEncoder passwordEncoder, UserSessions sessions) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.sessions = sessions;
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

  /** Blocks login and ends every session of the account at once. The password is kept. */
  @Transactional
  public void deactivate(Long userId) {
    User account = studentAccount(userId);
    account.deactivate();
    sessions.expireAll(new CurrentUser(account.getId(), account.getRole()));
  }

  /** Lets the account log in again with the password it had. */
  @Transactional
  public void activate(Long userId) {
    studentAccount(userId).activate();
  }

  /**
   * Replaces the password with a random temporary one and returns it, to be shown to the admin
   * once. Only its hash is stored. Every session of the account ends, any block from failed logins
   * is cleared, and the account must change the password at the next login.
   */
  @Transactional
  public String resetPassword(Long userId) {
    User account = studentAccount(userId);
    String temporaryPassword = randomPassword();
    account.setTemporaryPassword(passwordEncoder.encode(temporaryPassword));
    sessions.expireAll(new CurrentUser(account.getId(), account.getRole()));
    return temporaryPassword;
  }

  private User studentAccount(Long userId) {
    return users
        .findById(userId)
        .filter(account -> account.getRole() == Role.STUDENT)
        .orElseThrow(ResourceNotFoundException::new);
  }

  private String randomPassword() {
    StringBuilder password = new StringBuilder(TEMPORARY_PASSWORD_LENGTH);
    for (int i = 0; i < TEMPORARY_PASSWORD_LENGTH; i++) {
      password.append(
          TEMPORARY_PASSWORD_ALPHABET.charAt(random.nextInt(TEMPORARY_PASSWORD_ALPHABET.length())));
    }
    return password.toString();
  }
}
