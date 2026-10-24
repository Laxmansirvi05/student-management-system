package com.campuscompanion.student;

import com.campuscompanion.assessment.ResultService;
import com.campuscompanion.assessment.dto.ResultsResponse;
import com.campuscompanion.assessment.dto.SemesterResultResponse;
import com.campuscompanion.attendance.AttendanceService;
import com.campuscompanion.attendance.dto.AttendanceSummaryResponse;
import com.campuscompanion.common.config.CampusTime;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.notice.NoticeService;
import com.campuscompanion.planner.ExamCountdownService;
import com.campuscompanion.progress.ProgressService;
import com.campuscompanion.student.dto.DashboardResponse;
import com.campuscompanion.student.dto.DashboardResponse.AttendanceCard;
import com.campuscompanion.student.dto.DashboardResponse.ResultsCard;
import com.campuscompanion.student.dto.DashboardResponse.SemesterSgpa;
import com.campuscompanion.timetable.TimetableService;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The student's home page (FR-STU-001). It adds nothing of its own: each card is a short summary of
 * what the feature's own service returns, so the dashboard can never disagree with the pages it
 * links to.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

  private static final int RECENT_NOTICES = 5;
  private static final int DEADLINE_DAYS = 30;
  private static final int UPCOMING_EXAMS = 8;
  private static final int RECOMMENDATIONS = 3;

  private final StudentProfileService students;
  private final AttendanceService attendance;
  private final ResultService results;
  private final TimetableService timetable;
  private final NoticeService notices;
  private final ProgressService progress;
  private final ExamCountdownService exams;
  private final Clock clock;

  public DashboardService(
      StudentProfileService students,
      AttendanceService attendance,
      ResultService results,
      TimetableService timetable,
      NoticeService notices,
      ProgressService progress,
      ExamCountdownService exams,
      Clock clock) {
    this.students = students;
    this.attendance = attendance;
    this.results = results;
    this.timetable = timetable;
    this.notices = notices;
    this.progress = progress;
    this.exams = exams;
    this.clock = clock;
  }

  public DashboardResponse dashboardFor(CurrentUser currentUser) {
    StudentContext student = students.currentStudent(currentUser);
    int today = CampusTime.today(clock).getDayOfWeek().getValue();
    return new DashboardResponse(
        attendanceCard(attendance.summaryFor(currentUser)),
        resultsCard(results.resultsFor(currentUser)),
        timetable.timetableFor(currentUser, today),
        notices.deadlinesFor(student, DEADLINE_DAYS),
        notices.recentFor(student, RECENT_NOTICES),
        progress.recommendationsFor(student.studentId()).stream().limit(RECOMMENDATIONS).toList(),
        exams.next(currentUser),
        exams.list(currentUser).upcoming().stream().limit(UPCOMING_EXAMS).toList());
  }

  private static AttendanceCard attendanceCard(AttendanceSummaryResponse summary) {
    int below =
        (int)
            summary.subjects().stream()
                .filter(AttendanceSummaryResponse.SubjectAttendance::belowRequired)
                .count();
    return new AttendanceCard(summary.overallPercent(), summary.requiredPercent(), below);
  }

  /** The latest SGPA is the one of the highest published semester. */
  private static ResultsCard resultsCard(ResultsResponse published) {
    List<SemesterResultResponse> semesters = published.semesters();
    BigDecimal latest = semesters.isEmpty() ? null : semesters.get(semesters.size() - 1).sgpa();
    List<SemesterSgpa> each =
        semesters.stream()
            .map(
                semester ->
                    new SemesterSgpa(
                        semester.semester(), semester.sgpa(), semester.resultStatus().name()))
            .toList();
    return new ResultsCard(latest, published.cgpa(), each);
  }
}
