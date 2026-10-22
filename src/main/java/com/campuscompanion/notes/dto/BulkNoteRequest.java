package com.campuscompanion.notes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * One action on many notes. The actions are move, pin, unpin, archive, unarchive, trash, restore,
 * delete and empty_trash; {@code ids} is not needed for empty_trash. A move without a folder moves
 * the notes out of their folders.
 */
public record BulkNoteRequest(
    @NotBlank String action,
    @Size(max = 200, message = "Choose at most 200 notes.") List<Long> ids,
    Long folderId) {}
