package com.campuscompanion.progress.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * A unit the student should revise, with their accuracy in it and up to three resources to read.
 */
public record RecommendationResponse(
    Long subjectId,
    String subjectCode,
    Long unitId,
    /** Null for a whole-subject test. */
    Integer unitNo,
    String unitTitle,
    BigDecimal accuracyPercent,
    List<ResourceLink> resources) {

  public record ResourceLink(Long id, String title, String type) {}
}
