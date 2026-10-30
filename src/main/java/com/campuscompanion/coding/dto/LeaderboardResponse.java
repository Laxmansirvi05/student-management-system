package com.campuscompanion.coding.dto;

import java.util.List;

/** One page of the students' leaderboard, and the student's own standing. */
public record LeaderboardResponse(
    List<LeaderboardEntry> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    MyStanding me) {}
