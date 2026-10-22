package com.campuscompanion.complaint.dto;

import com.campuscompanion.complaint.ComplaintCategory;
import com.campuscompanion.complaint.ComplaintStatus;
import com.campuscompanion.complaint.ComplaintType;
import java.time.Instant;

/**
 * A complaint as a student sees it. {@code authorRollNo} is set only on a public complaint whose
 * author chose to show it; there is no other field that could name the author.
 */
public record ComplaintResponse(
    Long id,
    ComplaintType type,
    ComplaintCategory category,
    String title,
    String description,
    ComplaintStatus status,
    String adminResponse,
    Instant respondedAt,
    Instant createdAt,
    String authorRollNo,
    int supporters,
    boolean group,
    boolean supported,
    boolean mine) {}
