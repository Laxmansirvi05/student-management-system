package com.campuscompanion.coding.dto;

import com.campuscompanion.coding.platform.CodingPlatform;
import java.time.Instant;
import java.util.List;

/** One student on the admin leaderboard (FR-COD-005). */
public record AdminLeaderboardEntry(
    int rank,
    Long studentId,
    String rollNo,
    String fullName,
    String branch,
    int semester,
    String section,
    int totalSolved,
    int solvedLast30Days,
    int verifiedAccounts,
    int pendingAccounts,
    List<CodingPlatform> platforms,
    boolean showOnLeaderboard,
    Instant lastSyncedAt) {}
