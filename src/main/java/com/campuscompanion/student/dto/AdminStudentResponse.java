package com.campuscompanion.student.dto;

import java.time.Instant;

/**
 * A student as an admin sees them: the profile plus the state of the login account. It never
 * carries a password. A null {@code lastLoginAt} means the account is still awaiting first login.
 */
public record AdminStudentResponse(
    Long id,
    String rollNo,
    String email,
    String fullName,
    String branch,
    int currentSemester,
    String section,
    int admissionYear,
    String phone,
    boolean active,
    boolean mustChangePassword,
    boolean locked,
    Instant lastLoginAt) {}
