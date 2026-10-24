package com.campuscompanion.ai.dto;

import jakarta.validation.constraints.Size;

/** A topic, or a process the student describes, to turn into a flowchart. One of the two. */
public record FlowchartRequest(
    @Size(min = 3, max = 200, message = "Use 3 to 200 characters.") String topic,
    @Size(min = 20, max = 3000, message = "Use 20 to 3,000 characters.") String processText,
    Long subjectId) {}
