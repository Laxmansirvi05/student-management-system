package com.campuscompanion.assessment;

import com.campuscompanion.assessment.dto.ResultsResponse;
import com.campuscompanion.assessment.dto.SemesterResultResponse;
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

  private final SemesterResultRepository results;
  private final StudentProfileService students;

  public ResultService(SemesterResultRepository results, StudentProfileService students) {
    this.results = results;
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
    return new ResultsResponse(
        CgpaCalculator.cgpa(terms), published.stream().map(ResultService::toResponse).toList());
  }

  static SemesterResultResponse toResponse(SemesterResult result) {
    return new SemesterResultResponse(
        result.getSemester(),
        result.getSgpa(),
        result.getCreditsRegistered(),
        result.getCreditsEarned(),
        result.getResultStatus());
  }
}
