package com.campuscompanion.ai.provider;

/**
 * A picture the student attached to a question: the type and the bytes of an image the application
 * has already checked and redrawn. It exists only for the length of one request and is never stored
 * or logged.
 */
public record AiImage(String mimeType, byte[] data) {}
