package com.campuscompanion.coding.dto;

import java.time.LocalDate;

/** The student's total problems solved on one day. */
public record JourneyPoint(LocalDate date, int problemsSolved) {}
