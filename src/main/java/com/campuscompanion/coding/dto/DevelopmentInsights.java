package com.campuscompanion.coding.dto;

import com.campuscompanion.coding.platform.PlatformDetails;
import java.util.List;
import java.util.Map;

/**
 * The development view of a portfolio, from the verified GitHub account.
 *
 * @param calendar contributions by ISO date
 * @param languages each language's share of the repositories, in whole percent, largest first
 */
public record DevelopmentInsights(
    String handle,
    String profileUrl,
    ActivitySummary contributions,
    Map<String, Integer> calendar,
    Map<String, Integer> languages,
    Integer stars,
    Integer commits,
    Integer pullRequests,
    Integer issues,
    Integer publicRepos,
    Integer followers,
    List<PlatformDetails.Project> projects) {}
