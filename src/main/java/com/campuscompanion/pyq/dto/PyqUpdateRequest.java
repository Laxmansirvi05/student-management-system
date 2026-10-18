package com.campuscompanion.pyq.dto;

import com.campuscompanion.pyq.PyqStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** An admin's change to a paper's details, and whether students can see it. */
public record PyqUpdateRequest(
    @NotNull(message = "Enter the details.") @Valid PyqMetadata metadata,
    @NotNull(message = "Choose whether students can see it.") PyqStatus status) {}
