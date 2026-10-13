package com.campuscompanion.student.dto;

import java.time.Instant;

/** A link that shows the student's own photo for a few minutes. */
public record PhotoUrlResponse(String url, Instant expiresAt) {}
