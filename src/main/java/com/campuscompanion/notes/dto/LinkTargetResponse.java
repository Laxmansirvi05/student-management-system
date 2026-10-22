package com.campuscompanion.notes.dto;

/** Something a note can link to. {@code label} is null when the target no longer exists. */
public record LinkTargetResponse(String type, Long id, String label, String sublabel) {}
