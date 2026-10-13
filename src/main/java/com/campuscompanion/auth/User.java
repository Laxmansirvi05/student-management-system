package com.campuscompanion.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A login account: identity, credentials and account state. One row per account, for students and
 * admins alike. A student's roll number is not stored here; it lives in the student profile.
 */
@Entity
@Table(name = "users")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** The login identifier, always trimmed and lower case. */
  @Column(name = "login_email", nullable = false, length = 254)
  private String loginEmail;

  /** BCrypt hash. No password is ever stored in any other form. */
  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Role role;

  @Column(name = "display_name", nullable = false, length = 120)
  private String displayName;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "must_change_password", nullable = false)
  private boolean mustChangePassword = true;

  @JdbcTypeCode(SqlTypes.SMALLINT)
  @Column(name = "failed_login_count", nullable = false)
  private int failedLoginCount;

  @Column(name = "locked_until")
  private Instant lockedUntil;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Required by JPA. */
  protected User() {}

  public User(String loginEmail, String passwordHash, Role role, String displayName) {
    this.loginEmail = loginEmail;
    this.passwordHash = passwordHash;
    this.role = role;
    this.displayName = displayName;
  }

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  /** True while further login attempts for this account are blocked. */
  public boolean isLocked(Instant now) {
    return lockedUntil != null && lockedUntil.isAfter(now);
  }

  /**
   * Counts one failed attempt. On reaching the limit the account is blocked for the given time and
   * the counter starts again, so the next block needs the full number of failures.
   */
  public void recordFailedLogin(Instant now, int maxFailedAttempts, Duration lockDuration) {
    failedLoginCount++;
    if (failedLoginCount >= maxFailedAttempts) {
      lockedUntil = now.plus(lockDuration);
      failedLoginCount = 0;
    }
  }

  /** Records a successful login and clears the failure counter and any block. */
  public void recordSuccessfulLogin(Instant now) {
    lastLoginAt = now;
    failedLoginCount = 0;
    lockedUntil = null;
  }

  /** Stores the hash of a password the user chose. The account no longer has to change it. */
  public void changePassword(String newPasswordHash) {
    passwordHash = newPasswordHash;
    mustChangePassword = false;
  }

  public Long getId() {
    return id;
  }

  public String getLoginEmail() {
    return loginEmail;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public Role getRole() {
    return role;
  }

  public String getDisplayName() {
    return displayName;
  }

  public boolean isActive() {
    return active;
  }

  public boolean isMustChangePassword() {
    return mustChangePassword;
  }

  public int getFailedLoginCount() {
    return failedLoginCount;
  }

  public Instant getLockedUntil() {
    return lockedUntil;
  }

  public Instant getLastLoginAt() {
    return lastLoginAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
