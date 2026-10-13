package com.campuscompanion.auth;

import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import com.campuscompanion.common.security.ApiSecurityErrorHandler;
import com.campuscompanion.common.security.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * While an account still has a temporary password, refuses everything except the few requests
 * needed to change it (SECURITY_RULES.md SR-52). This runs on the server for every endpoint and
 * page, so the restriction cannot be skipped by calling the API directly.
 */
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

  private static final String CHANGE_PASSWORD_PAGE = "/change-password.html";

  /** The only API requests allowed while a password change is required. */
  private static final Set<String> ALLOWED_API_REQUESTS =
      Set.of(
          "GET /api/v1/auth/csrf",
          "GET /api/v1/auth/me",
          "POST /api/v1/auth/change-password",
          "POST /api/v1/auth/logout");

  private final UserRepository users;
  private final ApiSecurityErrorHandler errorHandler;

  public PasswordChangeRequiredFilter(UserRepository users, ApiSecurityErrorHandler errorHandler) {
    this.users = users;
    this.errorHandler = errorHandler;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (isAllowed(request) || !mustChangePassword()) {
      chain.doFilter(request, response);
      return;
    }
    if (request.getRequestURI().startsWith("/api/")) {
      errorHandler.write(
          request,
          response,
          new ApiException(
              ErrorCode.AUTH_PASSWORD_CHANGE_REQUIRED,
              "Change your password before you continue."));
    } else {
      response.sendRedirect(CHANGE_PASSWORD_PAGE);
    }
  }

  private static boolean isAllowed(HttpServletRequest request) {
    String path = request.getRequestURI();
    return ALLOWED_API_REQUESTS.contains(request.getMethod() + " " + path)
        || path.equals(CHANGE_PASSWORD_PAGE)
        || path.equals("/login.html")
        || path.equals("/favicon.ico")
        || path.equals("/actuator/health")
        || path.startsWith("/css/")
        || path.startsWith("/js/");
  }

  /** True if someone is logged in and their account still has a temporary password. */
  private boolean mustChangePassword() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser user)) {
      return false;
    }
    return users.findById(user.id()).map(User::isMustChangePassword).orElse(false);
  }
}
