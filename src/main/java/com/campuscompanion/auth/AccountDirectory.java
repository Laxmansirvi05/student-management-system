package com.campuscompanion.auth;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Looks up the login email of accounts by ID, for screens that show who did or got something. */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AccountDirectory {

  private final UserRepository users;

  public AccountDirectory(UserRepository users) {
    this.users = users;
  }

  /** Account ID to login email, for the IDs that exist. */
  @Transactional(readOnly = true)
  public Map<Long, String> emailsOf(Collection<Long> accountIds) {
    Map<Long, String> emails = new HashMap<>();
    users.findAllById(accountIds).forEach(user -> emails.put(user.getId(), user.getLoginEmail()));
    return emails;
  }
}
