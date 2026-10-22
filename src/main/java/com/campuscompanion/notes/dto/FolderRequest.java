package com.campuscompanion.notes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A new folder. */
public record FolderRequest(
    @NotBlank(message = "A folder needs a name.")
        @Size(max = 60, message = "A folder name is at most 60 characters.")
        String name,
    String color,
    @Size(max = 40) String icon,
    Long parentId) {}
