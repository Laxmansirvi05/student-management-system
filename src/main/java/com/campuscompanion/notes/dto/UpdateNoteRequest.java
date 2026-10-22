package com.campuscompanion.notes.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * A change to a note: any of the fields may be present. {@code unfile} moves the note out of its
 * folder, {@code restore} brings it back from the Trash. A shared editor may change only the title
 * and the content.
 */
public record UpdateNoteRequest(
    @Size(max = 200, message = "A title is at most 200 characters.") String title,
    String content,
    @Size(max = 20, message = "A note has at most 20 tags.") List<@Size(max = 40) String> tags,
    Boolean pinned,
    Boolean archived,
    Long folderId,
    Boolean unfile,
    Boolean restore) {}
