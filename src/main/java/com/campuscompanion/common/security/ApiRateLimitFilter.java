package com.campuscompanion.common.security;

import com.campuscompanion.common.config.ApiProtectionProperties;
import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The general API limit: at most 120 calls per session per minute (docs/09-api-contract.md section
 * 6). It runs after the security filters, so a request without a valid session has already been
 * turned away and is not counted. Logging in has its own limit, per IP address, and the AI
 * assistant has its own limits on top of this one.
 */
@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {

  private static final String LOGIN_PATH = "/api/v1/auth/login";

  private final SlidingWindowRateLimiter limiter;
  private final ApiSecurityErrorHandler errorHandler;
  private final Clock clock;

  public ApiRateLimitFilter(
      ApiProtectionProperties limits, ApiSecurityErrorHandler errorHandler, Clock clock) {
    this.limiter = new SlidingWindowRateLimiter(limits.maxRequestsPerSession(), limits.window());
    this.errorHandler = errorHandler;
    this.clock = clock;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/")
        || LOGIN_PATH.equals(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    HttpSession session = request.getSession(false);
    if (session == null) {
      chain.doFilter(request, response);
      return;
    }
    // The session ID is used only as a key in memory. It is never logged or sent anywhere.
    String key = session.getId();
    Instant now = clock.instant();
    if (!limiter.tryAcquire(key, now)) {
      long seconds = Math.max(1, limiter.retryAfter(key, now).toSeconds());
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
