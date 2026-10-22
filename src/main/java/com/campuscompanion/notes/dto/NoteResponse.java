package com.campuscompanion.notes.dto;

import java.time.Instant;
import java.util.List;

/**
 * A note. {@code sharedPermission} is set only when the note reached this student through a share
 * rather than ownership, so the editor knows to go read-only. {@code sharedBy} then names the
 * owner.
 */
public record NoteResponse(
    Long id,
    String title,
    String content,
    String source,
    Long folderId,
    List<String> tags,
    boolean pinned,
    boolean archived,
    Instant deletedAt,
    Instant createdAt,
    Instant updatedAt,
    NoteFolderRef folder,
    boolean shared,
    String sharedPermission,
    String sharedBy) {}
