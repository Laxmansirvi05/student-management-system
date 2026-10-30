package com.campuscompanion.coding.dto;

/** A calendar added up: the total, the days with any activity, and the longest and current runs. */
public record ActivitySummary(int total, int activeDays, int maxStreak, int currentStreak) {}
