package com.campuscompanion.resource.dto;

import com.campuscompanion.resource.ResourceStatus;
import com.campuscompanion.resource.ResourceType;
import java.time.Instant;

/**
 * A resource in a list or on its own page. The storage path is never part of it: the file is
 * reached only through a signed link. {@code status} is sent to admins only.
 */
public record ResourceResponse(
    Long id,
    String title,
    String description,
    Long subjectId,
    String subjectCode,
    String subjectName,
    Long unitId,
    Integer unitNo,
    String unitTitle,
    ResourceType type,
    long fileSizeBytes,
    Instant createdAt,
    boolean bookmarked,
    QuestionPaperDetails questionPaper,
    ResourceStatus status) {}
