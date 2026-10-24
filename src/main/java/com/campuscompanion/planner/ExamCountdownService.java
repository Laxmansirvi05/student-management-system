package com.campuscompanion.planner;

import com.campuscompanion.common.config.CampusTime;
import com.campuscompanion.common.error.ResourceNotFoundException;
import com.campuscompanion.common.error.ValidationException;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.notice.NoticeService;
import com.campuscompanion.planner.dto.ExamListResponse;
import com.campuscompanion.planner.dto.ExamRequest;
import com.campuscompanion.planner.dto.ExamResponse;
import com.campuscompanion.planner.dto.PlannerSubject;
import com.campuscompanion.student.StudentContext;
import com.campuscompanion.student.StudentProfileService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The exam countdown (FR-PLN-002): the exam dates a student adds, together with the exam dates the
 * college publishes as notices (category Exam, with a due date) for the student's class. The
 * student's own exams can be added, edited and deleted; the college's are read-only here.
 */
@Service
@PreAuthorize("hasRole('STUDENT')")
public class ExamCountdownService {

  /** College exam dates are looked up this far ahead. */
  static final int COLLEGE_DAYS_AHEAD = 366;

  /** At most this many past exams are listed. */
  static final int PAST_SHOWN = 20;

  private static final Comparator<ExamResponse> SOONEST_FIRST =
      Comparator.comparing(ExamResponse::examDate)
          .thenComparing(exam -> exam.source().equals("COLLEGE") ? 0 : 1)
          .thenComparing(ExamResponse::name, String.CASE_INSENSITIVE_ORDER);

  private final StudentExamRepository exams;
  private final StudentProfileService students;
  private final NoticeService notices;
  private final PlannerSubjects subjects;
  private final Clock clock;

  public ExamCountdownService(
      StudentExamRepository exams,
      StudentProfileService students,
      NoticeService notices,
      PlannerSubjects subjects,
      Clock clock) {
    this.exams = exams;
    this.students = students;
    this.notices = notices;
    this.subjects = subjects;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public ExamListResponse list(CurrentUser user) {
    StudentContext student = students.currentStudent(user);
    LocalDate today = CampusTime.today(clock);
    List<ExamResponse> own =
        respond(exams.findByStudentIdOrderByExamDateAscIdAsc(student.studentId()), today);
    List<ExamResponse> upcoming =
        new ArrayList<>(own.stream().filter(exam -> !exam.past()).toList());
    upcoming.addAll(collegeExams(student, today));
    upcoming.sort(SOONEST_FIRST);
    List<ExamResponse> past =
        own.stream()
            .filter(ExamResponse::past)
            .sorted(SOONEST_FIRST.reversed())
            .limit(PAST_SHOWN)
            .toList();
    return new ExamListResponse(upcoming, past);
  }

  /** The next exam, of the student's own or the college's, for the dashboard; null when none. */
  @Transactional(readOnly = true)
  public ExamResponse next(CurrentUser user) {
    List<ExamResponse> upcoming = list(user).upcoming();
    return upcoming.isEmpty() ? null : upcoming.get(0);
  }

  @Transactional
  public ExamResponse create(CurrentUser user, ExamRequest request) {
    Long studentId = students.currentStudent(user).studentId();
    LocalDate today = CampusTime.today(clock);
    checkDate(request.examDate(), null, today);
    StudentExam exam =
        new StudentExam(
            studentId,
            request.name().strip(),
            subjects.checked(user, request.subjectId()),
            request.examDate(),
            clock.instant());
    return respond(List.of(exams.save(exam)), today).get(0);
  }

  @Transactional
  public ExamResponse update(CurrentUser user, Long id, ExamRequest request) {
    StudentExam exam = owned(user, id);
    LocalDate today = CampusTime.today(clock);
    checkDate(request.examDate(), exam.getExamDate(), today);
    exam.change(
        request.name().strip(), subjects.checked(user, request.subjectId()), request.examDate());
    return respond(List.of(exam), today).get(0);
  }

  @Transactional
  public void delete(CurrentUser user, Long id) {
    exams.delete(owned(user, id));
  }

  private StudentExam owned(CurrentUser user, Long id) {
    Long studentId = students.currentStudent(user).studentId();
    return exams.findByIdAndStudentId(id, studentId).orElseThrow(ResourceNotFoundException::new);
  }

  private static void checkDate(LocalDate chosen, LocalDate current, LocalDate today) {
    String problem = PlannerRules.problemWithDate(chosen, current, today);
    if (problem != null) {
      throw new ValidationException("examDate", problem);
    }
  }

  private List<ExamResponse> collegeExams(StudentContext student, LocalDate today) {
    return notices.collegeExamsFor(student, today, today.plusDays(COLLEGE_DAYS_AHEAD)).stream()
        .map(
            exam ->
                new ExamResponse(
                    null, "COLLEGE", exam.noticeId(), exam.name(), null, exam.examDate(), false))
        .toList();
  }

  private List<ExamResponse> respond(List<StudentExam> list, LocalDate today) {
    Map<Long, PlannerSubject> named =
        subjects.named(
            list.stream().map(StudentExam::getSubjectId).filter(Objects::nonNull).toList());
    return list.stream()
        .map(
            exam ->
                new ExamResponse(
                    exam.getId(),
                    "MINE",
                    null,
                    exam.getName(),
                    exam.getSubjectId() == null ? null : named.get(exam.getSubjectId()),
                    exam.getExamDate(),
                    PlannerRules.isPast(exam.getExamDate(), today)))
        .toList();
  }
}
