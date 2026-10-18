package com.campuscompanion.pyq.dto;

/** One subject in the PYQ library: how many papers it has and from which years. */
public record PyqSubjectResponse(
    String branch, String subject, long papers, int firstYear, int latestYear) {}
