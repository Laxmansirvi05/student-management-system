package com.campuscompanion.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Whether the bundled course question banks are loaded at start-up (on by default). */
@ConfigurationProperties(prefix = "app.question-bank")
public record QuestionBankProperties(boolean enabled) {}
