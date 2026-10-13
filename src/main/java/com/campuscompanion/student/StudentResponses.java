package com.campuscompanion.student;

import com.campuscompanion.auth.User;
import com.campuscompanion.student.dto.AdminStudentResponse;
import java.time.Instant;

/** Builds the responses that describe a student. Entities stop here. */
final class StudentResponses {

  private StudentResponses() {}

  /** A student as an admin sees them: the profile plus the state of the login account. */
  static AdminStudentResponse forAdmin(StudentProfile profile, Instant now) {
    User account = profile.getUser();
    return new AdminStudentResponse(
        profile.getId(),
        profile.getRollNo(),
        account.getLoginEmail(),
        profile.getFullName(),
        profile.getBranch(),
        profile.getCurrentSemester(),
        profile.getSection(),
        profile.getAdmissionYear(),
        profile.getPhone(),
        account.isActive(),
        account.isMustChangePassword(),
        account.isLocked(now),
        account.getLastLoginAt());
  }
}
