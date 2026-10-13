package com.campuscompanion.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads an account by its normalised login email for Spring Security's password check. */
@Service
public class AccountUserDetailsService implements UserDetailsService {

  private final UserRepository users;

  public AccountUserDetailsService(UserRepository users) {
    this.users = users;
  }

  @Override
  @Transactional(readOnly = true)
  public UserDetails loadUserByUsername(String loginEmail) {
    return users
        .findByLoginEmail(EmailNormalizer.normalise(loginEmail))
        .map(AccountPrincipal::new)
        .orElseThrow(() -> new UsernameNotFoundException("No account for that email"));
  }
}
