package com.campuscompanion.attendance;

import com.campuscompanion.attendance.dto.AttendanceSheet;
import com.campuscompanion.audit.AuditAction;
import com.campuscompanion.audit.AuditService;
import com.campuscompanion.audit.AuditSnapshot;
import com.campuscompanion.common.config.CampusTime;
import com.campuscompanion.common.error.FieldErrorDetail;
import com.campuscompanion.common.error.ResourceNotFoundException;
import com.campuscompanion.common.error.ValidationException;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.common.web.SheetSaved;
import com.campuscompanion.student.StudentContext;
import com.campuscompanion.student.StudentProfileService;
import com.campuscompanion.subject.SubjectService;
import com.campuscompanion.subject.dto.SubjectResponse;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entering attendance (FR-ADM-002). A sheet is one class: a subject, a section, a date and a
 * period. It is saved whole, in one transaction: if any row is wrong, nothing is saved. Saving the
 * same class again corrects the earlier rows, and each correction is written to the audit log.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminAttendanceService {

  private final AttendanceRecordRepository records;
  private final StudentProfileService students;
  private final SubjectService subjects;
  private final AuditService audit;
  private final Clock clock;

  public AdminAttendanceService(
      AttendanceRecordRepository records,
      StudentProfileService students,
      SubjectService subjects,
      AuditService audit,
      Clock clock) {
    this.records = records;
    this.students = students;
    this.subjects = subjects;
    this.audit = audit;
    this.clock = clock;
  }

  /** The students of the section, each with the status already saved for this class, if any. */
  @Transactional(readOnly = true)
  public AttendanceSheet sheet(
      Long subjectId, String section, java.time.LocalDate classDate, int periodNo) {
    SubjectResponse subject = subjects.find(subjectId).orElseThrow(ResourceNotFoundException::new);
    List<StudentContext> classList =
        students.studentsOfClass(subject.branch(), subject.semester(), section.strip());
    Map<Long, AttendanceRecord> saved = savedRows(subjectId, classDate, periodNo, classList);
    List<AttendanceSheet.Entry> entries =
        classList.stream()
            .map(
                student ->
                    new AttendanceSheet.Entry(
                        student.studentId(),
                        student.rollNo(),
                        student.fullName(),
                        saved.containsKey(student.studentId())
                            ? saved.get(student.studentId()).getStatus()
                            : null))
            .toList();
    return new AttendanceSheet(subjectId, section.strip(), classDate, periodNo, entries);
  }

  @Transactional
  public SheetSaved save(AttendanceSheet sheet, CurrentUser admin) {
    SubjectResponse subject =
        subjects
            .find(sheet.subjectId())
            .orElseThrow(() -> new ValidationException("subjectId", "Choose an existing subject."));
    if (sheet.classDate().isAfter(CampusTime.today(clock))) {
      throw new ValidationException("classDate", "Attendance cannot be entered for a future date.");
    }
    List<StudentContext> classList =
        students.studentsOfClass(subject.branch(), subject.semester(), sheet.section().strip());
    rejectInvalidRows(sheet, classList);

    Map<Long, AttendanceRecord> saved =
        savedRows(sheet.subjectId(), sheet.classDate(), sheet.periodNo(), classList);
    int changed = 0;
    for (AttendanceSheet.Entry entry : sheet.entries()) {
      AttendanceRecord existing = saved.get(entry.studentId());
      if (existing == null) {
        records.save(
            new AttendanceRecord(
                entry.studentId(),
                sheet.subjectId(),
                sheet.classDate(),
                sheet.periodNo(),
                entry.status(),
                admin.id()));
        changed++;
      } else if (existing.getStatus() != entry.status()) {
        AuditSnapshot before = new AuditSnapshot.Attendance(existing.getStatus().name());
        existing.correct(entry.status(), admin.id());
        audit.record(
            AuditAction.ATTENDANCE_CORRECTED,
            "ATTENDANCE_RECORD",
            existing.getId(),
            before,
            new AuditSnapshot.Attendance(entry.status().name()));
        changed++;
      }
    }
    return new SheetSaved(sheet.entries().size(), changed);
  }

  /** Every row must name a student of this class, once, with a status. */
  private static void rejectInvalidRows(AttendanceSheet sheet, List<StudentContext> classList) {
    Set<Long> inClass =
        classList.stream().map(StudentContext::studentId).collect(Collectors.toSet());
    Set<Long> seen = new HashSet<>();
    List<FieldErrorDetail> errors = new ArrayList<>();
    for (int i = 0; i < sheet.entries().size(); i++) {
      AttendanceSheet.Entry entry = sheet.entries().get(i);
      String row = "entries[" + i + "]";
      if (!inClass.contains(entry.studentId())) {
        errors.add(
            new FieldErrorDetail(row + ".studentId", "This student is not in that section."));
      } else if (!seen.add(entry.studentId())) {
        errors.add(new FieldErrorDetail(row + ".studentId", "This student appears twice."));
      }
      if (entry.status() == null) {
        errors.add(new FieldErrorDetail(row + ".status", "Choose present or absent."));
      }
    }
    if (!errors.isEmpty()) {
      throw new ValidationException(errors);
    }
  }

  private Map<Long, AttendanceRecord> savedRows(
      Long subjectId, java.time.LocalDate classDate, int periodNo, List<StudentContext> classList) {
    List<Long> studentIds = classList.stream().map(StudentContext::studentId).toList();
    return records
        .findBySubjectIdAndClassDateAndPeriodNoAndStudentIdIn(
            subjectId, classDate, periodNo, studentIds)
        .stream()
        .collect(Collectors.toMap(AttendanceRecord::getStudentId, Function.identity()));
  }
}
