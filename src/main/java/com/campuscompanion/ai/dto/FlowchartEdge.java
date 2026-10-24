package com.campuscompanion.ai.dto;

/** An arrow of a flowchart, with an optional short label such as "Yes" or "No". */
public record FlowchartEdge(String from, String to, String label) {}
