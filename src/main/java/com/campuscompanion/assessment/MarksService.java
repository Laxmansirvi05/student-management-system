package com.campuscompanion.assessment;

import com.campuscompanion.assessment.dto.SubjectMarksResponse;
import com.campuscompanion.assessment.dto.SubjectMarksResponse.AssessmentMark;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.student.StudentContext;
import com.campuscompanion.student.StudentProfileService;
import com.campuscompanion.subject.SubjectService;
import com.campuscompanion.subject.dto.SubjectResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A student's own marks (FR-MRK-001). Only published assessments are read from the database, and
 * only the logged-in student's marks, so an unpublished mark can never be returned.
 */
@Service
@Transactional(readOnly = true)
public class MarksService {

  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  private final AssessmentRepository assessments;
  private final MarkRepository marks;
  private final StudentProfileService students;
  private final SubjectService subjects;

  public MarksService(
      AssessmentRepository assessments,
      MarkRepository marks,
      StudentProfileService students,
      SubjectService subjects) {
    this.assessments = assessments;
    this.marks = marks;
    this.students = students;
    this.subjects = subjects;
  }

  /**
   * Marks grouped by subject. Without a semester, the student's current one is used. A subject with
   * nothing published yet is listed with no assessments.
   */
  public List<SubjectMarksResponse> marksFor(
      CurrentUser currentUser, Long subjectId, Integer semester) {
    StudentContext student = students.currentStudent(currentUser);
    int wanted = semester == null ? student.currentSemester() : semester;
    List<SubjectResponse> wantedSubjects =
        subjects.subjectsOf(student, wanted).stream()
            .filter(subject -> subjectId == null || subject.id().equals(subjectId))
            .toList();
    List<Long> subjectIds = wantedSubjects.stream().map(SubjectResponse::id).toList();

    List<Assessment> published =
        assessments.findBySubjectIdInAndPublishedTrueOrderBySubjectIdAscIdAsc(subjectIds);
    Map<Long, Mark> ownMarks =
        marks
            .findByStudentIdAndAssessmentIdIn(
                student.studentId(), published.stream().map(Assessment::getId).toList())
            .stream()
            .collect(Collectors.toMap(Mark::getAssessmentId, Function.identity()));

    return wantedSubjects.stream()
        .map(
            subject ->
                new SubjectMarksResponse(
                    subject.id(),
                    subject.code(),
                    subject.name(),
                    published.stream()
                        .filter(assessment -> assessment.getSubjectId().equals(subject.id()))
                        .map(assessment -> toMark(assessment, ownMarks.get(assessment.getId())))
                        .toList()))
        .toList();
  }

  private static AssessmentMark toMark(Assessment assessment, Mark mark) {
    boolean absent = mark != null && mark.isAbsent();
    BigDecimal obtained = mark == null || absent ? null : mark.getMarksObtained();
    return new AssessmentMark(
        assessment.getId(),
        assessment.getName(),
        assessment.getType(),
        assessment.getMaxMarks(),
        obtained,
        absent,
        percent(obtained, assessment.getMaxMarks()));
  }

  /** Marks as a percentage of the maximum, to two decimals. Null when there are no marks. */
  static BigDecimal percent(BigDecimal obtained, BigDecimal maxMarks) {
    if (obtained == null) {
      return null;
    }
    return obtained.multiply(HUNDRED).divide(maxMarks, 2, RoundingMode.HALF_UP);
  }
}
