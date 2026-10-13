package com.campuscompanion.assessment;

import com.campuscompanion.assessment.dto.AssessmentResponse;
import com.campuscompanion.assessment.dto.AssessmentWriteRequest;
import com.campuscompanion.assessment.dto.MarksSheet;
import com.campuscompanion.audit.AuditAction;
import com.campuscompanion.audit.AuditService;
import com.campuscompanion.audit.AuditSnapshot;
import com.campuscompanion.common.error.ConflictException;
import com.campuscompanion.common.error.FieldErrorDetail;
import com.campuscompanion.common.error.ResourceNotFoundException;
import com.campuscompanion.common.error.ValidationException;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.common.web.SheetSaved;
import com.campuscompanion.student.StudentContext;
import com.campuscompanion.student.StudentProfileService;
import com.campuscompanion.subject.SubjectService;
import com.campuscompanion.subject.dto.SubjectResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assessments and their marks sheets (FR-ADM-003). A marks sheet is saved whole: if any row is
 * invalid, nothing is saved. Every change to marks that were already saved is written to the audit
 * log with the old and the new values.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminAssessmentService {

  private final AssessmentRepository assessments;
  private final MarkRepository marks;
  private final StudentProfileService students;
  private final SubjectService subjects;
  private final AuditService audit;

  public AdminAssessmentService(
      AssessmentRepository assessments,
      MarkRepository marks,
      StudentProfileService students,
      SubjectService subjects,
      AuditService audit) {
    this.assessments = assessments;
    this.marks = marks;
    this.students = students;
    this.subjects = subjects;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public List<AssessmentResponse> list(Long subjectId) {
    List<Assessment> found =
        subjectId == null
            ? assessments.findAllByOrderBySubjectIdAscIdAsc()
            : assessments.findBySubjectIdOrderByIdAsc(subjectId);
    return found.stream().map(AdminAssessmentService::toResponse).toList();
  }

  @Transactional
  public AssessmentResponse create(AssessmentWriteRequest request) {
    requireSubject(request.subjectId());
    String name = request.name().strip();
    if (assessments.existsBySubjectIdAndNameAndAcademicYear(
        request.subjectId(), name, request.academicYear())) {
      throw new ConflictException(
          "This subject already has an assessment with that name this year.");
    }
    Assessment assessment =
        new Assessment(
            request.subjectId(),
            name,
            request.type(),
            request.maxMarks(),
            request.heldOn(),
            request.academicYear(),
            request.published());
    return toResponse(assessments.save(assessment));
  }

  @Transactional
  public AssessmentResponse update(Long id, AssessmentWriteRequest request) {
    Assessment assessment = assessments.findById(id).orElseThrow(ResourceNotFoundException::new);
    requireSubject(request.subjectId());
    String name = request.name().strip();
    if (assessments.existsBySubjectIdAndNameAndAcademicYearAndIdNot(
        request.subjectId(), name, request.academicYear(), id)) {
      throw new ConflictException(
          "This subject already has an assessment with that name this year.");
    }
    BigDecimal highest = marks.highestMarks(id);
    if (highest != null && highest.compareTo(request.maxMarks()) > 0) {
      throw new ValidationException(
          "maxMarks",
          "A student already has " + highest.toPlainString() + " marks in this assessment.");
    }
    assessment.update(
        request.subjectId(),
        name,
        request.type(),
        request.maxMarks(),
        request.heldOn(),
        request.academicYear(),
        request.published());
    return toResponse(assessment);
  }

  /** Every student of the subject's branch and semester, with the marks already saved, if any. */
  @Transactional(readOnly = true)
  public MarksSheet sheet(Long assessmentId) {
    Assessment assessment =
        assessments.findById(assessmentId).orElseThrow(ResourceNotFoundException::new);
    Map<Long, Mark> saved = savedMarks(assessmentId);
    return new MarksSheet(
        classListOf(assessment).stream()
            .map(
                student -> {
                  Mark mark = saved.get(student.studentId());
                  return new MarksSheet.Entry(
                      student.studentId(),
                      student.rollNo(),
                      student.fullName(),
                      mark == null ? null : mark.getMarksObtained(),
                      mark != null && mark.isAbsent());
                })
            .toList());
  }

  @Transactional
  public SheetSaved save(Long assessmentId, MarksSheet sheet, CurrentUser admin) {
    Assessment assessment =
        assessments.findById(assessmentId).orElseThrow(ResourceNotFoundException::new);
    rejectInvalidRows(sheet, assessment, classListOf(assessment));

    Map<Long, Mark> saved = savedMarks(assessmentId);
    int changed = 0;
    for (MarksSheet.Entry entry : sheet.entries()) {
      BigDecimal obtained = entry.absent() ? null : entry.marksObtained();
      Mark existing = saved.get(entry.studentId());
      if (existing == null) {
        marks.save(new Mark(entry.studentId(), assessmentId, obtained, entry.absent(), admin.id()));
        changed++;
      } else if (differs(existing, obtained, entry.absent())) {
        AuditSnapshot before =
            new AuditSnapshot.Marks(existing.getMarksObtained(), existing.isAbsent());
        existing.record(obtained, entry.absent(), admin.id());
        audit.record(
            AuditAction.MARKS_UPDATED,
            "MARK",
            existing.getId(),
            before,
            new AuditSnapshot.Marks(obtained, entry.absent()));
        changed++;
      }
    }
    return new SheetSaved(sheet.entries().size(), changed);
  }

  /** Each row: a student of this class, once; absent, or marks from 0 to the maximum. */
  private static void rejectInvalidRows(
      MarksSheet sheet, Assessment assessment, List<StudentContext> classList) {
    Set<Long> inClass =
        classList.stream().map(StudentContext::studentId).collect(Collectors.toSet());
    Set<Long> seen = new HashSet<>();
    List<FieldErrorDetail> errors = new ArrayList<>();
    for (int i = 0; i < sheet.entries().size(); i++) {
      MarksSheet.Entry entry = sheet.entries().get(i);
      String row = "entries[" + i + "]";
      if (!inClass.contains(entry.studentId())) {
        errors.add(
            new FieldErrorDetail(row + ".studentId", "This student does not take this subject."));
      } else if (!seen.add(entry.studentId())) {
        errors.add(new FieldErrorDetail(row + ".studentId", "This student appears twice."));
      }
      if (!entry.absent()) {
        String problem = marksProblem(entry.marksObtained(), assessment.getMaxMarks());
        if (problem != null) {
          errors.add(new FieldErrorDetail(row + ".marksObtained", problem));
        }
      }
    }
    if (!errors.isEmpty()) {
      throw new ValidationException(errors);
    }
  }

  private static String marksProblem(BigDecimal marksObtained, BigDecimal maxMarks) {
    if (marksObtained == null) {
      return "Enter the marks, or mark the student absent.";
    }
    if (marksObtained.signum() < 0) {
      return "Marks cannot be below zero.";
    }
    if (marksObtained.compareTo(maxMarks) > 0) {
      return "Marks cannot be above the maximum of " + maxMarks.toPlainString() + ".";
    }
    if (marksObtained.stripTrailingZeros().scale() > 2) {
      return "Use at most two decimals.";
    }
    return null;
  }

  private static boolean differs(Mark existing, BigDecimal obtained, boolean absent) {
    if (existing.isAbsent() != absent) {
      return true;
    }
    BigDecimal before = existing.getMarksObtained();
    return !(Objects.equals(before, obtained)
        || (before != null && obtained != null && before.compareTo(obtained) == 0));
  }

  private List<StudentContext> classListOf(Assessment assessment) {
    SubjectResponse subject =
        subjects.find(assessment.getSubjectId()).orElseThrow(ResourceNotFoundException::new);
    return students.studentsOfSemester(subject.branch(), subject.semester());
  }

  private Map<Long, Mark> savedMarks(Long assessmentId) {
    return marks.findByAssessmentId(assessmentId).stream()
        .collect(Collectors.toMap(Mark::getStudentId, Function.identity()));
  }

  private void requireSubject(Long subjectId) {
    if (subjects.find(subjectId).isEmpty()) {
      throw new ValidationException("subjectId", "Choose an existing subject.");
    }
  }

  static AssessmentResponse toResponse(Assessment assessment) {
    return new AssessmentResponse(
        assessment.getId(),
        assessment.getSubjectId(),
        assessment.getName(),
        assessment.getType(),
        assessment.getMaxMarks(),
        assessment.getHeldOn(),
        assessment.getAcademicYear(),
        assessment.isPublished());
  }
}
