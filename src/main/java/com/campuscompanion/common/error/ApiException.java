package com.campuscompanion.common.error;

/**
 * Base class for every failure the application reports on purpose. Services throw a subclass (or
 * this class with a specific code); {@link GlobalExceptionHandler} turns it into the response. The
 * detail text is shown to the user, so it must be plain and must not contain internals.
 */
public class ApiException extends RuntimeException {

  private final ErrorCode code;

  public ApiException(ErrorCode code, String detail) {
    super(detail);
    this.code = code;
  }

  public ErrorCode getCode() {
    return code;
  }
}
