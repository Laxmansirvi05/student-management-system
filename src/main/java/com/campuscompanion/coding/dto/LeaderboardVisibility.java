package com.campuscompanion.coding.dto;

import jakarta.validation.constraints.NotNull;

/** Whether the student shows on the students' leaderboard. */
public record LeaderboardVisibility(@NotNull(message = "Choose yes or no.") Boolean show) {}
