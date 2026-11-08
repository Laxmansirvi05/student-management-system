package com.campuscompanion.common.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One clock for the application, so that tests can replace it with a fixed time. It ticks in whole
 * microseconds, the precision PostgreSQL keeps: on Linux the system clock gives nanoseconds, and a
 * time returned before saving would then differ from the same time read back from the database.
 */
@Configuration
public class ClockConfig {

  @Bean
  public Clock clock() {
    return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1000));
  }
}
