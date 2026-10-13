package com.campuscompanion.attendance;

import com.campuscompanion.attendance.AttendanceRecordRepository.SubjectCount;
import com.campuscompanion.attendance.dto.AttendanceEntryResponse;
import com.campuscompanion.attendance.dto.AttendanceSummaryResponse;
import com.campuscompanion.attendance.dto.AttendanceSummaryResponse.SubjectAttendance;
import com.campuscompanion.common.config.AcademicProperties;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.common.web.PageResponse;
import com.campuscompanion.student.StudentContext;
import com.campuscompanion.student.StudentProfileService;
import com.campuscompanion.subject.SubjectService;
import com.campuscompanion.subject.dto.SubjectResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A student's own attendance (FR-ATT-001, FR-ATT-002). The student is always the one who is logged
 * in: every query here takes the student ID from the session.
 */
@Service
@Transactional(readOnly = true)
public class AttendanceService {

  private final AttendanceRecordRepository records;
  private final StudentProfileService students;
  private final SubjectService subjects;
  private final AcademicProperties academic;

  public AttendanceService(
      AttendanceRecordRepository records,
      StudentProfileService students,
      SubjectService subjects,
      AcademicProperties academic) {
    this.records = records;
    this.students = students;
    this.subjects = subjects;
    this.academic = academic;
  }

  /** One row for every subject of the student's current semester, and the overall figure. */
  public AttendanceSummaryResponse summaryFor(CurrentUser currentUser) {
    StudentContext student = students.currentStudent(currentUser);
    BigDecimal required = academic.attendanceRequiredPercent();
    Map<Long, SubjectCount> counts =
        records.countBySubject(student.studentId()).stream()
            .collect(Collectors.toMap(SubjectCount::getSubjectId, Function.identity()));

    List<SubjectAttendance> rows = new ArrayList<>();
    long totalHeld = 0;
    long totalAttended = 0;
    for (SubjectResponse subject : subjects.subjectsOf(student, student.currentSemester())) {
      SubjectCount count = counts.get(subject.id());
      long held = count == null ? 0 : count.getHeld();
      long attended = count == null ? 0 : count.getAttended();
      BigDecimal percent = AttendanceCalculator.percent(attended, held);
      rows.add(
          new SubjectAttendance(
              subject.id(),
              subject.code(),
              subject.name(),
              held,
              attended,
              percent,
              AttendanceCalculator.isBelowRequired(percent, required)));
      totalHeld += held;
      totalAttended += attended;
    }
    return new AttendanceSummaryResponse(
        required, AttendanceCalculator.percent(totalAttended, totalHeld), rows);
  }

  /** The dated history of one subject, newest first. Only the student's own rows can match. */
  public PageResponse<AttendanceEntryResponse> historyFor(
      CurrentUser currentUser, Long subjectId, int page, int size) {
    StudentContext student = students.currentStudent(currentUser);
    return PageResponse.of(
        records
            .findByStudentIdAndSubjectIdOrderByClassDateDescPeriodNoDesc(
                student.studentId(), subjectId, PageRequest.of(page, size))
            .map(
                record ->
                    new AttendanceEntryResponse(
                        record.getClassDate(), record.getPeriodNo(), record.getStatus())));
  }
}
