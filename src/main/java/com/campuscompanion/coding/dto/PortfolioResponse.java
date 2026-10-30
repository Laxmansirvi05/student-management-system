package com.campuscompanion.coding.dto;

import java.util.List;

/** A student's coding portfolio (FR-COD-002): the accounts, the total and the journey. */
public record PortfolioResponse(
    int totalSolved,
    int verifiedAccounts,
    boolean showOnLeaderboard,
    List<CodingAccountResponse> accounts,
    List<JourneyPoint> journey) {}
