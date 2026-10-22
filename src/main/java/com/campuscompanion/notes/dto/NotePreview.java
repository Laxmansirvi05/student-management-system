package com.campuscompanion.notes.dto;

import java.time.Instant;

/** What a folder card and a note card draw: not the whole document. */
public record NotePreview(
    Long id, String title, String snippet, String imageUrl, Instant updatedAt, boolean pinned) {}
