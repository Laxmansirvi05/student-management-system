package com.campuscompanion.coding.dto;

import com.campuscompanion.coding.platform.CodingPlatform;
import java.util.List;

/** One row of the students' leaderboard (FR-COD-004). */
public record LeaderboardEntry(
    int rank,
    String fullName,
    String branch,
    int semester,
    int totalSolved,
    List<CodingPlatform> platforms,
    boolean me) {}
