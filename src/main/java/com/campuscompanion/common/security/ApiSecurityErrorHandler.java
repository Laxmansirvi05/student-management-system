package com.campuscompanion.common.security;

import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Turns the refusals raised inside Spring Security's filters into the same error format as the rest
 * of the API. It hands them to the global exception handler, so there is still only one place that
 * builds error responses.
 */
@Component
public class ApiSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final HandlerExceptionResolver resolver;

  public ApiSecurityErrorHandler(
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
    this.resolver = resolver;
  }

  /** Not logged in. */
  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) {
    resolver.resolveException(
        request,
        response,
        null,
        new ApiException(ErrorCode.AUTH_REQUIRED, "Please log in to continue."));
  }

  /** Logged in but not allowed, or a missing or wrong CSRF token. */
  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) {
    ApiException failure =
        ex instanceof CsrfException
            ? new ApiException(
                ErrorCode.CSRF_INVALID, "Your page is out of date. Refresh it and try again.")
            : new ApiException(ErrorCode.ACCESS_DENIED, "You do not have permission to do this.");
    resolver.resolveException(request, response, null, failure);
  }
}
