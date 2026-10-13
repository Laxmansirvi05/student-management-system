package com.campuscompanion.ai.dto;

/** What an answer is based on: the student's own text, general knowledge, or both. */
public enum AiBasis {
  GENERAL_KNOWLEDGE,
  PROVIDED_TEXT,
  MIXED
}
