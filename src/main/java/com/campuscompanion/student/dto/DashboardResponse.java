package com.campuscompanion.student.dto;

import com.campuscompanion.notice.dto.NoticeResponse;
import com.campuscompanion.planner.dto.ExamResponse;
import com.campuscompanion.progress.dto.RecommendationResponse;
import com.campuscompanion.timetable.dto.TimetableEntryResponse;
import java.math.BigDecimal;
import java.util.List;

/** Everything the student's home page shows (FR-STU-001), in one response. */
public record DashboardResponse(
    AttendanceCard attendance,
    ResultsCard results,
    List<TimetableEntryResponse> todayClasses,
    List<NoticeResponse> upcomingDeadlines,
    List<NoticeResponse> recentNotices,
    List<RecommendationResponse> recommendations,
    /** The next exam, the student's own or the college's (FR-PLN-002); null when none. */
    ExamResponse nextExam,
    /** The coming exams, soonest first, at most eight. */
    List<ExamResponse> upcomingExams) {

  public record AttendanceCard(
      BigDecimal overallPercent, BigDecimal requiredPercent, int subjectsBelowRequired) {}

  /** The CGPA, the latest SGPA, and the SGPA of every published semester in order. */
  public record ResultsCard(BigDecimal latestSgpa, BigDecimal cgpa, List<SemesterSgpa> semesters) {}

  public record SemesterSgpa(int semester, BigDecimal sgpa, String resultStatus) {}
}
