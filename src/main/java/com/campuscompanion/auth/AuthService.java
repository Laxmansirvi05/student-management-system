package com.campuscompanion.auth;

import com.campuscompanion.auth.dto.CurrentUserResponse;
import com.campuscompanion.auth.dto.LoginRequest;
import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import com.campuscompanion.common.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login and "who am I". Spring Security checks the password; this class decides what the answer
 * means. Every failed login gets the same response, whether the email has no account, the password
 * is wrong or the account is deactivated, so nothing reveals that an account exists.
 */
@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);
  private static final String GENERIC_FAILURE = "Incorrect email or password.";

  private final AuthenticationManager authenticationManager;
  private final UserRepository users;
  private final LoginAttemptService loginAttempts;

  public AuthService(
      AuthenticationManager authenticationManager,
      UserRepository users,
      LoginAttemptService loginAttempts) {
    this.authenticationManager = authenticationManager;
    this.users = users;
    this.loginAttempts = loginAttempts;
  }

  /**
   * Checks the credentials and returns the identity to keep in the session. A blocked email is
   * refused before the password is looked at; every failure counts toward the block.
   *
   * @throws ApiException with {@link ErrorCode#AUTH_ACCOUNT_LOCKED} while the email is blocked, or
   *     {@link ErrorCode#AUTH_INVALID_CREDENTIALS} for every other kind of failure
   */
  public CurrentUser login(LoginRequest request) {
    loginAttempts.assertNotBlocked(request.email());
    AccountPrincipal account;
    try {
      account = authenticate(request);
    } catch (InternalAuthenticationServiceException ex) {
      // The check itself broke (for example the database is unreachable). That is our fault,
      // not a wrong password, so it must not count toward the user's block.
      throw ex;
    } catch (AuthenticationException ex) {
      log.info("Login failed: {}", ex.getClass().getSimpleName());
      loginAttempts.recordFailure(request.email());
      throw invalidCredentials();
    }
    if (!account.isActive()) {
      log.info("Login refused for a deactivated account, id {}", account.getId());
      loginAttempts.recordFailure(request.email());
      throw invalidCredentials();
    }
    loginAttempts.recordSuccess(account.getId());
    log.info("Login succeeded for account id {}", account.getId());
    return new CurrentUser(account.getId(), account.getRole());
  }

  /** Describes the logged-in account. A deleted or deactivated account is treated as logged out. */
  @Transactional(readOnly = true)
  public CurrentUserResponse describe(CurrentUser currentUser) {
    User user =
        users
            .findById(currentUser.id())
            .filter(User::isActive)
            .orElseThrow(
                () -> new ApiException(ErrorCode.AUTH_REQUIRED, "Please log in to continue."));
    return new CurrentUserResponse(
        user.getLoginEmail(), user.getDisplayName(), user.getRole(), user.isMustChangePassword());
  }

  private AccountPrincipal authenticate(LoginRequest request) {
    Authentication result =
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(
                request.email(), request.password()));
    return (AccountPrincipal) result.getPrincipal();
  }

  private static ApiException invalidCredentials() {
    return new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS, GENERIC_FAILURE);
  }
}
