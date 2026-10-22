package com.campuscompanion.notes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Link a note to something. {@code targetType} is note, folder or resource. */
public record AddLinkRequest(@NotBlank String targetType, @NotNull Long targetId) {}
