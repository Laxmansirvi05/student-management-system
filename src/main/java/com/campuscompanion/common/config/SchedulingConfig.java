package com.campuscompanion.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Switches on scheduled jobs. Each job checks its own setting before doing anything. */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
