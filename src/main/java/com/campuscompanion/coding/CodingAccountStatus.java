package com.campuscompanion.coding;

public enum CodingAccountStatus {
  /** Linked, waiting for the student to put the code in their profile and press Verify. */
  PENDING,
  /** Proven to be the student's; counts on the leaderboard. */
  VERIFIED
}
