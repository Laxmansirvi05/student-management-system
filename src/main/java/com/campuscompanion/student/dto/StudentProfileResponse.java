package com.campuscompanion.student.dto;

/**
 * A student's own profile, as shown on the profile page (FR-STU-002). The email is the
 * institutional login email; it is an identifier and is not verified. The phone and the bio may be
 * null. The photo itself is fetched through its own short-lived link.
 */
public record StudentProfileResponse(
    String rollNo,
    String email,
    String fullName,
    String branch,
    int currentSemester,
    String section,
    int admissionYear,
    String phone,
    String bio,
    boolean hasPhoto) {}
