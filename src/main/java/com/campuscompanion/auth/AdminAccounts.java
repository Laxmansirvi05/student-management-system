package com.campuscompanion.auth;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lets other features ask about admin accounts without touching the user table themselves. */
@Service
public class AdminAccounts {

  private final UserRepository users;

  public AdminAccounts(UserRepository users) {
    this.users = users;
  }

  /** The account ID of the first administrator, if one exists yet. */
  @Transactional(readOnly = true)
  public Optional<Long> firstAdminId() {
    return users.findFirstByRoleOrderByIdAsc(Role.ADMIN).map(User::getId);
  }
}
