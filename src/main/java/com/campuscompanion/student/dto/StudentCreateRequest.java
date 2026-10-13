package com.campuscompanion.student.dto;

/**
 * Roster details for one new student. There is no email and no password here: the login email is
 * generated from the roll number, and the temporary password is the roll number itself.
 */
public record StudentCreateRequest(
    String rollNo,
    String fullName,
    String branch,
    Integer currentSemester,
    String section,
    Integer admissionYear,
    String phone) {}
