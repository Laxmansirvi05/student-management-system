package com.campuscompanion.audit;

/** What happened, as stored in the audit log. The list is docs/08-database-design.md 12.1. */
public enum AuditAction {
  MARKS_UPDATED,
  ATTENDANCE_CORRECTED,
  RESULT_UPDATED,
  ACCOUNT_CREATED,
  ACCOUNT_DEACTIVATED,
  ACCOUNT_ACTIVATED,
  PROFILE_UPDATED,
  PASSWORD_RESET,
  PASSWORD_CHANGED;

  /** Password events record only that they happened: no old value and no new value, ever. */
  public boolean isPasswordEvent() {
    return this == PASSWORD_RESET || this == PASSWORD_CHANGED;
  }
}
