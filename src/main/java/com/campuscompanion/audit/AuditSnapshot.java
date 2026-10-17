package com.campuscompanion.audit;

import java.math.BigDecimal;

/**
 * The only values that may be written into an audit entry. Each record lists its fields one by one,
 * so a field added to an entity later cannot leak into the log by accident, and none of them has a
 * field for a password or a hash (docs/08-database-design.md 12.1).
 */
public sealed interface AuditSnapshot {

  record Marks(BigDecimal marksObtained, boolean absent) implements AuditSnapshot {}

  record Attendance(String status) implements AuditSnapshot {}

  record AttendanceTotals(int classesHeld, int classesAttended) implements AuditSnapshot {}

  record Result(
      BigDecimal sgpa,
      int creditsRegistered,
      int creditsEarned,
      String resultStatus,
      boolean published)
      implements AuditSnapshot {}

  record Account(String role, String loginEmail, boolean active) implements AuditSnapshot {}

  record AccountStatus(boolean active) implements AuditSnapshot {}

  record Profile(
      String fullName,
      String branch,
      int currentSemester,
      String section,
      int admissionYear,
      String phone)
      implements AuditSnapshot {}
}
