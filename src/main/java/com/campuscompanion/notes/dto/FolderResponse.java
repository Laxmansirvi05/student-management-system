package com.campuscompanion.notes.dto;

import java.util.List;

/** A folder with its exact note count and up to five previews for the card that fans them out. */
public record FolderResponse(
    Long id,
    String name,
    String color,
    String icon,
    Long parentId,
    int position,
    int noteCount,
    List<NotePreview> previews) {}
