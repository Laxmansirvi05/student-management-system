package com.campuscompanion.quiz.dto;

import com.campuscompanion.quiz.OptionKey;

/** One answer option as shown to a student: its key and its text, and nothing about being right. */
public record QuizOption(OptionKey key, String text) {}
