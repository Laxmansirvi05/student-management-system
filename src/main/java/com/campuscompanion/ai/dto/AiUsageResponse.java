package com.campuscompanion.ai.dto;

import java.time.Instant;

/**
 * A student's AI requests today. "enabled" is false when the assistant is switched off or today's
 * limit for everyone is reached. "demo" is true when the built-in demo assistant answers instead of
 * a real AI provider.
 */
public record AiUsageResponse(
    int dailyLimit,
    int usedToday,
    int remainingToday,
    Instant resetsAt,
    boolean enabled,
    boolean demo) {}
