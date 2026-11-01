package com.campuscompanion.assistant.attachment;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * The numbering of a Word document (word/numbering.xml). Word stores "1." and "(a)" in front of a
 * list item as a rule, not as text, so a question paper read without this would lose its question
 * numbers. This works out the label of each list item from the rule and the item's position.
 */
final class Numbering {

  private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

  private record Level(String format, String text, int start) {}

  private final Map<String, String> abstractOf = new HashMap<>();
  private final Map<String, Level[]> levels = new HashMap<>();

  private Numbering() {}

  /** The rules of a document; a document without numbering.xml still gets simple labels. */
  static Numbering of(byte[] xml) {
    Numbering numbering = new Numbering();
    if (xml == null) {
      return numbering;
    }
    Document document = DocxReader.parse(xml);
    for (Element abstractNum : DocxReader.all(document.getDocumentElement(), W, "abstractNum")) {
      Level[] list = new Level[9];
      for (Element level : DocxReader.all(abstractNum, W, "lvl")) {
        int index = number(level.getAttributeNS(W, "ilvl"), 0);
        if (index < 0 || index > 8) {
          continue;
        }
        list[index] =
            new Level(
                value(level, "numFmt", "decimal"),
                value(level, "lvlText", "%" + (index + 1) + "."),
                number(value(level, "start", "1"), 1));
      }
      numbering.levels.put(abstractNum.getAttributeNS(W, "abstractNumId"), list);
    }
    for (Element num : DocxReader.all(document.getDocumentElement(), W, "num")) {
      Element abstractRef = DocxReader.first(num, W, "abstractNumId");
      if (abstractRef != null) {
        numbering.abstractOf.put(
            num.getAttributeNS(W, "numId"), abstractRef.getAttributeNS(W, "val"));
      }
    }
    return numbering;
  }

  /**
   * The label of an item at this level; counters holds how many items each level has had so far.
   */
  String label(String numId, int level, int[] counters) {
    Level[] list = levels.get(abstractOf.get(numId));
    Level rule = list == null ? null : list[level];
    if (rule == null || rule.format().equals("bullet") || rule.format().equals("none")) {
      return "-";
    }
    String text = rule.text();
    for (int k = 1; k <= 9; k++) {
      if (!text.contains("%" + k)) {
        continue;
      }
      Level of = list[k - 1];
      int start = of == null ? 1 : of.start();
      int count = counters[k - 1] == 0 ? start : counters[k - 1] - 1 + start;
      text = text.replace("%" + k, format(of == null ? "decimal" : of.format(), count));
    }
    return text.isBlank() ? "-" : text;
  }

  private static String format(String kind, int value) {
    return switch (kind) {
      case "lowerLetter" -> letters(value, false);
      case "upperLetter" -> letters(value, true);
      case "lowerRoman" -> roman(value).toLowerCase(Locale.ROOT);
      case "upperRoman" -> roman(value);
      case "decimalZero" -> value < 10 ? "0" + value : Integer.toString(value);
      default -> Integer.toString(value);
    };
  }

  private static String letters(int value, boolean upper) {
    StringBuilder text = new StringBuilder();
    int n = Math.max(1, value);
    while (n > 0) {
      n--;
      text.insert(0, (char) ((upper ? 'A' : 'a') + n % 26));
      n /= 26;
    }
    return text.toString();
  }

  private static String roman(int value) {
    int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
    StringBuilder text = new StringBuilder();
    int n = Math.max(1, Math.min(value, 3999));
    for (int i = 0; i < values.length; i++) {
      while (n >= values[i]) {
        text.append(symbols[i]);
        n -= values[i];
      }
    }
    return text.toString();
  }

  private static String value(Element parent, String name, String fallback) {
    Element child = DocxReader.first(parent, W, name);
    String value = child == null ? "" : child.getAttributeNS(W, "val");
    return value.isEmpty() ? fallback : value;
  }

  private static int number(String text, int fallback) {
    try {
      return Integer.parseInt(text.strip());
    } catch (NumberFormatException notANumber) {
      return fallback;
    }
  }
}
