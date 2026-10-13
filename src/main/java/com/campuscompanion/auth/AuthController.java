package com.campuscompanion.auth;

import com.campuscompanion.auth.dto.ChangePasswordRequest;
import com.campuscompanion.auth.dto.CsrfTokenResponse;
import com.campuscompanion.auth.dto.CurrentUserResponse;
import com.campuscompanion.auth.dto.LoginRequest;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.common.security.SessionLogin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.DeferredCsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authentication endpoints. The contract is in docs/09-api-contract.md sections 2 and 8.1. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;
  private final PasswordService passwordService;
  private final SessionLogin sessionLogin;

  public AuthController(
      AuthService authService, PasswordService passwordService, SessionLogin sessionLogin) {
    this.authService = authService;
    this.passwordService = passwordService;
    this.sessionLogin = sessionLogin;
  }

  /**
   * Issues the CSRF token. Reading it here also sets the XSRF-TOKEN cookie; the frontend sends the
   * value back in the X-XSRF-TOKEN header on every state-changing request.
   */
  @GetMapping("/csrf")
  public CsrfTokenResponse csrf(HttpServletRequest request) {
    DeferredCsrfToken deferred =
        (DeferredCsrfToken) request.getAttribute(DeferredCsrfToken.class.getName());
    var token = deferred.get();
    return new CsrfTokenResponse(token.getHeaderName(), token.getToken());
  }

  @PostMapping("/login")
  public CurrentUserResponse login(
      @Valid @RequestBody LoginRequest body,
      HttpServletRequest request,
      HttpServletResponse response) {
    CurrentUser currentUser = authService.login(body);
    sessionLogin.start(currentUser, request, response);
    return authService.describe(currentUser);
  }

  @GetMapping("/me")
  public CurrentUserResponse me(@AuthenticationPrincipal CurrentUser currentUser) {
    return authService.describe(currentUser);
  }

  /** Changes the logged-in user's own password. Contract: docs/09-api-contract.md section 8.2. */
  @PostMapping("/change-password")
  public ResponseEntity<Void> changePassword(
      @Valid @RequestBody ChangePasswordRequest body,
      @AuthenticationPrincipal CurrentUser currentUser,
      HttpServletRequest request) {
    passwordService.changePassword(currentUser, body);
    sessionLogin.renewAfterPasswordChange(currentUser, request);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
    sessionLogin.end(request, response);
    return ResponseEntity.noContent().build();
  }
}
