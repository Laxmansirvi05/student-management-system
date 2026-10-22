package com.campuscompanion.assistant.dto;

import java.time.Instant;

public record ConversationResponse(Long id, String title, String source, Instant updatedAt) {}
