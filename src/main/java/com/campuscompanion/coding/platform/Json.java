package com.campuscompanion.coding.platform;

import com.campuscompanion.coding.platform.PlatformException.Kind;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Small JSON helpers shared by the readers. A site that sends something else is "unavailable". */
final class Json {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private Json() {}

  static JsonNode parse(String body) throws PlatformException {
    try {
      return MAPPER.readTree(body);
    } catch (JacksonException notJson) {
      throw new PlatformException(Kind.UNAVAILABLE);
    }
  }

  /** A whole number, or null when missing or not a number. Decimals are rounded. */
  static Integer integer(JsonNode node) {
    if (node == null || node.isNull() || node.isMissingNode() || !node.isNumber()) {
      return null;
    }
    return (int) Math.round(node.asDouble());
  }

  /** A text value, or null when missing. */
  static String text(JsonNode node) {
    return node == null || node.isNull() || node.isMissingNode() ? null : node.asString();
  }

  /** A short label for display: control characters removed, spaces collapsed, at most 40. */
  static String label(String value) {
    if (value == null) {
      return null;
    }
    String clean = value.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").strip();
    if (clean.isEmpty()) {
      return null;
    }
    return clean.length() > 40 ? clean.substring(0, 40) : clean;
  }

  /** The most recent days of a calendar, oldest first, keyed by ISO date. */
  static Map<String, Integer> recentDays(Map<LocalDate, Integer> byDay) {
    Map<String, Integer> kept = new LinkedHashMap<>();
    byDay.entrySet().stream()
        .sorted(Map.Entry.<LocalDate, Integer>comparingByKey().reversed())
        .limit(PlatformDetails.MAX_DAYS)
        .sorted(Map.Entry.comparingByKey())
        .forEach(entry -> kept.put(entry.getKey().toString(), entry.getValue()));
    return kept;
  }

  /** Counts by cleaned name, largest first, merged when two names clean to the same label. */
  static Map<String, Integer> largestFirst(Map<String, Integer> counts, int limit) {
    Map<String, Integer> merged = new LinkedHashMap<>();
    counts.forEach(
        (name, count) -> {
          String clean = label(name);
          if (clean != null && count != null && count > 0) {
            merged.merge(clean, count, Integer::sum);
          }
        });
    Map<String, Integer> kept = new LinkedHashMap<>();
    merged.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
        .limit(limit)
        .forEach(entry -> kept.put(entry.getKey(), entry.getValue()));
    return kept;
  }
}
