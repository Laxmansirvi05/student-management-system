package com.campuscompanion.progress.dto;

import com.campuscompanion.progress.PerformanceLevel;
import java.math.BigDecimal;
import java.util.List;

/**
 * A student's progress: how many tests they submitted and their average score, their level in each
 * unit they have practised, and what to revise next.
 */
public record ProgressResponse(
    int testsTaken,
    BigDecimal averagePercent,
    List<UnitProgress> units,
    List<RecommendationResponse> recommendations) {

  public record UnitProgress(
      Long subjectId,
      String subjectCode,
      String subjectName,
      Long unitId,
      int unitNo,
      String unitTitle,
      int answered,
      int correct,
      BigDecimal accuracyPercent,
      PerformanceLevel level) {}
}
