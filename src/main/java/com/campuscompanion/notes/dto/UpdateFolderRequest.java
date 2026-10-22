package com.campuscompanion.notes.dto;

import jakarta.validation.constraints.Size;

/**
 * A change to a folder: any field may be present. {@code toTopLevel} takes it out of its parent.
 */
public record UpdateFolderRequest(
    @Size(max = 60, message = "A folder name is at most 60 characters.") String name,
    String color,
    @Size(max = 40) String icon,
    Long parentId,
    Boolean toTopLevel,
    Integer position) {}
