package com.campuscompanion.ai.dto;

/** One box of a flowchart. {@code type} is START, END, STEP or DECISION. */
public record FlowchartNode(String id, String label, String type) {}
