package com.campuscompanion.resource.dto;

import java.time.Instant;

/** A short-lived link to one file, and the moment it stops working. */
public record DownloadUrlResponse(String url, Instant expiresAt) {}
