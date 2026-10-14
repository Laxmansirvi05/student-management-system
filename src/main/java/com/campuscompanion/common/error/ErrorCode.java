package com.campuscompanion.common.error;

import org.springframework.http.HttpStatus;

/**
 * The stable error codes of the API, each with its HTTP status and a short title. The list and the
 * meaning of every code are defined in docs/09-api-contract.md section 4. The frontend switches on
 * the code, so a code is never renamed.
 */
public enum ErrorCode {
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),
  AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "Login required"),
  AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Incorrect email or password"),
  AUTH_PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "Password change required"),
  ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access denied"),
  CSRF_INVALID(HttpStatus.FORBIDDEN, "Security token missing or invalid"),
  NOT_FOUND(HttpStatus.NOT_FOUND, "Not found"),
  CONFLICT(HttpStatus.CONFLICT, "Conflict"),
  QUIZ_NOT_ENOUGH_QUESTIONS(HttpStatus.CONFLICT, "Not enough questions"),
  QUIZ_ALREADY_SUBMITTED(HttpStatus.CONFLICT, "Attempt already submitted"),
  QUIZ_ATTEMPT_EXPIRED(HttpStatus.CONFLICT, "Attempt expired"),
  UPLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "File too large"),
  REQUEST_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "Request too large"),
  UPLOAD_INVALID_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported file type"),
  AI_REQUEST_DECLINED(HttpStatus.UNPROCESSABLE_CONTENT, "Request declined"),
  AUTH_ACCOUNT_LOCKED(HttpStatus.LOCKED, "Too many attempts"),
  RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
  AI_QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Daily AI limit reached"),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong"),
  AI_INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "Assistant unavailable"),
  AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Assistant unavailable"),
  STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "File storage unavailable"),
  AI_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "Assistant unavailable");

  private final HttpStatus status;
  private final String title;

  ErrorCode(HttpStatus status, String title) {
    this.status = status;
    this.title = title;
  }

  public HttpStatus status() {
    return status;
  }

  public String title() {
    return title;
  }
}
