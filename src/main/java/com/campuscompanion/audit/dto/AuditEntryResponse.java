package com.campuscompanion.audit.dto;

import java.time.Instant;
import tools.jackson.databind.JsonNode;

/**
 * One audit entry as an admin sees it. The actor and the subject are shown as an email address or
 * roll number where one is known. The old and new values are only what {@code AuditSnapshot}
 * allows, so there is no password material in them.
 */
public record AuditEntryResponse(
    long id,
    Instant createdAt,
    String action,
    String actor,
    String entityType,
    String entityId,
    String subject,
    JsonNode oldValue,
    JsonNode newValue,
    String ipAddress) {}
