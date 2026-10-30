package com.campuscompanion.coding.dto;

/** One student's portfolio as an admin sees it, with who the student is. */
public record AdminPortfolioResponse(
    Long studentId,
    String rollNo,
    String fullName,
    String branch,
    int semester,
    String section,
    PortfolioResponse portfolio) {}
