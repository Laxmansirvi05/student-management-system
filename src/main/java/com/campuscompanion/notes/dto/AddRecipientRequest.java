package com.campuscompanion.notes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Share a note with a classmate, named by roll number or college email. */
public record AddRecipientRequest(
    @NotBlank(message = "Enter a roll number or college email.") @Size(max = 120) String identifier,
    String permission) {}
