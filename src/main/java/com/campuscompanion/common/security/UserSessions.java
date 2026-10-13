package com.campuscompanion.common.security;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;

/**
 * Keeps track of which sessions belong to which account, using Spring Security's session registry,
 * so that a user's sessions can be ended from the server: the other sessions after a password
 * change, and all of them after an admin reset or a deactivation.
 */
@Component
public class UserSessions {

  private final SessionRegistry sessionRegistry;

  public UserSessions(SessionRegistry sessionRegistry) {
    this.sessionRegistry = sessionRegistry;
  }

  /** Remembers that the session belongs to the user. Called at login. */
  public void register(CurrentUser user, String sessionId) {
    sessionRegistry.registerNewSession(sessionId, user);
  }

  /** Ends every session of the user except the one given. */
  public void expireOthers(CurrentUser user, String sessionIdToKeep) {
    for (SessionInformation session : sessionRegistry.getAllSessions(user, false)) {
      if (!session.getSessionId().equals(sessionIdToKeep)) {
        session.expireNow();
      }
    }
  }

  /** Ends every session of the user. */
  public void expireAll(CurrentUser user) {
    expireOthers(user, null);
  }
}
