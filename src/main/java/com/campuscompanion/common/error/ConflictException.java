package com.campuscompanion.common.error;

/** The request clashes with existing data, for example a duplicate roll number. */
public class ConflictException extends ApiException {

  public ConflictException(String detail) {
    super(ErrorCode.CONFLICT, detail);
  }
}
