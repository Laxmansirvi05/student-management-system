package com.campuscompanion.common.error;

import java.util.List;

/**
 * One or more fields are invalid for a reason only a service can check, for example a wrong current
 * password. It produces the same response as a failed Bean Validation check.
 */
public class ValidationException extends ApiException {

  private final List<FieldErrorDetail> errors;

  public ValidationException(List<FieldErrorDetail> errors) {
    super(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.");
    this.errors = List.copyOf(errors);
  }

  public ValidationException(String field, String message) {
    this(List.of(new FieldErrorDetail(field, message)));
  }

  public List<FieldErrorDetail> getErrors() {
    return errors;
  }
}
