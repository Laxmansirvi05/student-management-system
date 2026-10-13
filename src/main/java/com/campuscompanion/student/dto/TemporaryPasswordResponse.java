package com.campuscompanion.student.dto;

/**
 * The random temporary password of an admin reset. It is shown to the admin once and stored only as
 * a hash; the student must change it at the next login.
 */
public record TemporaryPasswordResponse(String temporaryPassword) {

  /** Never print the password, for example in a log line. */
  @Override
  public String toString() {
    return "TemporaryPasswordResponse[temporaryPassword=<hidden>]";
  }
}
