package com.campuscompanion.notes.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

/** A new note. It needs a title or some content. {@code source} is "manual" or "robot". */
public record CreateNoteRequest(
    @Size(max = 200, message = "A title is at most 200 characters.") String title,
    String content,
    String source,
    Long folderId,
    @Size(max = 20, message = "A note has at most 20 tags.") List<@Size(max = 40) String> tags) {}
