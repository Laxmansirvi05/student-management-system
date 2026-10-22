package com.campuscompanion.notes.dto;

public record ShareRecipientResponse(
    Long id, Long recipientId, String name, String rollNo, String permission) {}
