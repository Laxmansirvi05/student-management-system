package com.campuscompanion.complaint;

public enum ComplaintStatus {
  OPEN,
  IN_REVIEW,
  RESOLVED,
  /** A rejected public complaint leaves the public board. */
  REJECTED
}
