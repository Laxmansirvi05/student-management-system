package com.campuscompanion.resource.dto;

import java.time.Instant;

/**
 * A report on a resource. A student sees their own report without {@code reportedBy}; an
 * administrator also sees the resource title and who reported it.
 */
public record ResourceReportResponse(
    Long id,
    Long resourceId,
    String resourceTitle,
    String reason,
    String status,
    String reportedBy,
    Instant createdAt,
    Instant resolvedAt) {}
