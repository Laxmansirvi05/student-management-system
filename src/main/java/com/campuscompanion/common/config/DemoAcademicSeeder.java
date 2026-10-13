package com.campuscompanion.common.config;

import com.campuscompanion.assessment.AdminAssessmentService;
import com.campuscompanion.assessment.AdminResultService;
import com.campuscompanion.assessment.AssessmentType;
import com.campuscompanion.assessment.ResultStatus;
import com.campuscompanion.assessment.dto.AssessmentResponse;
import com.campuscompanion.assessment.dto.AssessmentWriteRequest;
import com.campuscompanion.assessment.dto.MarksSheet;
import com.campuscompanion.assessment.dto.SemesterResultWriteRequest;
import com.campuscompanion.attendance.AdminAttendanceService;
import com.campuscompanion.attendance.AttendanceStatus;
import com.campuscompanion.attendance.dto.AttendanceSheet;
import com.campuscompanion.auth.AdminAccounts;
import com.campuscompanion.auth.Role;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.notice.AdminNoticeService;
import com.campuscompanion.notice.NoticeCategory;
import com.campuscompanion.notice.dto.NoticeWriteRequest;
import com.campuscompanion.student.StudentContext;
import com.campuscompanion.student.StudentProfileService;
import com.campuscompanion.subject.AdminSubjectService;
import com.campuscompanion.subject.SubjectService;
import com.campuscompanion.subject.dto.AdminSubjectResponse;
import com.campuscompanion.subject.dto.SubjectWriteRequest;
import com.campuscompanion.subject.dto.UnitWriteRequest;
import com.campuscompanion.timetable.AdminTimetableService;
import com.campuscompanion.timetable.dto.TimetableWriteRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Synthetic academic records for the demo students of CSE semester 3: subjects and units, a weekly
 * timetable, four weeks of attendance, assessments with marks, semester results and notices.
 *
 * <p>Everything is written through the same admin services an administrator uses, so the demo data
 * obeys every rule and every change that should be audited is audited. All values are invented and
 * follow fixed patterns; nothing here describes a real person, subject teacher or class.
 */
@Component
public class DemoAcademicSeeder {

  private static final Logger log = LoggerFactory.getLogger(DemoAcademicSeeder.class);

  private static final String BRANCH = "CSE";
  private static final int SEMESTER = 3;
  private static final String ACADEMIC_YEAR = "2026-27";
  private static final int DAYS_OF_ATTENDANCE = 28;

  /** Code, name, credits, then the unit titles in order. */
  private static final List<List<String>> SUBJECTS =
      List.of(
          List.of(
              "CS301",
              "Data Structures",
              "4",
              "Arrays and Complexity",
              "Stacks, Queues and Linked Lists",
              "Trees",
              "Graphs",
              "Hashing"),
          List.of(
              "MA301",
              "Discrete Mathematics",
              "4",
              "Logic and Sets",
              "Relations and Functions",
              "Combinatorics",
              "Graph Theory"),
          List.of(
              "EC301",
              "Digital Logic Design",
              "3",
              "Boolean Algebra",
              "Combinational Circuits",
              "Sequential Circuits",
              "Registers and Counters"),
          List.of(
              "CS302",
              "Object Oriented Programming",
              "4",
              "Classes and Objects",
              "Inheritance",
              "Polymorphism",
              "Exceptions and Collections"),
          List.of("ES301", "Environmental Science", "2"));

  /** One class of the week: day (1 is Monday), start, end, subject code, room. */
  private record WeeklyClass(int day, String start, String end, String code, String room) {}

  private static final List<WeeklyClass> WEEK =
      List.of(
          new WeeklyClass(1, "09:00", "10:00", "EC301", "Room 204"),
          new WeeklyClass(1, "10:00", "11:00", "MA301", "Room 204"),
          new WeeklyClass(1, "11:15", "12:15", "CS302", "Room 201"),
          new WeeklyClass(1, "14:00", "16:00", "CS301", "Lab 1"),
          new WeeklyClass(2, "09:00", "10:00", "CS301", "Room 204"),
          new WeeklyClass(2, "10:00", "11:00", "EC301", "Room 204"),
          new WeeklyClass(2, "11:15", "12:15", "MA301", "Room 108"),
          new WeeklyClass(3, "09:00", "10:00", "MA301", "Room 204"),
          new WeeklyClass(3, "10:00", "11:00", "CS301", "Room 204"),
          new WeeklyClass(3, "11:15", "12:15", "CS302", "Room 201"),
          new WeeklyClass(3, "14:00", "16:00", "EC301", "Lab 3"),
          new WeeklyClass(4, "09:00", "10:00", "CS302", "Room 201"),
          new WeeklyClass(4, "10:00", "11:00", "MA301", "Room 204"),
          new WeeklyClass(4, "11:15", "12:15", "CS301", "Room 204"),
          new WeeklyClass(4, "14:00", "15:00", "EC301", "Room 204"),
          new WeeklyClass(5, "09:00", "10:00", "EC301", "Room 204"),
          new WeeklyClass(5, "10:00", "11:00", "CS302", "Room 201"),
          new WeeklyClass(5, "11:15", "12:15", "MA301", "Room 204"),
          new WeeklyClass(5, "14:00", "16:00", "CS302", "Lab 2"),
          new WeeklyClass(6, "09:00", "10:00", "CS301", "Room 204"));

  private final SubjectService subjects;
  private final AdminSubjectService adminSubjects;
  private final AdminTimetableService timetable;
  private final AdminAttendanceService attendance;
  private final AdminAssessmentService assessments;
  private final AdminResultService results;
  private final AdminNoticeService notices;
  private final StudentProfileService students;
  private final AdminAccounts admins;
  private final Clock clock;

  public DemoAcademicSeeder(
      SubjectService subjects,
      AdminSubjectService adminSubjects,
      AdminTimetableService timetable,
      AdminAttendanceService attendance,
      AdminAssessmentService assessments,
      AdminResultService results,
      AdminNoticeService notices,
      StudentProfileService students,
      AdminAccounts admins,
      Clock clock) {
    this.subjects = subjects;
    this.adminSubjects = adminSubjects;
    this.timetable = timetable;
    this.attendance = attendance;
    this.assessments = assessments;
    this.results = results;
    this.notices = notices;
    this.students = students;
    this.admins = admins;
    this.clock = clock;
  }

  /** Loads the academic demo data once. It is all saved, or none of it is. */
  @Transactional
  public void seedIfEmpty() {
    if (subjects.anySubjectExists()) {
      return;
    }
    Optional<Long> adminId = admins.firstAdminId();
    if (adminId.isEmpty()) {
      log.info(
          "Academic demo data was not loaded: it needs an administrator as its author. Set"
              + " ADMIN_BOOTSTRAP_EMAIL and ADMIN_BOOTSTRAP_PASSWORD and start again.");
      return;
    }
    CurrentUser admin = new CurrentUser(adminId.get(), Role.ADMIN);
    actAs(admin);
    try {
      Map<String, Long> subjectIds = seedSubjects();
      seedTimetable(subjectIds);
      seedAttendance(subjectIds, admin);
      seedMarks(subjectIds, admin);
      seedResults();
      seedNotices(admin);
      log.info("Loaded synthetic academic demo data for {} semester {}.", BRANCH, SEMESTER);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  /** The admin services check the role, so the seeder works as the first administrator. */
  static void actAs(CurrentUser admin) {
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            admin, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    SecurityContextHolder.setContext(context);
  }

  private Map<String, Long> seedSubjects() {
    Map<String, Long> ids = new LinkedHashMap<>();
    for (List<String> row : SUBJECTS) {
      AdminSubjectResponse subject =
          adminSubjects.create(
              new SubjectWriteRequest(
                  row.get(0), row.get(1), BRANCH, SEMESTER, Integer.valueOf(row.get(2)), true));
      for (int unit = 3; unit < row.size(); unit++) {
        adminSubjects.addUnit(subject.id(), new UnitWriteRequest(unit - 2, row.get(unit)));
      }
      ids.put(subject.code(), subject.id());
    }
    return ids;
  }

  private void seedTimetable(Map<String, Long> subjectIds) {
    for (String section : List.of("A", "B")) {
      for (WeeklyClass slot : WEEK) {
        timetable.create(
            new TimetableWriteRequest(
                BRANCH,
                SEMESTER,
                section,
                slot.day(),
                slot.start(),
                slot.end(),
                subjectIds.get(slot.code()),
                slot.room(),
                "Faculty " + (char) ('A' + SUBJECTS.indexOf(subjectRow(slot.code())))));
      }
    }
  }

  /** One sheet for every class held in the last four weeks, up to yesterday. */
  private void seedAttendance(Map<String, Long> subjectIds, CurrentUser admin) {
    LocalDate today = CampusTime.today(clock);
    for (String section : List.of("A", "B")) {
      List<StudentContext> classList = students.studentsOfClass(BRANCH, SEMESTER, section);
      if (classList.isEmpty()) {
        continue;
      }
      for (int daysAgo = DAYS_OF_ATTENDANCE; daysAgo >= 1; daysAgo--) {
        LocalDate date = today.minusDays(daysAgo);
        int period = 0;
        for (WeeklyClass slot : WEEK) {
          if (slot.day() != date.getDayOfWeek().getValue()) {
            continue;
          }
          period++;
          List<AttendanceSheet.Entry> entries = new ArrayList<>();
          for (int index = 0; index < classList.size(); index++) {
            entries.add(
                new AttendanceSheet.Entry(
                    classList.get(index).studentId(),
                    null,
                    null,
                    present(index, daysAgo, slot.code())
                        ? AttendanceStatus.PRESENT
                        : AttendanceStatus.ABSENT));
          }
          attendance.save(
              new AttendanceSheet(subjectIds.get(slot.code()), section, date, period, entries),
              admin);
        }
      }
    }
  }

  /**
   * A fixed pattern of absences. Each subject is missed about one class in six to twelve, and the
   * first student misses every third class of Digital Logic Design, so that one subject falls below
   * the required 75%.
   */
  private static boolean present(int studentIndex, int daysAgo, String code) {
    int subjectIndex = SUBJECTS.indexOf(subjectRow(code));
    int every = code.equals("EC301") && studentIndex == 0 ? 3 : 6 + subjectIndex * 2;
    return (daysAgo + studentIndex * 2 + subjectIndex) % every != 0;
  }

  private void seedMarks(Map<String, Long> subjectIds, CurrentUser admin) {
    List<StudentContext> classList = students.studentsOfSemester(BRANCH, SEMESTER);
    int subjectIndex = 0;
    for (Map.Entry<String, Long> subject : subjectIds.entrySet()) {
      if (subject.getKey().equals("ES301")) {
        continue; // Nothing published yet for this subject, to show that state.
      }
      AssessmentResponse internal =
          assessment(subject.getValue(), "Internal 1", AssessmentType.INTERNAL, 50, true);
      AssessmentResponse assignment =
          assessment(subject.getValue(), "Assignment 1", AssessmentType.ASSIGNMENT, 20, true);
      assessment(subject.getValue(), "Internal 2", AssessmentType.INTERNAL, 50, false);

      List<MarksSheet.Entry> internalMarks = new ArrayList<>();
      List<MarksSheet.Entry> assignmentMarks = new ArrayList<>();
      for (int index = 0; index < classList.size(); index++) {
        Long studentId = classList.get(index).studentId();
        internalMarks.add(marks(studentId, 28 + (index * 7 + subjectIndex * 5) % 20, false));
        boolean absent = subject.getKey().equals("EC301") && index == 1;
        assignmentMarks.add(marks(studentId, 12 + (index * 3 + subjectIndex * 2) % 9, absent));
      }
      if (!classList.isEmpty()) {
        assessments.save(internal.id(), new MarksSheet(internalMarks), admin);
        assessments.save(assignment.id(), new MarksSheet(assignmentMarks), admin);
      }
      subjectIndex++;
    }
  }

  private AssessmentResponse assessment(
      Long subjectId, String name, AssessmentType type, int maxMarks, boolean published) {
    return assessments.create(
        new AssessmentWriteRequest(
            subjectId, name, type, BigDecimal.valueOf(maxMarks), null, ACADEMIC_YEAR, published));
  }

  private static MarksSheet.Entry marks(Long studentId, int marksObtained, boolean absent) {
    return new MarksSheet.Entry(
        studentId, null, null, absent ? null : BigDecimal.valueOf(marksObtained), absent);
  }

  /** Two published semesters each. The first student failed one course in semester 1. */
  private void seedResults() {
    List<StudentContext> classList = students.studentsOfSemester(BRANCH, SEMESTER);
    for (int index = 0; index < classList.size(); index++) {
      Long studentId = classList.get(index).studentId();
      boolean failedOne = index == 0;
      results.save(
          new SemesterResultWriteRequest(
              studentId,
              1,
              failedOne ? new BigDecimal("6.50") : new BigDecimal("7.20").add(tenths(index * 3)),
              24,
              failedOne ? 20 : 24,
              failedOne ? ResultStatus.FAIL : ResultStatus.PASS,
              true));
      results.save(
          new SemesterResultWriteRequest(
              studentId,
              2,
              new BigDecimal("7.80").add(tenths(index * 2)),
              22,
              22,
              ResultStatus.PASS,
              true));
    }
  }

  private static BigDecimal tenths(int count) {
    return BigDecimal.valueOf(count, 1);
  }

  private void seedNotices(CurrentUser admin) {
    LocalDate today = CampusTime.today(clock);
    publish(
        admin,
        NoticeCategory.EXAM,
        "Mid-term examination schedule",
        null,
        today.plusDays(2),
        "The mid-term examinations for Semester 3 begin next week.\n\nCheck your timetable for the room of each paper. Reach the hall ten minutes before the start and carry your college identity card.\n\nIf a paper clashes with another one, tell the examination office before the due date.");
    publish(
        admin,
        NoticeCategory.ASSIGNMENT,
        "Data Structures: Assignment 2 on linked lists",
        BRANCH,
        today.plusDays(6),
        "Implement a singly linked list with insert, delete, search and reverse operations, and write a short note on the time taken by each.\n\nSubmit one PDF. Late submissions lose marks.");
    publish(
        admin,
        NoticeCategory.GENERAL,
        "Library books due for renewal",
        null,
        today.minusDays(1),
        "Books issued before the start of this month are due for renewal. Renew them at the library desk to avoid a fine.");
    publish(
        admin,
        NoticeCategory.EVENT,
        "Coding club: weekend problem-solving session",
        null,
        today.plusDays(12),
        "The coding club is holding a problem-solving session this month. Teams of two or three. Register with your class representative before the due date.");
    publish(
        admin,
        NoticeCategory.ASSIGNMENT,
        "Digital Logic Design: lab record submission",
        BRANCH,
        today.plusDays(9),
        "Complete experiments 1 to 6 in your lab record and submit it to the lab assistant.");
    publish(
        admin,
        NoticeCategory.GENERAL,
        "Network maintenance in the main block",
        null,
        null,
        "The campus network in the main block will be under maintenance on Saturday afternoon. Lab computers may be offline during that time.");
    // A draft stays hidden from students until an admin publishes it.
    notices.create(
        new NoticeWriteRequest(
            "Sports day volunteers",
            "Draft: details to follow.",
            NoticeCategory.EVENT,
            null,
            null,
            null),
        admin);
  }

  private void publish(
      CurrentUser admin,
      NoticeCategory category,
      String title,
      String branch,
      LocalDate dueDate,
      String body) {
    Long id =
        notices
            .create(
                new NoticeWriteRequest(
                    title, body, category, branch, branch == null ? null : SEMESTER, dueDate),
                admin)
            .id();
    notices.publish(id);
  }

  private static List<String> subjectRow(String code) {
    return SUBJECTS.stream().filter(row -> row.get(0).equals(code)).findFirst().orElseThrow();
  }
}
