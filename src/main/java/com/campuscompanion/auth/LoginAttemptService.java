package com.campuscompanion.auth;

import com.campuscompanion.common.config.LoginProtectionProperties;
import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Blocks an email address after too many consecutive failed logins (FR-AUTH-005). The block works
 * the same way whether or not an account exists for the email: accounts are counted in the
 * database, other emails in memory, and both give the identical "too many attempts" answer.
 */
@Service
public class LoginAttemptService {

  private final UserRepository users;
  private final LoginProtectionProperties limits;
  private final Clock clock;
  private final UnknownEmailAttempts unknownEmails = new UnknownEmailAttempts();

  public LoginAttemptService(UserRepository users, LoginProtectionProperties limits, Clock clock) {
    this.users = users;
    this.limits = limits;
    this.clock = clock;
  }

  /**
   * Refuses the attempt if the email is currently blocked. Called before the password is checked,
   * so even the correct password fails during a block.
   */
  @Transactional(readOnly = true)
  public void assertNotBlocked(String email) {
    Instant now = clock.instant();
    Optional<User> user = users.findByLoginEmail(email);
    boolean blocked =
        user.isPresent() ? user.get().isLocked(now) : unknownEmails.isBlocked(email, now);
    if (blocked) {
      throw new ApiException(
          ErrorCode.AUTH_ACCOUNT_LOCKED, "Too many attempts. Try again in a few minutes.");
    }
  }

  /**
   * Counts one failed attempt for the email. It runs in its own transaction so the count is kept
   * even though the request that caused it ends in an error.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordFailure(String email) {
    Instant now = clock.instant();
    Optional<User> user = users.findByLoginEmail(email);
    if (user.isPresent()) {
      user.get().recordFailedLogin(now, limits.maxFailedAttempts(), limits.lockDuration());
    } else {
      unknownEmails.recordFailure(email, now, limits.maxFailedAttempts(), limits.lockDuration());
    }
  }

  /** Records a successful login, which clears the account's failure counter. */
  @Transactional
  public void recordSuccess(Long userId) {
    users.findById(userId).ifPresent(user -> user.recordSuccessfulLogin(clock.instant()));
  }
}
