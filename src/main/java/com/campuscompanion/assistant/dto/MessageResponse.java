package com.campuscompanion.assistant.dto;

import java.time.Instant;

/** A message of a chat. {@code role} is "user" or "ai". The text is plain text. */
public record MessageResponse(Long id, String role, String content, Instant createdAt) {}
