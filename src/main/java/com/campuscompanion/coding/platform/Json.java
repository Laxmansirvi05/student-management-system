package com.campuscompanion.coding.platform;

import com.campuscompanion.coding.platform.PlatformException.Kind;
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
}
