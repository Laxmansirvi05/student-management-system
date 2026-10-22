package com.campuscompanion.complaint;

/** Who sees a complaint and who sees the student behind it (ADR 0013). */
public enum ComplaintType {
  /** Every student and the administrator; the student chooses whether other students see them. */
  PUBLIC,
  /** Only the administrator, who sees the student. */
  PRIVATE,
  /** Only the administrator, who never sees the student. */
  SECURE
}
