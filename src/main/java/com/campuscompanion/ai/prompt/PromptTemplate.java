package com.campuscompanion.ai.prompt;

/**
 * One versioned prompt: the instructions for the model, and the layout of the student's content
 * with its {placeholders}. The version is the file name, for example "explain-v1".
 */
public record PromptTemplate(String version, String systemInstructions, String userLayout) {}
