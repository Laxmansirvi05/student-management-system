package com.campuscompanion.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Synthetic demo data seeder switch. Used from task T1-10. */
@ConfigurationProperties(prefix = "app.seed")
public record SeedProperties(boolean demoEnabled) {}
