package com.campuscompanion.timetable;

import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.student.StudentContext;
import com.campuscompanion.student.StudentProfileService;
import com.campuscompanion.subject.SubjectService;
import com.campuscompanion.subject.dto.SubjectResponse;
import com.campuscompanion.timetable.dto.TimetableEntryResponse;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A student's own timetable (FR-TT-001): the classes of their branch, semester and section. Those
 * three values come from the student's profile, never from the request.
 */
@Service
@Transactional(readOnly = true)
public class TimetableService {

  static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm");

  private final TimetableEntryRepository entries;
  private final StudentProfileService students;
  private final SubjectService subjects;

  public TimetableService(
      TimetableEntryRepository entries, StudentProfileService students, SubjectService subjects) {
    this.entries = entries;
    this.students = students;
    this.subjects = subjects;
  }

  /** The week in day and time order, or one day when dayOfWeek is given (1 is Monday). */
  public List<TimetableEntryResponse> timetableFor(CurrentUser currentUser, Integer dayOfWeek) {
    StudentContext student = students.currentStudent(currentUser);
    List<TimetableEntry> found =
        dayOfWeek == null
            ? entries.findByBranchAndSemesterAndSectionOrderByDayOfWeekAscStartTimeAsc(
                student.branch(), student.currentSemester(), student.section())
            : entries.findByBranchAndSemesterAndSectionAndDayOfWeekOrderByStartTimeAsc(
                student.branch(), student.currentSemester(), student.section(), dayOfWeek);
    Map<Long, SubjectResponse> named =
        subjects.byIds(found.stream().map(TimetableEntry::getSubjectId).distinct().toList());
    return found.stream().map(entry -> toResponse(entry, named.get(entry.getSubjectId()))).toList();
  }

  private static TimetableEntryResponse toResponse(TimetableEntry entry, SubjectResponse subject) {
    return new TimetableEntryResponse(
        entry.getDayOfWeek(),
        format(entry.getStartTime()),
        format(entry.getEndTime()),
        subject == null ? null : subject.code(),
        subject == null ? null : subject.name(),
        entry.getRoom(),
        entry.getFacultyName());
  }

  static String format(LocalTime time) {
    return time.format(HOUR_MINUTE);
  }
}
