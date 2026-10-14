package com.campuscompanion.student;

import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import com.campuscompanion.common.error.FieldErrorDetail;
import com.campuscompanion.common.error.ValidationException;
import com.campuscompanion.student.CsvReader.Row;
import com.campuscompanion.student.dto.StudentCreateRequest;
import com.campuscompanion.student.dto.StudentImportResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates many students from one CSV file (FR-ADM-010). Every row follows the same rules as a
 * student added by hand, because each one goes through {@link StudentProvisioningService}.
 *
 * <p>The whole file is checked first. If any row is wrong, nothing is created and every problem is
 * listed with its line number. If all rows are right, they are created in one transaction, so a
 * failure half-way leaves no student behind.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class StudentImportService {

  public static final int MAX_BYTES = 1024 * 1024;

  /**
   * Each new account needs a BCrypt hash, about 110 ms on a laptop, and Heroku ends a request after
   * 30 seconds. 100 rows take about 11 seconds, which leaves room for a slower server.
   */
  static final int MAX_ROWS = 100;

  static final int MAX_REPORTED_PROBLEMS = 100;

  /** The columns, in any order. The first six are required; phone may be left out. */
  static final List<String> REQUIRED =
      List.of("rollNo", "fullName", "branch", "currentSemester", "section", "admissionYear");

  static final String PHONE = "phone";

  private final StudentProvisioningService provisioning;
  private final StudentRosterValidator validator;
  private final StudentProfileRepository profiles;

  public StudentImportService(
      StudentProvisioningService provisioning,
      StudentRosterValidator validator,
      StudentProfileRepository profiles) {
    this.provisioning = provisioning;
    this.validator = validator;
    this.profiles = profiles;
  }

  @Transactional
  public StudentImportResponse importStudents(byte[] file, String fileName) {
    if (file.length > MAX_BYTES) {
      throw new ApiException(ErrorCode.UPLOAD_TOO_LARGE, "The file is larger than 1 MB.");
    }
    if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
      throw new ApiException(ErrorCode.UPLOAD_INVALID_TYPE, "Choose a .csv file.");
    }
    List<StudentCreateRequest> students = check(read(file));
    students.forEach(provisioning::provision);
    return new StudentImportResponse(students.size());
  }

  private static List<Row> read(byte[] file) {
    String text;
    try {
      text =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(file))
              .toString();
    } catch (CharacterCodingException notUtf8) {
      throw new ValidationException("file", "Save the file as CSV in UTF-8 and try again.");
    }
    try {
      return CsvReader.read(text);
    } catch (CsvReader.MalformedCsvException broken) {
      throw new ValidationException("file", broken.getMessage());
    }
  }

  /** Every row as a request, or every problem of the file at once. */
  private List<StudentCreateRequest> check(List<Row> rows) {
    if (rows.isEmpty()) {
      throw new ValidationException("file", "The file is empty.");
    }
    Map<String, Integer> columns = columnsOf(rows.get(0));
    List<Row> data = rows.subList(1, rows.size());
    if (data.isEmpty()) {
      throw new ValidationException("file", "The file has a header line but no students.");
    }
    if (data.size() > MAX_ROWS) {
      throw new ValidationException(
          "file", "A file can hold at most " + MAX_ROWS + " students. Split it into parts.");
    }

    List<FieldErrorDetail> problems = new ArrayList<>();
    List<StudentCreateRequest> students = new ArrayList<>();
    Map<String, Integer> lineOfRollNo = new HashMap<>();
    for (Row row : data) {
      String at = "rows[" + row.line() + "].";
      if (hasValueWithoutColumn(row, columns)) {
        problems.add(
            new FieldErrorDetail(
                at + "line", "This line has a value in a column that has no name in the header."));
        continue;
      }
      List<FieldErrorDetail> rowProblems = new ArrayList<>();
      StudentCreateRequest student = toRequest(row, columns, rowProblems);
      for (FieldErrorDetail problem : validator.violations(student)) {
        // A number that could not be read is already reported; do not also call it missing.
        if (rowProblems.stream().noneMatch(p -> p.field().equals(problem.field()))) {
          rowProblems.add(problem);
        }
      }
      checkRollNoIsNew(student.rollNo(), row.line(), lineOfRollNo, rowProblems);
      for (FieldErrorDetail problem : rowProblems) {
        problems.add(new FieldErrorDetail(at + problem.field(), problem.message()));
      }
      students.add(student);
    }
    if (!problems.isEmpty()) {
      throw new ValidationException(shortened(problems));
    }
    return students;
  }

  private static StudentCreateRequest toRequest(
      Row row, Map<String, Integer> columns, List<FieldErrorDetail> problems) {
    return new StudentCreateRequest(
        value(row, columns, "rollNo"),
        value(row, columns, "fullName"),
        value(row, columns, "branch"),
        number(row, columns, "currentSemester", problems),
        value(row, columns, "section"),
        number(row, columns, "admissionYear", problems),
        columns.containsKey(PHONE) ? value(row, columns, PHONE) : null);
  }

  /** A roll number may appear once in the file, and must not belong to a student already. */
  private void checkRollNoIsNew(
      String rollNo, int line, Map<String, Integer> lineOfRollNo, List<FieldErrorDetail> problems) {
    if (rollNo == null || problems.stream().anyMatch(p -> p.field().equals("rollNo"))) {
      return;
    }
    String normalised = rollNo.toUpperCase(Locale.ROOT);
    Integer earlier = lineOfRollNo.putIfAbsent(normalised, line);
    if (earlier != null) {
      problems.add(
          new FieldErrorDetail(
              "rollNo", "Roll number " + normalised + " is also on line " + earlier + "."));
    } else if (profiles.existsByRollNo(normalised)) {
      problems.add(
          new FieldErrorDetail(
              "rollNo", "A student with roll number " + normalised + " already exists."));
    }
  }

  /**
   * The position of every column, by name. Unknown, repeated or missing columns stop the import.
   */
  private static Map<String, Integer> columnsOf(Row header) {
    Map<String, String> known = new HashMap<>();
    for (String name : REQUIRED) {
      known.put(name.toLowerCase(Locale.ROOT), name);
    }
    known.put(PHONE, PHONE);

    Map<String, Integer> columns = new HashMap<>();
    List<FieldErrorDetail> problems = new ArrayList<>();
    for (int i = 0; i < header.fields().size(); i++) {
      String raw = header.fields().get(i).strip();
      String name = known.get(raw.toLowerCase(Locale.ROOT));
      if (raw.isEmpty()) {
        continue; // Spreadsheets often save empty columns after the last one; they are ignored.
      } else if (name == null) {
        problems.add(
            new FieldErrorDetail(
                "header",
                "Unknown column \""
                    + raw
                    + "\". The columns are "
                    + String.join(", ", REQUIRED)
                    + " and, if you like, phone."));
      } else if (columns.putIfAbsent(name, i) != null) {
        problems.add(new FieldErrorDetail("header", "The column " + name + " appears twice."));
      }
    }
    for (String name : REQUIRED) {
      if (!columns.containsKey(name)) {
        problems.add(new FieldErrorDetail("header", "The column " + name + " is missing."));
      }
    }
    if (!problems.isEmpty()) {
      throw new ValidationException(problems);
    }
    return columns;
  }

  /** True if the line has text in a column that the header does not name. */
  private static boolean hasValueWithoutColumn(Row row, Map<String, Integer> columns) {
    for (int i = 0; i < row.fields().size(); i++) {
      if (!columns.containsValue(i) && !row.fields().get(i).isBlank()) {
        return true;
      }
    }
    return false;
  }

  private static String value(Row row, Map<String, Integer> columns, String name) {
    int index = columns.get(name);
    if (index >= row.fields().size()) {
      return null;
    }
    String value = row.fields().get(index).strip();
    return value.isEmpty() ? null : value;
  }

  private static Integer number(
      Row row, Map<String, Integer> columns, String name, List<FieldErrorDetail> problems) {
    String value = value(row, columns, name);
    if (value == null) {
      return null;
    }
    try {
      return Integer.valueOf(value);
    } catch (NumberFormatException notANumber) {
      problems.add(new FieldErrorDetail(name, "\"" + value + "\" is not a whole number."));
      return null;
    }
  }

  /** At most 100 problems, so a wrong file does not produce a huge answer. */
  private static List<FieldErrorDetail> shortened(List<FieldErrorDetail> problems) {
    if (problems.size() <= MAX_REPORTED_PROBLEMS) {
      return problems;
    }
    List<FieldErrorDetail> first = new ArrayList<>(problems.subList(0, MAX_REPORTED_PROBLEMS));
    first.add(
        new FieldErrorDetail(
            "file",
            "And "
                + (problems.size() - MAX_REPORTED_PROBLEMS)
                + " more problems. Fix these first."));
    return first;
  }
}
