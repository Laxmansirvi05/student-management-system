package com.campuscompanion.coding.dto;

/** The student's own place: the rank is null while they are not on the leaderboard. */
public record MyStanding(
    boolean showOnLeaderboard, Integer rank, int totalSolved, int verifiedAccounts) {}
