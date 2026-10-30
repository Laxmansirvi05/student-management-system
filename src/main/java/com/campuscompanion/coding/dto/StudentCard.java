package com.campuscompanion.coding.dto;

/** Who the portfolio belongs to, for its profile card. */
public record StudentCard(
    String fullName,
    String rollNo,
    String branch,
    int semester,
    String section,
    String bio,
    boolean hasPhoto) {}
