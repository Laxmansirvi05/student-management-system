package com.campuscompanion.student;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads comma-separated text into rows of fields, the way spreadsheets write it (RFC 4180). A field
 * may be in double quotes, and then it may hold commas, line breaks and doubled quotes. It is plain
 * Java, written here so no library is needed for one small format.
 */
public final class CsvReader {

  /** The invisible character some programs, Excel among them, put at the start of a UTF-8 file. */
  static final String BYTE_ORDER_MARK = String.valueOf((char) 0xFEFF);

  private CsvReader() {}

  /** One line of the file, with the number it has in the file (the first line is 1). */
  public record Row(int line, List<String> fields) {}

  /** Thrown when a quoted field is never closed. Says on which line it started. */
  public static class MalformedCsvException extends RuntimeException {

    private final int line;

    MalformedCsvException(int line) {
      super("A quoted field that starts on line " + line + " is never closed.");
      this.line = line;
    }

    public int getLine() {
      return line;
    }
  }

  /** The rows of the text. Empty lines are left out; a byte order mark at the start is ignored. */
  public static List<Row> read(String text) {
    String content = text.startsWith(BYTE_ORDER_MARK) ? text.substring(1) : text;
    List<Row> rows = new ArrayList<>();
    List<String> fields = new ArrayList<>();
    StringBuilder field = new StringBuilder();
    boolean quoted = false;
    int line = 1;
    int rowStart = 1;
    int quoteStart = 1;

    for (int i = 0; i < content.length(); i++) {
      char c = content.charAt(i);
      if (quoted) {
        if (c == '"' && i + 1 < content.length() && content.charAt(i + 1) == '"') {
          field.append('"');
          i++;
        } else if (c == '"') {
          quoted = false;
        } else {
          if (c == '\n') {
            line++;
          }
          field.append(c);
        }
      } else if (c == '"' && field.isEmpty()) {
        quoted = true;
        quoteStart = line;
      } else if (c == ',') {
        fields.add(field.toString());
        field.setLength(0);
      } else if (c == '\n' || c == '\r') {
        if (c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
          i++;
        }
        fields.add(field.toString());
        field.setLength(0);
        addUnlessEmpty(rows, rowStart, fields);
        fields = new ArrayList<>();
        line++;
        rowStart = line;
      } else {
        field.append(c);
      }
    }
    if (quoted) {
      throw new MalformedCsvException(quoteStart);
    }
    fields.add(field.toString());
    addUnlessEmpty(rows, rowStart, fields);
    return rows;
  }

  private static void addUnlessEmpty(List<Row> rows, int line, List<String> fields) {
    boolean empty = fields.stream().allMatch(String::isBlank);
    if (!empty) {
      rows.add(new Row(line, List.copyOf(fields)));
    }
  }
}
