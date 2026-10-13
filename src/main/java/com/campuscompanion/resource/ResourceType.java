package com.campuscompanion.resource;

public enum ResourceType {
  NOTES,
  SYLLABUS,
  LAB_MANUAL,
  QUESTION_PAPER,
  MODEL_PAPER,
  IMPORTANT_QUESTIONS;

  /** Question papers and model papers carry an exam year and an exam type. */
  public boolean isPaper() {
    return this == QUESTION_PAPER || this == MODEL_PAPER;
  }
}
