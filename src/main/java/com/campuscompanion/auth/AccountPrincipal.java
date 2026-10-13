package com.campuscompanion.auth;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * What Spring Security needs to check a password: the account loaded from the database. It lives
 * only for the duration of one login attempt. After a successful check, {@link AuthService} keeps
 * just the ID and role in the session.
 */
public class AccountPrincipal implements UserDetails, CredentialsContainer {

  private final Long id;
  private final String loginEmail;
  private final Role role;
  private final boolean active;
  private String passwordHash;

  public AccountPrincipal(User user) {
    this.id = user.getId();
    this.loginEmail = user.getLoginEmail();
    this.role = user.getRole();
    this.active = user.isActive();
    this.passwordHash = user.getPasswordHash();
  }

  public Long getId() {
    return id;
  }

  public Role getRole() {
    return role;
  }

  /**
   * Whether the account may log in. This is checked by {@link AuthService} after the password, not
   * through {@link #isEnabled()}, so that a deactivated account takes the same time and gets the
   * same answer as a wrong password.
   */
  public boolean isActive() {
    return active;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public String getUsername() {
    return loginEmail;
  }

  @Override
  public void eraseCredentials() {
    passwordHash = null;
  }
}
