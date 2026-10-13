package com.campuscompanion.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * Starts and ends a logged-in session using Spring Security's own session machinery. Nothing here
 * checks a password; that has already happened.
 */
@Component
public class SessionLogin {

  private final SecurityContextRepository securityContextRepository;
  private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

  private final UserSessions userSessions;

  public SessionLogin(
      SecurityContextRepository securityContextRepository, UserSessions userSessions) {
    this.securityContextRepository = securityContextRepository;
    this.userSessions = userSessions;
  }

  /**
   * After a password change: ends the user's other sessions and gives the current one a new
   * identifier, so nothing obtained with the old password stays usable.
   */
  public void renewAfterPasswordChange(CurrentUser user, HttpServletRequest request) {
    userSessions.expireOthers(user, request.getSession().getId());
    request.changeSessionId();
  }

  /**
   * Stores the identity in the session. An existing session gets a new identifier first, so an
   * identifier planted before login is useless afterwards (session fixation).
   */
  public void start(CurrentUser user, HttpServletRequest request, HttpServletResponse response) {
    if (request.getSession(false) != null) {
      request.changeSessionId();
    }
    Authentication authentication =
        UsernamePasswordAuthenticationToken.authenticated(
            user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    securityContextRepository.saveContext(context, request, response);
    userSessions.register(user, request.getSession().getId());
  }

  /** Invalidates the session on the server and clears the security context. */
  public void end(HttpServletRequest request, HttpServletResponse response) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    logoutHandler.logout(request, response, authentication);
  }
}
