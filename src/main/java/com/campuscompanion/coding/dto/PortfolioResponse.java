package com.campuscompanion.coding.dto;

import java.util.List;

/**
 * A student's coding portfolio (FR-COD-002): who it belongs to, the accounts, the total, the
 * journey, and the problem-solving and development views. {@code development} is null without a
 * verified GitHub account.
 */
public record PortfolioResponse(
    StudentCard student,
    int totalSolved,
    int verifiedAccounts,
    boolean showOnLeaderboard,
    List<CodingAccountResponse> accounts,
    List<JourneyPoint> journey,
    ProblemSolvingInsights problemSolving,
    DevelopmentInsights development) {}
