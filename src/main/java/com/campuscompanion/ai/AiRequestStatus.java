package com.campuscompanion.ai;

/**
 * How an AI request ended. Only SUCCESS and DECLINED count toward the daily limits; a failure or a
 * timeout does not use up a student's requests.
 */
public enum AiRequestStatus {
  SUCCESS,
  FAILED,
  TIMEOUT,
  DECLINED;

  public boolean countsTowardLimits() {
    return this == SUCCESS || this == DECLINED;
  }
}
