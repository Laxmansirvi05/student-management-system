package com.campuscompanion.student;

import com.campuscompanion.auth.StudentAccountService;
import com.campuscompanion.auth.User;
import com.campuscompanion.common.config.IdentityProperties;
import com.campuscompanion.common.error.ConflictException;
import com.campuscompanion.common.error.FieldErrorDetail;
import com.campuscompanion.common.error.ValidationException;
import com.campuscompanion.student.dto.AdminStudentResponse;
import com.campuscompanion.student.dto.StudentCreateRequest;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only code that creates a student (FR-ADM-001, FR-AUTH-006). In one transaction it checks the
 * roster details, generates the login email from the roll number and the configured domain, creates
 * the account with the roll number as its temporary password, and creates the linked profile. If
 * any step fails, nothing is saved.
 *
 * <p>Using the roll number as the first password is security exception EX-AUTH-01 (ADR 0008). The
 * password is never returned or logged, and the server forces a change at first login.
 */
@Service
public class StudentProvisioningService {

  private static final Logger log = LoggerFactory.getLogger(StudentProvisioningService.class);

  private final StudentRosterValidator validator;
  private final StudentProfileRepository profiles;
  private final StudentAccountService accounts;
  private final IdentityProperties identity;
  private final Clock clock;

  public StudentProvisioningService(
      StudentRosterValidator validator,
      StudentProfileRepository profiles,
      StudentAccountService accounts,
      IdentityProperties identity,
      Clock clock) {
    this.validator = validator;
    this.profiles = profiles;
    this.accounts = accounts;
    this.identity = identity;
    this.clock = clock;
  }

  /**
   * Creates the account and the profile of one student.
   *
   * @throws ValidationException naming each invalid field
   * @throws ConflictException if the roll number or the generated email is already taken
   */
  @Transactional
  public AdminStudentResponse provision(StudentCreateRequest request) {
    List<FieldErrorDetail> problems = validator.violations(request);
    if (!problems.isEmpty()) {
      throw new ValidationException(problems);
    }
    String rollNo = request.rollNo().toUpperCase(Locale.ROOT);
    if (profiles.existsByRollNo(rollNo)) {
      throw new ConflictException("A student with this roll number already exists.");
    }
    String fullName = request.fullName().strip();
    User account = accounts.createStudentAccount(emailFor(rollNo), rollNo, fullName);
    StudentProfile profile =
        profiles.save(
            new StudentProfile(
                account,
                rollNo,
                fullName,
                request.branch().strip(),
                request.currentSemester(),
                request.section().strip(),
                request.admissionYear(),
                blankToNull(request.phone())));
    log.info("Provisioned a student, student id {}.", profile.getId());
    return toResponse(profile, account);
  }

  /** The institutional email: the roll number in lower case, an at sign, the configured domain. */
  private String emailFor(String rollNo) {
    return rollNo.toLowerCase(Locale.ROOT) + "@" + identity.studentEmailDomain();
  }

  private static String blankToNull(String text) {
    return text == null || text.isBlank() ? null : text.strip();
  }

  private AdminStudentResponse toResponse(StudentProfile profile, User account) {
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
        account.isLocked(clock.instant()),
        account.getLastLoginAt());
  }
}
