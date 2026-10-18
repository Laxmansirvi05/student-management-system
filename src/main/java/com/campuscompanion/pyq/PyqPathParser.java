package com.campuscompanion.pyq;

import com.campuscompanion.common.error.ValidationException;
import com.campuscompanion.pyq.dto.PyqMetadata;
import java.time.Month;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a paper's details from where it sits in the organised folder: {@code BRANCH/Subject
 * name/2025(December)-supply AR20 (2).pdf}. Plain Java, so the folder format is tested on its own
 * and the admin page and any later tool read it the same way.
 *
 * <p>The file name is {@code year(Month)-kind}, then optionally a regulation such as AR20 or a
 * course code, then optionally a question-set number in brackets. The kind is regular, supply, mid
 * or mid-1, mid-2. Only the last two folders and the file name are used, so a longer path (the
 * folder was picked from a parent) works too.
 */
public final class PyqPathParser {

  private static final Pattern FILE =
      Pattern.compile(
          "^(\\d{4})\\(([A-Za-z]+)\\)-(regular|supply|mid)(?:-([1-4]))?"
              + "(?:\\s+([A-Za-z0-9&,]+))?(?:\\s+\\((\\d{1,2})\\))?\\.pdf$",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern REGULATION = Pattern.compile("^AR\\d{2}$", Pattern.CASE_INSENSITIVE);

  private PyqPathParser() {}

  public static PyqMetadata parse(String path) {
    if (path == null || path.isBlank()) {
      throw problem(
          "Give the path of the file, such as CSE/Machine Learning/2025(December)-supply.pdf.");
    }
    String[] parts = path.replace('\\', '/').split("/");
    if (parts.length < 3) {
      throw problem(
          "The path must be branch, subject and file name, such as CSE/Machine Learning/2025(December)-supply.pdf.");
    }
    String file = parts[parts.length - 1];
    String subject = parts[parts.length - 2].strip();
    String branch = parts[parts.length - 3].strip().toUpperCase(Locale.ROOT);
    Matcher name = FILE.matcher(file);
    if (!name.matches()) {
      throw problem("The file name must look like 2025(December)-supply.pdf. Got: " + file);
    }
    Month month = monthOf(name.group(2));
    PyqExamKind kind = PyqExamKind.valueOf(name.group(3).toUpperCase(Locale.ROOT));
    Integer midNumber = name.group(4) == null ? null : Integer.valueOf(name.group(4));
    if (kind != PyqExamKind.MID && midNumber != null) {
      throw problem("Only a mid paper has a number: " + file);
    }
    String extra = name.group(5);
    String regulation = null;
    String courseCode = null;
    if (extra != null) {
      if (REGULATION.matcher(extra).matches()) {
        regulation = extra.toUpperCase(Locale.ROOT);
      } else {
        courseCode = extra.toUpperCase(Locale.ROOT);
      }
    }
    Integer variant = name.group(6) == null ? 1 : Integer.valueOf(name.group(6));
    return new PyqMetadata(
        branch,
        subject,
        Integer.valueOf(name.group(1)),
        month.getValue(),
        kind,
        midNumber,
        regulation,
        courseCode,
        variant);
  }

  private static Month monthOf(String name) {
    for (Month month : Month.values()) {
      if (month.name().equalsIgnoreCase(name)) {
        return month;
      }
    }
    throw problem("\"" + name + "\" is not a month. Use the full month name, such as December.");
  }

  private static ValidationException problem(String message) {
    return new ValidationException("path", message);
  }
}
