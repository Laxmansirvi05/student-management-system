package com.campuscompanion.notes.dto;

import java.time.Instant;

/** An image or file in a note. {@code url} is this application's own address for the file. */
public record AttachmentResponse(
    Long id,
    Long noteId,
    String noteTitle,
    String url,
    String name,
    String mimeType,
    long sizeBytes,
    String kind,
    Instant createdAt) {}
