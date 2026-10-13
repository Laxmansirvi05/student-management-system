package com.campuscompanion.ai.provider;

import java.time.Duration;

/**
 * One request to the model. The instructions and the student's content are kept in two separate
 * fields all the way to the provider, and nothing here says who the student is.
 *
 * @param promptVersion which template produced the instructions, for example "explain-v1"
 * @param systemInstructions the rules, from a template file in the repository
 * @param userContent the student's text, already wrapped in its tags
 */
public record AiRequest(
    String promptVersion,
    String systemInstructions,
    String userContent,
    int maxOutputTokens,
    Duration timeout) {}
