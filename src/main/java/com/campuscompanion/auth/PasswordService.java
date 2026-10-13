package com.campuscompanion.auth;

import com.campuscompanion.audit.AuditAction;
import com.campuscompanion.audit.AuditService;
import com.campuscompanion.auth.dto.ChangePasswordRequest;
import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import com.campuscompanion.common.error.FieldErrorDetail;
import com.campuscompanion.common.error.ValidationException;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.student.StudentProfileService;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Changes a user's own password (FR-AUTH-003). The user proves who they are with the current
 * password, enters the new one twice, and the new one must pass {@link PasswordPolicy}. Only the
 * BCrypt hash is stored, and no password is ever logged.
 */
@Service
public class PasswordService {

  private static final Logger log = LoggerFactory.getLogger(PasswordService.class);

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final PasswordPolicy passwordPolicy;
  private final LoginAttemptService loginAttempts;
  private final StudentProfileService studentProfiles;
  private final AuditService audit;

  public PasswordService(
      UserRepository users,
      PasswordEncoder passwordEncoder,
      PasswordPolicy passwordPolicy,
      LoginAttemptService loginAttempts,
      StudentProfileService studentProfiles,
      AuditService audit) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.passwordPolicy = passwordPolicy;
    this.loginAttempts = loginAttempts;
    this.studentProfiles = studentProfiles;
    this.audit = audit;
  }

  /**
   * Replaces the password of the logged-in user and clears the "must change password" flag.
   *
   * @throws ValidationException naming the field, if the current password is wrong, the two new
   *     entries differ, or the new password breaks the policy
   */
  @Transactional
  public void changePassword(CurrentUser currentUser, ChangePasswordRequest request) {
    User user =
        users
            .findById(currentUser.id())
            .filter(User::isActive)
            .orElseThrow(
                () -> new ApiException(ErrorCode.AUTH_REQUIRED, "Please log in to continue."));
    loginAttempts.assertNotBlocked(user.getLoginEmail());

    if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
      // A wrong current password is a failed proof of identity, so it counts toward the block.
      loginAttempts.recordFailure(user.getLoginEmail());
      throw new ValidationException("currentPassword", "Current password is incorrect.");
    }

    List<FieldErrorDetail> errors = new ArrayList<>();
    if (!request.newPassword().equals(request.confirmNewPassword())) {
      errors.add(new FieldErrorDetail("confirmNewPassword", "The two passwords do not match."));
    }
    String rollNo = studentProfiles.rollNoOf(user.getId()).orElse(null);
    boolean sameAsCurrent = request.newPassword().equals(request.currentPassword());
    for (String problem :
        passwordPolicy.violations(
            request.newPassword(), user.getLoginEmail(), rollNo, sameAsCurrent)) {
      errors.add(new FieldErrorDetail("newPassword", problem));
    }
    if (!errors.isEmpty()) {
      throw new ValidationException(errors);
    }

    user.changePassword(passwordEncoder.encode(request.newPassword()));
    audit.recordPasswordEvent(AuditAction.PASSWORD_CHANGED, user.getId());
    log.info("Password changed for account id {}", user.getId());
  }
}
