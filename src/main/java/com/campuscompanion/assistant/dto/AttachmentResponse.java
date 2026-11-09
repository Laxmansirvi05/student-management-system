package com.campuscompanion.assistant.dto;

/**
 * What the server really read from an attached picture: its id, name, {@code kind} ("image") and
 * how many pictures will go to the model with the question.
 */
public record AttachmentResponse(String id, String name, String kind, int pictures) {}
