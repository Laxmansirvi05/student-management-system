package com.campuscompanion.student;

import com.campuscompanion.audit.AuditAction;
import com.campuscompanion.audit.AuditService;
import com.campuscompanion.audit.AuditSnapshot;
import com.campuscompanion.auth.StudentAccountService;
import com.campuscompanion.auth.User;
import com.campuscompanion.common.error.ResourceNotFoundException;
import com.campuscompanion.common.web.PageResponse;
import com.campuscompanion.student.dto.AdminStudentResponse;
import com.campuscompanion.student.dto.StudentUpdateRequest;
import com.campuscompanion.student.dto.TemporaryPasswordResponse;
import java.time.Clock;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Managing student accounts (FR-ADM-001): search, edit, deactivate, reactivate and reset the
 * password. Creating a student is {@link StudentProvisioningService}. Students are never deleted.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminStudentService {

  private static final String STUDENT = "STUDENT_PROFILE";
  private static final String ACCOUNT = "USER";

  private final StudentProfileRepository profiles;
  private final StudentAccountService accounts;
  private final AuditService audit;
  private final Clock clock;

  public AdminStudentService(
      StudentProfileRepository profiles,
      StudentAccountService accounts,
      AuditService audit,
      Clock clock) {
    this.profiles = profiles;
    this.accounts = accounts;
    this.audit = audit;
    this.clock = clock;
  }

  /** One page of students in roll-number order. Every filter is optional. */
  @Transactional(readOnly = true)
  public PageResponse<AdminStudentResponse> search(
      String branch, Integer semester, String section, String query, int page, int size) {
    String search =
        query == null || query.isBlank()
            ? null
            : "%" + query.strip().toLowerCase(Locale.ROOT) + "%";
    return PageResponse.of(
        profiles
            .search(
                blankToNull(branch),
                semester,
                blankToNull(section),
                search,
                PageRequest.of(page, size, Sort.by("rollNo")))
            .map(profile -> StudentResponses.forAdmin(profile, clock.instant())));
  }

  @Transactional(readOnly = true)
  public AdminStudentResponse get(Long studentId) {
    return StudentResponses.forAdmin(find(studentId), clock.instant());
  }

  /** Edits the profile. An entry is written to the audit log only when something changed. */
  @Transactional
  public AdminStudentResponse update(Long studentId, StudentUpdateRequest request) {
    StudentProfile profile = find(studentId);
    AuditSnapshot.Profile before = snapshot(profile);
    profile.update(
        request.fullName().strip(),
        request.branch().strip(),
        request.currentSemester(),
        request.section().strip(),
        request.admissionYear(),
        request.phone() == null || request.phone().isBlank() ? null : request.phone().strip());
    profile.getUser().rename(profile.getFullName());
    AuditSnapshot.Profile after = snapshot(profile);
    if (!after.equals(before)) {
      audit.record(AuditAction.PROFILE_UPDATED, STUDENT, studentId, before, after);
    }
    return StudentResponses.forAdmin(profile, clock.instant());
  }

  /** Blocks login and ends the student's sessions at once. The password is kept. */
  @Transactional
  public void deactivate(Long studentId) {
    User account = find(studentId).getUser();
    if (account.isActive()) {
      accounts.deactivate(account.getId());
      audit.record(
          AuditAction.ACCOUNT_DEACTIVATED,
          ACCOUNT,
          account.getId(),
          new AuditSnapshot.AccountStatus(true),
          new AuditSnapshot.AccountStatus(false));
    }
  }

  /** Restores the account with the password it had. */
  @Transactional
  public void activate(Long studentId) {
    User account = find(studentId).getUser();
    if (!account.isActive()) {
      accounts.activate(account.getId());
      audit.record(
          AuditAction.ACCOUNT_ACTIVATED,
          ACCOUNT,
          account.getId(),
          new AuditSnapshot.AccountStatus(false),
          new AuditSnapshot.AccountStatus(true));
    }
  }

  /**
   * Issues a random temporary password. It ends the student's sessions, clears any block and forces
   * a change at the next login. It is never the roll number.
   */
  @Transactional
  public TemporaryPasswordResponse resetPassword(Long studentId) {
    User account = find(studentId).getUser();
    String temporaryPassword = accounts.resetPassword(account.getId());
    audit.recordPasswordEvent(AuditAction.PASSWORD_RESET, account.getId());
    return new TemporaryPasswordResponse(temporaryPassword);
  }

  private StudentProfile find(Long studentId) {
    return profiles.findById(studentId).orElseThrow(ResourceNotFoundException::new);
  }

  private static AuditSnapshot.Profile snapshot(StudentProfile profile) {
    return new AuditSnapshot.Profile(
        profile.getFullName(),
        profile.getBranch(),
        profile.getCurrentSemester(),
        profile.getSection(),
        profile.getAdmissionYear(),
        profile.getPhone());
  }

  private static String blankToNull(String text) {
    return text == null || text.isBlank() ? null : text.strip();
  }
}
