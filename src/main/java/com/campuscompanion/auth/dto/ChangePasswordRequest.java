package com.campuscompanion.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The body of a password change: the current password and the new one entered twice. The values are
 * never trimmed or altered.
 */
public record ChangePasswordRequest(
    @NotBlank(message = "Enter your current password.")
        @Size(max = 72, message = "Enter your current password.")
        String currentPassword,
    @NotBlank(message = "Enter a new password.") String newPassword,
    @NotBlank(message = "Enter the new password again.") String confirmNewPassword) {

  /** Keeps every password out of logs and error messages. */
  @Override
  public String toString() {
    return "ChangePasswordRequest[]";
  }
}
