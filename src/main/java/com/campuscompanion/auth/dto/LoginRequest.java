package com.campuscompanion.auth.dto;

import com.campuscompanion.auth.EmailNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The body of a login request. The email is normalised as soon as the request is read, so the
 * validation and the lookup both see the trimmed, lower-case value. The password is never altered.
 */
public record LoginRequest(
    @NotBlank(message = "Enter your email address.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 254, message = "Enter a valid email address.")
        String email,
    @NotBlank(message = "Enter your password.") @Size(max = 72, message = "Enter your password.")
        String password) {

  public LoginRequest {
    email = EmailNormalizer.normalise(email);
  }

  /** Keeps the password out of logs and error messages. */
  @Override
  public String toString() {
    return "LoginRequest[email=" + email + "]";
  }
}
