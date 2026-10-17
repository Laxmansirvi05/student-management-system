package com.campuscompanion.attendance;

import com.campuscompanion.attendance.dto.AttendanceReportImportResponse;
import com.campuscompanion.audit.AuditAction;
import com.campuscompanion.audit.AuditService;
import com.campuscompanion.audit.AuditSnapshot;
import com.campuscompanion.common.csv.CsvReader;
import com.campuscompanion.common.csv.CsvReader.Row;
import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import com.campuscompanion.common.error.FieldErrorDetail;
import com.campuscompanion.common.error.ValidationException;
import com.campuscompanion.student.StudentDirectory;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads the college's attendance totals from a CSV file: one line per student with classes held and
 * classes attended, for one period. The whole file is checked first; if any line is wrong, nothing
 * is saved and every problem is listed by line. Importing the same period again corrects the
 * numbers, and each correction is written to the audit log.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AttendanceReportImportService {

  public static final int MAX_BYTES = 1024 * 1024;
  static final int MAX_ROWS = 1000;
  static final int MAX_REPORTED_PROBLEMS = 100;

  private static final String ROLL_NO = "rollNo";
  private static final String HELD = "classesHeld";
  private static final String ATTENDED = "classesAttended";
  private static final String PERCENT = "percent";
  private static final String NAME = "fullName";
  private static final List<String> REQUIRED = List.of(ROLL_NO, HELD, ATTENDED);
  private static final List<String> OPTIONAL = List.of(PERCENT, NAME);
  private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

  private final AttendanceReportRepository reports;
  private final StudentDirectory students;
  private final AuditService audit;
  private final Clock clock;

  public AttendanceReportImportService(
      AttendanceReportRepository reports,
      StudentDirectory students,
      AuditService audit,
      Clock clock) {
    this.reports = reports;
    this.students = students;
    this.audit = audit;
    this.clock = clock;
  }

  /** One checked line of the file. */
  private record Line(int line, String rollNo, int held, int attended) {}

  @Transactional
  public AttendanceReportImportResponse importReports(
      byte[] file, String fileName, LocalDate fromDate, LocalDate toDate) {
    checkPeriod(fromDate, toDate);
    if (file.length > MAX_BYTES) {
      throw new ApiException(ErrorCode.UPLOAD_TOO_LARGE, "The file is larger than 1 MB.");
    }
    if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
      throw new ApiException(ErrorCode.UPLOAD_INVALID_TYPE, "Choose a .csv file.");
    }
    List<Line> lines = check(read(file));
    Map<String, Long> ids = students.idsByRollNumber(lines.stream().map(Line::rollNo).toList());

    int created = 0;
    int corrected = 0;
    int unchanged = 0;
    for (Line line : lines) {
      Long studentId = ids.get(line.rollNo());
      var existing = reports.findByStudentIdAndFromDateAndToDate(studentId, fromDate, toDate);
      if (existing.isEmpty()) {
        reports.save(
            new AttendanceReport(studentId, fromDate, toDate, line.held(), line.attended()));
        created++;
      } else if (existing.get().getClassesHeld() == line.held()
          && existing.get().getClassesAttended() == line.attended()) {
        unchanged++;
      } else {
        AttendanceReport report = existing.get();
        var before =
            new AuditSnapshot.AttendanceTotals(
                report.getClassesHeld(), report.getClassesAttended());
        report.correct(line.held(), line.attended());
        audit.record(
            AuditAction.ATTENDANCE_CORRECTED,
            "ATTENDANCE_REPORT",
            report.getId(),
            before,
            new AuditSnapshot.AttendanceTotals(line.held(), line.attended()));
        corrected++;
      }
    }
    return new AttendanceReportImportResponse(created, corrected, unchanged);
  }

  private void checkPeriod(LocalDate fromDate, LocalDate toDate) {
    List<FieldErrorDetail> problems = new ArrayList<>();
    if (fromDate == null) {
      problems.add(new FieldErrorDetail("fromDate", "Choose the first day of the period."));
    }
    if (toDate == null) {
      problems.add(new FieldErrorDetail("toDate", "Choose the last day of the period."));
    }
    if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
      problems.add(new FieldErrorDetail("toDate", "The last day is before the first day."));
    }
    if (toDate != null && toDate.isAfter(LocalDate.now(clock))) {
      problems.add(new FieldErrorDetail("toDate", "The period cannot end in the future."));
    }
    if (!problems.isEmpty()) {
      throw new ValidationException(problems);
    }
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

  /** Every line as numbers for a known student, or every problem of the file at once. */
  private List<Line> check(List<Row> rows) {
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

    List<String> rollNumbers = new ArrayList<>();
    for (Row row : data) {
      String rollNo = value(row, columns, ROLL_NO);
      if (rollNo != null) {
        rollNumbers.add(rollNo.toUpperCase(Locale.ROOT));
      }
    }
    Set<String> known = students.idsByRollNumber(rollNumbers).keySet();

    List<FieldErrorDetail> problems = new ArrayList<>();
    List<Line> lines = new ArrayList<>();
    Map<String, Integer> lineOfRollNo = new HashMap<>();
    for (Row row : data) {
      String at = "rows[" + row.line() + "].";
      List<FieldErrorDetail> rowProblems = new ArrayList<>();
      String rollNo = value(row, columns, ROLL_NO);
      Integer held = number(row, columns, HELD, rowProblems);
      Integer attended = number(row, columns, ATTENDED, rowProblems);
      if (rollNo == null) {
        rowProblems.add(new FieldErrorDetail(ROLL_NO, "Roll number is required."));
      } else {
        rollNo = rollNo.toUpperCase(Locale.ROOT);
        Integer earlier = lineOfRollNo.putIfAbsent(rollNo, row.line());
        if (earlier != null) {
          rowProblems.add(
              new FieldErrorDetail(
                  ROLL_NO, "Roll number " + rollNo + " is also on line " + earlier + "."));
        } else if (!known.contains(rollNo)) {
          rowProblems.add(
              new FieldErrorDetail(ROLL_NO, "No student has roll number " + rollNo + "."));
        }
      }
      if (held != null && (held < 1 || held > 10000)) {
        rowProblems.add(new FieldErrorDetail(HELD, "Classes held must be between 1 and 10,000."));
      }
      if (held != null && attended != null && (attended < 0 || attended > held)) {
        rowProblems.add(
            new FieldErrorDetail(ATTENDED, "Classes attended must be between 0 and classes held."));
      }
      checkPercent(row, columns, held, attended, rowProblems);
      rowProblems.forEach(p -> problems.add(new FieldErrorDetail(at + p.field(), p.message())));
      if (rowProblems.isEmpty()) {
        lines.add(new Line(row.line(), rollNo, held, attended));
      }
    }
    if (!problems.isEmpty()) {
      throw new ValidationException(shortened(problems));
    }
    return lines;
  }

  /**
   * When the file gives a percentage, it must agree with held and attended. Catches typing slips.
   */
  private static void checkPercent(
      Row row,
      Map<String, Integer> columns,
      Integer held,
      Integer attended,
      List<FieldErrorDetail> problems) {
    String given = columns.containsKey(PERCENT) ? value(row, columns, PERCENT) : null;
    if (given == null || held == null || attended == null || held < 1) {
      return;
    }
    try {
      BigDecimal stated = new BigDecimal(given.replace("%", "").strip());
      BigDecimal calculated = AttendanceCalculator.percent(attended, held);
      if (stated.subtract(calculated).abs().compareTo(TOLERANCE) > 0) {
        problems.add(
            new FieldErrorDetail(
                PERCENT,
                "The file says "
                    + given
                    + "%, but "
                    + attended
                    + " of "
                    + held
                    + " is "
                    + calculated
                    + "%. Check the three numbers."));
      }
    } catch (NumberFormatException notANumber) {
      problems.add(new FieldErrorDetail(PERCENT, "\"" + given + "\" is not a number."));
    }
  }

  private static Map<String, Integer> columnsOf(Row header) {
    Map<String, String> known = new HashMap<>();
    for (String name : REQUIRED) {
      known.put(name.toLowerCase(Locale.ROOT), name);
    }
    for (String name : OPTIONAL) {
      known.put(name.toLowerCase(Locale.ROOT), name);
    }
    Map<String, Integer> columns = new HashMap<>();
    List<FieldErrorDetail> problems = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < header.fields().size(); i++) {
      String raw = header.fields().get(i).strip();
      if (raw.isEmpty()) {
        continue;
      }
      String name = known.get(raw.toLowerCase(Locale.ROOT));
      if (name == null) {
        problems.add(
            new FieldErrorDetail(
                "header",
                "Unknown column \""
                    + raw
                    + "\". The columns are rollNo, classesHeld, classesAttended and, if you"
                    + " like, percent and fullName."));
      } else if (!seen.add(name)) {
        problems.add(new FieldErrorDetail("header", "The column " + name + " appears twice."));
      } else {
        columns.put(name, i);
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
      problems.add(new FieldErrorDetail(name, "This number is required."));
      return null;
    }
    try {
      return Integer.valueOf(value);
    } catch (NumberFormatException notANumber) {
      problems.add(new FieldErrorDetail(name, "\"" + value + "\" is not a whole number."));
      return null;
    }
  }

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
