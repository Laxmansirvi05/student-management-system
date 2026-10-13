package com.campuscompanion.ai.dto;

import java.util.List;

/** An explanation. Every text field is plain text and is shown as text, never as HTML. */
public record ExplainResponse(
    String topic,
    AnswerFormat answerFormat,
    String explanation,
    List<String> keyPoints,
    String example,
    AiBasis basis,
    String notice,
    String promptVersion) {}
