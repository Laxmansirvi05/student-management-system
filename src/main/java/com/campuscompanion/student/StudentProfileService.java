package com.campuscompanion.student;

import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import com.campuscompanion.common.error.ResourceNotFoundException;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.student.dto.StudentProfileResponse;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
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

  /**
   * The logged-in student, for features that hold records per student. An account without a student
   * profile (an admin) is refused, so an admin cannot use the student-only endpoints.
   */
  @Transactional(readOnly = true)
  public StudentContext currentStudent(CurrentUser currentUser) {
    return profiles
        .findByUserId(currentUser.id())
        .map(StudentProfileService::toContext)
        .orElseThrow(
            () ->
                new ApiException(
                    ErrorCode.ACCESS_DENIED, "You do not have permission to do this."));
  }

  /** The students of one class, in roll-number order. */
  @Transactional(readOnly = true)
  public List<StudentContext> studentsOfClass(String branch, int semester, String section) {
    return profiles
        .findByBranchAndCurrentSemesterAndSectionOrderByRollNo(branch, semester, section)
        .stream()
        .map(StudentProfileService::toContext)
        .toList();
  }

  /** The students of a branch and semester, across every section, in roll-number order. */
  @Transactional(readOnly = true)
  public List<StudentContext> studentsOfSemester(String branch, int semester) {
    return profiles.findByBranchAndCurrentSemesterOrderByRollNo(branch, semester).stream()
        .map(StudentProfileService::toContext)
        .toList();
  }

  @Transactional(readOnly = true)
  public Optional<StudentContext> findStudent(Long studentId) {
    return profiles.findById(studentId).map(StudentProfileService::toContext);
  }

  /** Students by ID, for naming the student of a record. */
  @Transactional(readOnly = true)
  public Map<Long, StudentContext> studentsByIds(Collection<Long> studentIds) {
    return profiles.findAllById(studentIds).stream()
        .map(StudentProfileService::toContext)
        .collect(Collectors.toMap(StudentContext::studentId, Function.identity()));
  }

  private static StudentContext toContext(StudentProfile profile) {
    return new StudentContext(
        profile.getId(),
        profile.getRollNo(),
        profile.getFullName(),
        profile.getBranch(),
        profile.getCurrentSemester(),
        profile.getSection());
  }

  /** True once at least one student has been provisioned. */
  @Transactional(readOnly = true)
  public boolean anyStudentExists() {
    return profiles.count() > 0;
  }
}
