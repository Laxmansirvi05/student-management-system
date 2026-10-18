package com.campuscompanion.assessment;

import com.campuscompanion.assessment.dto.ResultsResponse;
import com.campuscompanion.assessment.dto.SemesterResultResponse;
import com.campuscompanion.assessment.dto.SubjectGradeResponse;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.student.StudentContext;
import com.campuscompanion.student.StudentProfileService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A student's own semester results and CGPA (FR-MRK-002). Only published results are returned. The
 * CGPA is never stored: it is calculated here on every read.
 */
@Service
@Transactional(readOnly = true)
public class ResultService {

  private static final java.util.Set<String> NOT_PASSED = java.util.Set.of("F", "Ab", "U");

  private final SemesterResultRepository results;
  private final SubjectResultRepository subjectResults;
  private final StudentProfileService students;

  public ResultService(
      SemesterResultRepository results,
      SubjectResultRepository subjectResults,
      StudentProfileService students) {
    this.results = results;
    this.subjectResults = subjectResults;
    this.students = students;
  }

  public ResultsResponse resultsFor(CurrentUser currentUser) {
    StudentContext student = students.currentStudent(currentUser);
    List<SemesterResult> published =
        results.findByStudentIdOrderBySemesterAsc(student.studentId()).stream()
            .filter(SemesterResult::isPublished)
            .toList();
    List<CgpaCalculator.Term> terms =
        published.stream()
            .map(
                result ->
                    new CgpaCalculator.Term(
                        result.getSgpa(), result.getCreditsRegistered(), result.getResultStatus()))
            .toList();
    java.util.Map<Integer, List<SubjectGradeResponse>> grades =
        gradesBySemester(student.studentId());
    return new ResultsResponse(
        CgpaCalculator.cgpa(terms),
        published.stream()
            .map(result -> toResponse(result, grades.getOrDefault(result.getSemester(), List.of())))
            .toList());
  }

  /** The current grade of every subject, by semester: the latest attempt wins. */
  private java.util.Map<Integer, List<SubjectGradeResponse>> gradesBySemester(Long studentId) {
    java.util.Map<String, List<SubjectResult>> attempts = new java.util.LinkedHashMap<>();
    for (SubjectResult row :
        subjectResults.findByStudentIdOrderBySemesterAscSubjectCodeAscAttemptAsc(studentId)) {
      attempts.computeIfAbsent(row.getSubjectCode(), code -> new java.util.ArrayList<>()).add(row);
    }
    java.util.Map<Integer, List<SubjectGradeResponse>> bySemester = new java.util.TreeMap<>();
    for (List<SubjectResult> subject : attempts.values()) {
      SubjectResult first = subject.get(0);
      SubjectResult latest = subject.get(subject.size() - 1);
      bySemester
          .computeIfAbsent(latest.getSemester(), semester -> new java.util.ArrayList<>())
          .add(
              new SubjectGradeResponse(
                  latest.getSubjectCode(),
                  latest.getSubjectName(),
                  latest.getCredits(),
                  latest.getGrade(),
                  latest.getExam(),
                  subject.size(),
                  subject.size() > 1 ? first.getGrade() : null,
                  !NOT_PASSED.contains(latest.getGrade())));
    }
    return bySemester;
  }

  static SemesterResultResponse toResponse(
      SemesterResult result, List<SubjectGradeResponse> subjects) {
    return new SemesterResultResponse(
        result.getSemester(),
        result.getSgpa(),
        result.getCreditsRegistered(),
        result.getCreditsEarned(),
        result.getResultStatus(),
        subjects);
  }
}
