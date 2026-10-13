package com.campuscompanion.common.security;

import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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

  private static final String LOGIN_PAGE = "/login.html";
  private static final String FRONT_DOOR = "/";

  private final HandlerExceptionResolver resolver;

  public ApiSecurityErrorHandler(
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
    this.resolver = resolver;
  }

  /** Not logged in: an API call gets a 401 error, a page is sent to the login page. */
  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
      throws IOException {
    if (!isApiRequest(request)) {
      response.sendRedirect(LOGIN_PAGE);
      return;
    }
    resolver.resolveException(
        request,
        response,
        null,
        new ApiException(ErrorCode.AUTH_REQUIRED, "Please log in to continue."));
  }

  /** Logged in but not allowed, or a missing or wrong CSRF token. */
  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
      throws IOException {
    if (!isApiRequest(request) && !(ex instanceof CsrfException)) {
      // A page that belongs to the other role: go to the front door, which sends each user to
      // their own dashboard.
      response.sendRedirect(FRONT_DOOR);
      return;
    }
    ApiException failure =
        ex instanceof CsrfException
            ? new ApiException(
                ErrorCode.CSRF_INVALID, "Your page is out of date. Refresh it and try again.")
            : new ApiException(ErrorCode.ACCESS_DENIED, "You do not have permission to do this.");
    write(request, response, failure);
  }

  /** Everything that is not a page: the REST API and the health endpoints. */
  private static boolean isApiRequest(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.startsWith("/api/") || path.equals("/actuator") || path.startsWith("/actuator/");
  }

  /** Writes any deliberate failure raised in a filter, in the API's standard error format. */
  public void write(
      HttpServletRequest request, HttpServletResponse response, ApiException failure) {
    resolver.resolveException(request, response, null, failure);
  }
}
