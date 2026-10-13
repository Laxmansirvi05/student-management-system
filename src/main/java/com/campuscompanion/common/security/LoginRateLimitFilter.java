package com.campuscompanion.common.security;

import com.campuscompanion.common.config.LoginProtectionProperties;
import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Applies the per-IP limit to login attempts. It runs after the security filters, so a request
 * without a valid CSRF token is refused before it is counted.
 */
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

  private static final String LOGIN_PATH = "/api/v1/auth/login";

  private final LoginRateLimiter limiter;
  private final ApiSecurityErrorHandler errorHandler;
  private final Clock clock;

  public LoginRateLimitFilter(
      LoginProtectionProperties limits, ApiSecurityErrorHandler errorHandler, Clock clock) {
    this.limiter = new LoginRateLimiter(limits.maxAttemptsPerIp(), limits.ipWindow());
    this.errorHandler = errorHandler;
    this.clock = clock;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !("POST".equals(request.getMethod()) && LOGIN_PATH.equals(request.getRequestURI()));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String ipAddress = request.getRemoteAddr();
    Instant now = clock.instant();
    if (!limiter.tryAcquire(ipAddress, now)) {
      long seconds = Math.max(1, limiter.retryAfter(ipAddress, now).toSeconds());
      response.setHeader("Retry-After", Long.toString(seconds));
      errorHandler.write(
          request,
          response,
          new ApiException(ErrorCode.RATE_LIMITED, "Too many requests. Please wait a moment."));
      return;
    }
    chain.doFilter(request, response);
  }
}
