package com.campuscompanion.ai.dto;

import java.util.List;

/**
 * A checked flowchart (FR-AI-006): its nodes and edges, the same chart as numbered steps, and
 * Mermaid text that Java built (never the model). All text is plain text.
 */
public record FlowchartResponse(
    String title,
    List<FlowchartNode> nodes,
    List<FlowchartEdge> edges,
    List<String> steps,
    String mermaid,
    AiBasis basis,
    String notice,
    String promptVersion) {}
