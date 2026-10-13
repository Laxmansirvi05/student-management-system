package com.campuscompanion.common.config;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * The college's local time. Timestamps are stored in UTC, but "today" and "now" for a class or a
 * deadline mean the date and time in India (docs/04-TRD.md TR-JAVA-7).
 */
public final class CampusTime {

  public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

  private CampusTime() {}

  public static LocalDate today(Clock clock) {
    return LocalDate.now(clock.withZone(ZONE));
  }

  public static LocalTime now(Clock clock) {
    return LocalTime.now(clock.withZone(ZONE));
  }
}
