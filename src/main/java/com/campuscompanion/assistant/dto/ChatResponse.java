package com.campuscompanion.assistant.dto;

/**
 * The answer to a message: the chat it belongs to, the student's message as stored, and the reply.
 */
public record ChatResponse(
    ConversationResponse conversation,
    MessageResponse question,
    MessageResponse reply,
    String notice) {}
