package com.campuscompanion.notes;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Makes the HTML of a note safe to store and to show. The editor produces HTML, but the API can be
 * called without the editor, so every write is rebuilt here from an allowlist: only the tags and
 * attributes the editor can produce survive, every attribute value is checked against a pattern,
 * and the output is written from scratch rather than edited. It is plain Java with no dependency.
 *
 * <p>The rule for changing this class: whatever the editor can produce must be allowed here in the
 * same change, or its formatting disappears the first time the note is saved.
 *
 * <p>Images and files may point only at this application's own attachment addresses, so a note can
 * never load something from another site. A link may be a web, mail or phone address, or a path
 * inside this application.
 */
public final class NoteHtmlSanitizer {

  private static final Set<String> VOID_TAGS = Set.of("br", "hr", "img", "input", "col");

  /** Tags whose text must not survive either: it would be script or style source. */
  private static final Set<String> DROP_WITH_CONTENT =
      Set.of("script", "style", "textarea", "option", "template", "noscript");

  private static final Set<String> ALLOWED_TAGS =
      Set.of(
          "p",
          "div",
          "br",
          "hr",
          "blockquote",
          "pre",
          "h1",
          "h2",
          "h3",
          "h4",
          "h5",
          "h6",
          "strong",
          "b",
          "em",
          "i",
          "u",
          "s",
          "del",
          "code",
          "mark",
          "span",
          "a",
          "sub",
          "sup",
          "ul",
          "ol",
          "li",
          "label",
          "input",
          "table",
          "thead",
          "tbody",
          "tfoot",
          "tr",
          "th",
          "td",
          "colgroup",
          "col",
          "img",
          "figure",
          "figcaption");

  private static final Set<String> STYLED =
      Set.of(
          "p",
          "div",
          "h1",
          "h2",
          "h3",
          "h4",
          "h5",
          "h6",
          "span",
          "mark",
          "table",
          "th",
          "td",
          "col",
          "img",
          "li",
          "blockquote");

  private static final Map<String, Set<String>> ATTRIBUTES = attributes();

  private static final Pattern ENTITY =
      Pattern.compile("&(#\\d{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z][a-zA-Z0-9]{1,31});");
  private static final Pattern ATTACHMENT_PATH =
      Pattern.compile("^/api/v1/notes/attachments/\\d{1,18}/file$");
  private static final Pattern INTERNAL_PATH =
      Pattern.compile("^/(?!/)[A-Za-z0-9._~!$&'()*+,;=:@/?#%-]{0,500}$");
  private static final Pattern EXTERNAL_LINK =
      Pattern.compile(
          "^(https?://[^\\s\"'<>]{1,2000}|mailto:[^\\s\"'<>]{1,320}|tel:[+0-9() .-]{1,40})$",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern COLOUR =
      Pattern.compile(
          "^(#[0-9a-fA-F]{3,8}|rgba?\\(\\s*\\d{1,3}\\s*,\\s*\\d{1,3}\\s*,\\s*\\d{1,3}\\s*(,\\s*(0|1|0?\\.\\d{1,3})\\s*)?\\))$");
  private static final Map<String, Pattern> STYLE_VALUES = styleValues();
  private static final Pattern WORD = Pattern.compile("^[A-Za-z0-9_-]{1,40}$");
  private static final Pattern CLASS_LIST = Pattern.compile("^[A-Za-z0-9 _-]{1,80}$");
  private static final Pattern SMALL_NUMBER = Pattern.compile("^\\d{1,4}$");
  private static final Pattern DIMENSION = Pattern.compile("^\\d{1,4}(px|%)?$");
  private static final Pattern COLUMN_WIDTHS = Pattern.compile("^\\d{1,4}(,\\d{1,4}){0,19}$");
  private static final int MAX_DEPTH = 100;

  private NoteHtmlSanitizer() {}

  private static Map<String, Set<String>> attributes() {
    Map<String, Set<String>> map = new LinkedHashMap<>();
    map.put("img", Set.of("src", "alt", "title", "width", "height", "data-align", "style"));
    map.put(
        "a",
        Set.of(
            "href",
            "data-note-link",
            "data-link-type",
            "data-file-name",
            "data-file-size",
            "data-note-file"));
    map.put("ul", Set.of("data-type"));
    map.put("ol", Set.of("start", "type"));
    map.put("li", Set.of("data-type", "data-checked", "style"));
    map.put("input", Set.of("type", "checked", "disabled"));
    map.put("span", Set.of("data-type"));
    map.put("mark", Set.of("data-color"));
    map.put("code", Set.of("class"));
    map.put("pre", Set.of("class"));
    map.put("div", Set.of("data-type"));
    map.put("th", Set.of("colspan", "rowspan", "colwidth"));
    map.put("td", Set.of("colspan", "rowspan", "colwidth"));
    map.put("figure", Set.of("data-type"));
    return map;
  }

  private static Map<String, Pattern> styleValues() {
    Map<String, Pattern> map = new LinkedHashMap<>();
    map.put("text-align", Pattern.compile("^(left|right|center|justify)$"));
    map.put("color", COLOUR);
    map.put("background-color", COLOUR);
    map.put("font-size", Pattern.compile("^\\d{1,3}(\\.\\d+)?(px|pt|em|rem)$"));
    map.put("line-height", Pattern.compile("^\\d(\\.\\d+)?$"));
    map.put("margin-left", Pattern.compile("^\\d{1,3}px$"));
    map.put("width", Pattern.compile("^\\d{1,4}(px|%)$"));
    map.put("height", Pattern.compile("^(auto|\\d{1,4}px)$"));
    map.put("min-width", Pattern.compile("^\\d{1,4}px$"));
    return map;
  }

  /** Returns the safe form of the HTML. Null is treated as empty. */
  public static String sanitize(String html) {
    if (html == null || html.isEmpty()) {
      return "";
    }
    StringBuilder out = new StringBuilder(html.length());
    Deque<String> open = new ArrayDeque<>();
    int i = 0;
    int length = html.length();
    while (i < length) {
      char c = html.charAt(i);
      if (c != '<') {
        int next = html.indexOf('<', i);
        int end = next < 0 ? length : next;
        appendText(out, html, i, end);
        i = end;
        continue;
      }
      if (html.startsWith("<!--", i)) {
        int close = html.indexOf("-->", i + 4);
        i = close < 0 ? length : close + 3;
        continue;
      }
      if (i + 1 >= length) {
        out.append("&lt;");
        i++;
        continue;
      }
      char following = html.charAt(i + 1);
      if (following == '!' || following == '?') {
        int close = html.indexOf('>', i);
        i = close < 0 ? length : close + 1;
        continue;
      }
      boolean closing = following == '/';
      int nameStart = closing ? i + 2 : i + 1;
      if (nameStart >= length || !Character.isLetter(html.charAt(nameStart))) {
        out.append("&lt;");
        i++;
        continue;
      }
      int nameEnd = nameStart;
      while (nameEnd < length && !isNameEnd(html.charAt(nameEnd))) {
        nameEnd++;
      }
      String name = html.substring(nameStart, nameEnd).toLowerCase(Locale.ROOT);
      int tagEnd = endOfTag(html, nameEnd);
      if (tagEnd < 0) {
        // An unfinished tag at the end: nothing after it is kept.
        break;
      }
      if (closing) {
        closeTag(out, open, name);
      } else if (DROP_WITH_CONTENT.contains(name)) {
        i = skipPast(html, tagEnd + 1, name);
        continue;
      } else if (ALLOWED_TAGS.contains(name)) {
        openTag(out, open, name, html.substring(nameEnd, tagEnd));
      }
      i = tagEnd + 1;
    }
    while (!open.isEmpty()) {
      out.append("</").append(open.pop()).append('>');
    }
    return out.toString();
  }

  private static boolean isNameEnd(char c) {
    return Character.isWhitespace(c) || c == '/' || c == '>';
  }

  /** The index of the {@code >} that ends the tag, skipping any inside a quoted value. */
  private static int endOfTag(String html, int from) {
    char quote = 0;
    for (int i = from; i < html.length(); i++) {
      char c = html.charAt(i);
      if (quote != 0) {
        if (c == quote) {
          quote = 0;
        }
      } else if (c == '"' || c == '\'') {
        quote = c;
      } else if (c == '>') {
        return i;
      }
    }
    return -1;
  }

  /** The index after the closing tag of a dropped element, or the end of the text. */
  private static int skipPast(String html, int from, String name) {
    String lower = html.toLowerCase(Locale.ROOT);
    int close = lower.indexOf("</" + name, from);
    if (close < 0) {
      return html.length();
    }
    int end = html.indexOf('>', close);
    return end < 0 ? html.length() : end + 1;
  }

  private static void openTag(
      StringBuilder out, Deque<String> open, String name, String rawAttributes) {
    if (open.size() >= MAX_DEPTH && !VOID_TAGS.contains(name)) {
      return;
    }
    Map<String, String> attributes = parseAttributes(rawAttributes);
    Map<String, String> kept = new LinkedHashMap<>();
    Set<String> allowed = ATTRIBUTES.getOrDefault(name, Set.of());
    for (Map.Entry<String, String> attribute : attributes.entrySet()) {
      String attributeName = attribute.getKey();
      boolean styleAllowed = attributeName.equals("style") && STYLED.contains(name);
      if (!styleAllowed && !allowed.contains(attributeName)) {
        continue;
      }
      String value = cleanValue(name, attributeName, attribute.getValue());
      if (value != null) {
        kept.put(attributeName, value);
      }
    }
    if (name.equals("a")) {
      String href = kept.get("href");
      if (href != null && !href.startsWith("/")) {
        kept.put("target", "_blank");
        kept.put("rel", "noopener noreferrer nofollow");
      }
    }
    if (name.equals("input") && !"checkbox".equals(kept.get("type"))) {
      return;
    }
    if (name.equals("img") && !kept.containsKey("src")) {
      return;
    }
    out.append('<').append(name);
    for (Map.Entry<String, String> attribute : kept.entrySet()) {
      out.append(' ').append(attribute.getKey());
      if (!attribute.getValue().isEmpty() || isTextAttribute(attribute.getKey())) {
        out.append("=\"").append(escapeAttribute(attribute.getValue())).append('"');
      }
    }
    out.append('>');
    if (!VOID_TAGS.contains(name)) {
      open.push(name);
    }
  }

  private static boolean isTextAttribute(String name) {
    return name.equals("alt") || name.equals("title");
  }

  private static void closeTag(StringBuilder out, Deque<String> open, String name) {
    if (!ALLOWED_TAGS.contains(name) || VOID_TAGS.contains(name) || !open.contains(name)) {
      return;
    }
    while (!open.isEmpty()) {
      String top = open.pop();
      out.append("</").append(top).append('>');
      if (top.equals(name)) {
        return;
      }
    }
  }

  /** Reads {@code name="value"}, {@code name='value'}, {@code name=value} and bare names. */
  private static Map<String, String> parseAttributes(String raw) {
    Map<String, String> attributes = new LinkedHashMap<>();
    int i = 0;
    int length = raw.length();
    while (i < length) {
      while (i < length && (Character.isWhitespace(raw.charAt(i)) || raw.charAt(i) == '/')) {
        i++;
      }
      int nameStart = i;
      while (i < length
          && !Character.isWhitespace(raw.charAt(i))
          && raw.charAt(i) != '='
          && raw.charAt(i) != '/') {
        i++;
      }
      if (i == nameStart) {
        i++;
        continue;
      }
      String name = raw.substring(nameStart, i).toLowerCase(Locale.ROOT);
      while (i < length && Character.isWhitespace(raw.charAt(i))) {
        i++;
      }
      String value = "";
      if (i < length && raw.charAt(i) == '=') {
        i++;
        while (i < length && Character.isWhitespace(raw.charAt(i))) {
          i++;
        }
        if (i < length && (raw.charAt(i) == '"' || raw.charAt(i) == '\'')) {
          char quote = raw.charAt(i++);
          int close = raw.indexOf(quote, i);
          int end = close < 0 ? length : close;
          value = raw.substring(i, end);
          i = close < 0 ? length : close + 1;
        } else {
          int valueStart = i;
          while (i < length && !Character.isWhitespace(raw.charAt(i))) {
            i++;
          }
          value = raw.substring(valueStart, i);
        }
      }
      attributes.putIfAbsent(name, unescape(value));
    }
    return attributes;
  }

  /** Checks one attribute value. Returns the value to keep, or null to drop the attribute. */
  private static String cleanValue(String tag, String name, String value) {
    String trimmed = value.strip();
    switch (name) {
      case "href":
        return cleanLink(trimmed);
      case "src":
        return ATTACHMENT_PATH.matcher(trimmed).matches() ? trimmed : null;
      case "style":
        return cleanStyle(trimmed);
      case "alt", "title":
        return trimmed.length() <= 300 ? trimmed : trimmed.substring(0, 300);
      case "width", "height":
        return DIMENSION.matcher(trimmed).matches() ? trimmed : null;
      case "colspan", "rowspan", "start":
        return SMALL_NUMBER.matcher(trimmed).matches() ? trimmed : null;
      case "colwidth":
        return COLUMN_WIDTHS.matcher(trimmed).matches() ? trimmed : null;
      case "data-checked":
        return trimmed.equals("true") || trimmed.equals("false") ? trimmed : null;
      case "data-align":
        return trimmed.matches("^(left|center|right)$") ? trimmed : null;
      case "data-color":
        return COLOUR.matcher(trimmed).matches() ? trimmed : null;
      case "data-type", "data-link-type":
        return WORD.matcher(trimmed).matches() ? trimmed : null;
      case "data-note-link":
        return trimmed.matches("^\\d{1,18}$") ? trimmed : null;
      case "data-file-name":
        return trimmed.length() <= 120 ? trimmed : trimmed.substring(0, 120);
      case "data-file-size":
        return trimmed.matches("^\\d{1,12}$") ? trimmed : null;
      case "data-note-file":
        return "";
      case "type":
        return tag.equals("input")
            ? (trimmed.equalsIgnoreCase("checkbox") ? "checkbox" : null)
            : (trimmed.matches("^[1aAiI]$") ? trimmed : null);
      case "checked", "disabled":
        return "";
      case "class":
        return CLASS_LIST.matcher(trimmed).matches() ? trimmed : null;
      default:
        return null;
    }
  }

  private static String cleanLink(String href) {
    if (ATTACHMENT_PATH.matcher(href).matches()
        || INTERNAL_PATH.matcher(href).matches()
        || EXTERNAL_LINK.matcher(href).matches()) {
      return href;
    }
    return null;
  }

  /** Keeps only the declarations whose property and value are on the list. */
  private static String cleanStyle(String style) {
    StringBuilder kept = new StringBuilder();
    for (String declaration : style.split(";")) {
      int colon = declaration.indexOf(':');
      if (colon < 0) {
        continue;
      }
      String property = declaration.substring(0, colon).strip().toLowerCase(Locale.ROOT);
      String value = declaration.substring(colon + 1).strip();
      Pattern pattern = STYLE_VALUES.get(property);
      if (pattern != null && pattern.matcher(value).matches()) {
        if (!kept.isEmpty()) {
          kept.append(' ');
        }
        kept.append(property).append(": ").append(value).append(';');
      }
    }
    return kept.isEmpty() ? null : kept.toString();
  }

  /** Text is escaped; character references that are already well formed are kept as they are. */
  private static void appendText(StringBuilder out, String html, int from, int to) {
    for (int i = from; i < to; i++) {
      char c = html.charAt(i);
      if (c == '&') {
        Matcher entity = ENTITY.matcher(html);
        entity.region(i, to);
        if (entity.lookingAt()) {
          out.append(entity.group());
          i = entity.end() - 1;
        } else {
          out.append("&amp;");
        }
      } else if (c == '<') {
        out.append("&lt;");
      } else if (c == '>') {
        out.append("&gt;");
      } else if (c >= 0x20 || c == '\n' || c == '\t' || c == '\r') {
        out.append(c);
      }
    }
  }

  private static String escapeAttribute(String value) {
    StringBuilder out = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '&' -> out.append("&amp;");
        case '"' -> out.append("&quot;");
        case '<' -> out.append("&lt;");
        case '>' -> out.append("&gt;");
        default -> {
          if (c >= 0x20) {
            out.append(c);
          }
        }
      }
    }
    return out.toString();
  }

  /** Turns the few character references that matter in attribute values back into characters. */
  private static String unescape(String value) {
    if (value.indexOf('&') < 0) {
      return value;
    }
    return value
        .replace("&quot;", "\"")
        .replace("&#34;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&");
  }
}
