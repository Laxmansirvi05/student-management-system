package com.campuscompanion.assistant.dto;

import java.time.Instant;

/**
 * A message of a chat. {@code role} is "user" or "ai". The text is kept exactly as it was shown:
 * plain text for a Quick Answer, Markdown with diagram blocks for an Explanatory Answer. For an
 * answer, {@code style} is its mode ("quick" or "explain") and {@code replyTo} the ID of the
 * question it answers; both are null for a question.
 */
public record MessageResponse(
    Long id, String role, String content, Instant createdAt, String style, Long replyTo) {}
