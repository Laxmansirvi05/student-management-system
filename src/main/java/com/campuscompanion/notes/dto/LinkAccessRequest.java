package com.campuscompanion.notes.dto;

import jakarta.validation.constraints.NotBlank;

/** Who may open the share link: "private" (nobody) or "view" (any signed-in student). */
public record LinkAccessRequest(@NotBlank String linkAccess) {}
