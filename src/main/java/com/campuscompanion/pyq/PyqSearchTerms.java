package com.campuscompanion.pyq;

import java.util.Locale;

/**
 * The words of a search box, ready for a SQL LIKE. Every word has to match, so "machine 2025" finds
 * the 2025 Machine Learning papers whatever order the words come in. Words beyond the third are
 * ignored.
 */
public record PyqSearchTerms(String first, String second, String third) {

  private static final int MAX_WORDS = 3;

  public static PyqSearchTerms of(String query) {
    String[] words = query == null ? new String[0] : query.strip().split("\\s+");
    String[] patterns = new String[MAX_WORDS];
    int count = 0;
    for (String word : words) {
      if (!word.isEmpty() && count < MAX_WORDS) {
        patterns[count++] = pattern(word);
      }
    }
    return new PyqSearchTerms(patterns[0], patterns[1], patterns[2]);
  }

  private static String pattern(String word) {
    String plain =
        word.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    return "%" + plain + "%";
  }
}
