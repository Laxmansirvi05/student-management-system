package com.campuscompanion.assistant.dto;

import jakarta.validation.constraints.Size;

/**
 * One message to the assistant. {@code conversationId} continues an earlier chat; without it a new
 * chat is started. {@code mode} is "quick" for the robot's chats. {@code style} is the answer mode:
 * "quick" (the default) for a short answer, "explain" for a structured explanation. The message may
 * be blank only when a picture is attached. {@code questionId} asks for the answer, in the given
 * style, to a question already in the chat: the saved answer is returned if there is one, and
 * otherwise one is written and saved; no second question is stored.
 */
public record ChatRequest(
    Long conversationId,
    @Size(max = 4000, message = "A message is at most 4,000 characters.") String message,
    String mode,
    String style,
    Long questionId) {}
