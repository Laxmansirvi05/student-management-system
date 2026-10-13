package com.campuscompanion.student;

import com.campuscompanion.common.error.ResourceNotFoundException;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.student.dto.StudentProfileResponse;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read access to student profiles: a student's own profile page, and lookups for other features.
 */
@Service
public class StudentProfileService {

  private final StudentProfileRepository profiles;

  public StudentProfileService(StudentProfileRepository profiles) {
    this.profiles = profiles;
  }

  /** The roll number of the student who owns the account, or empty for an admin account. */
  @Transactional(readOnly = true)
  public Optional<String> rollNoOf(Long userId) {
    return profiles.findByUserId(userId).map(StudentProfile::getRollNo);
  }

  /**
   * The profile of the logged-in student (FR-STU-002). The student is found from the session's
   * account ID and from nothing else, so one student can never read another's profile.
   *
   * @throws ResourceNotFoundException if the account has no student profile
   */
  @Transactional(readOnly = true)
  public StudentProfileResponse ownProfile(CurrentUser currentUser) {
    StudentProfile profile =
        profiles.findByUserId(currentUser.id()).orElseThrow(ResourceNotFoundException::new);
    return new StudentProfileResponse(
        profile.getRollNo(),
        profile.getUser().getLoginEmail(),
        profile.getFullName(),
        profile.getBranch(),
        profile.getCurrentSemester(),
        profile.getSection(),
        profile.getAdmissionYear(),
        profile.getPhone());
  }

  /** True once at least one student has been provisioned. */
  @Transactional(readOnly = true)
  public boolean anyStudentExists() {
    return profiles.count() > 0;
  }
}
