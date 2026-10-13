package com.campuscompanion.ai.provider;

/** The model's raw reply. It is untrusted until AiResponseParser has checked it. */
public record AiCompletion(String text, int inputChars, int outputChars) {}
