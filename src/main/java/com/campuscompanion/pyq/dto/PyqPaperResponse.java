package com.campuscompanion.pyq.dto;

import com.campuscompanion.pyq.PyqExamKind;
import java.time.Instant;

/**
 * A paper as the page shows it. The storage path is never in here: the file is reached only through
 * a short-lived link.
 */
public record PyqPaperResponse(
    long id,
    String branch,
    String subject,
    int year,
    int month,
    String monthName,
    PyqExamKind kind,
    Integer midNumber,
    String regulation,
    String courseCode,
    int variant,
    long sizeBytes,
    int downloads,
    boolean bookmarked,
    String status,
    Instant createdAt,
    String downloadName) {}
