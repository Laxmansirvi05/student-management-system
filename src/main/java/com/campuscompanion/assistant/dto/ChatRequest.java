package com.campuscompanion.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * One message to the assistant. {@code conversationId} continues an earlier chat; without it a new
 * chat is started. {@code mode} is "quick" for the robot's short answers.
 */
public record ChatRequest(
    Long conversationId,
    @NotBlank(message = "Write a message.")
        @Size(max = 4000, message = "A message is at most 4,000 characters.")
        String message,
    String mode) {}
