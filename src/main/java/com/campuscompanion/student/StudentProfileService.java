package com.campuscompanion.student;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read access to student profiles for other features. */
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

  /** True once at least one student has been provisioned. */
  @Transactional(readOnly = true)
  public boolean anyStudentExists() {
    return profiles.count() > 0;
  }
}
