package com.campuscompanion.assessment;

import com.campuscompanion.assessment.dto.AdminSemesterResultResponse;
import com.campuscompanion.assessment.dto.SemesterResultWriteRequest;
import com.campuscompanion.audit.AuditAction;
import com.campuscompanion.audit.AuditService;
import com.campuscompanion.audit.AuditSnapshot;
import com.campuscompanion.common.error.ValidationException;
import com.campuscompanion.student.StudentProfileService;
import java.time.Clock;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entering semester results (FR-ADM-004). There is one result per student and semester; saving it
 * again replaces the values. Every created or changed result is written to the audit log.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminResultService {

  private final SemesterResultRepository results;
  private final StudentProfileService students;
  private final AuditService audit;
  private final Clock clock;

  public AdminResultService(
      SemesterResultRepository results,
      StudentProfileService students,
      AuditService audit,
      Clock clock) {
    this.results = results;
    this.students = students;
    this.audit = audit;
    this.clock = clock;
  }

  /** Every result of one student, published or not, in semester order. */
  @Transactional(readOnly = true)
  public List<AdminSemesterResultResponse> resultsOf(Long studentId) {
    return results.findByStudentIdOrderBySemesterAsc(studentId).stream()
        .map(AdminResultService::toResponse)
        .toList();
  }

  @Transactional
  public AdminSemesterResultResponse save(SemesterResultWriteRequest request) {
    if (students.findStudent(request.studentId()).isEmpty()) {
      throw new ValidationException("studentId", "Choose an existing student.");
    }
    if (request.sgpa() == null && request.resultStatus() == ResultStatus.PASS) {
      throw new ValidationException("sgpa", "Enter the SGPA of a passed semester.");
    }
    if (request.creditsEarned() > request.creditsRegistered()) {
      throw new ValidationException(
          "creditsEarned", "Credits earned cannot be more than credits registered.");
    }
    SemesterResult result =
        results.findByStudentIdAndSemester(request.studentId(), request.semester()).orElse(null);
    AuditSnapshot before = result == null ? null : snapshot(result);
    if (result == null) {
      result = new SemesterResult(request.studentId(), request.semester());
    }
    result.record(
        request.sgpa(),
        request.creditsRegistered(),
        request.creditsEarned(),
        request.resultStatus(),
        request.published(),
        clock.instant());
    result = results.save(result);
    AuditSnapshot after = snapshot(result);
    if (!after.equals(before)) {
      audit.record(AuditAction.RESULT_UPDATED, "SEMESTER_RESULT", result.getId(), before, after);
    }
    return toResponse(result);
  }

  private static AuditSnapshot snapshot(SemesterResult result) {
    return new AuditSnapshot.Result(
        result.getSgpa() == null ? null : result.getSgpa().stripTrailingZeros(),
        result.getCreditsRegistered(),
        result.getCreditsEarned(),
        result.getResultStatus().name(),
        result.isPublished());
  }

  static AdminSemesterResultResponse toResponse(SemesterResult result) {
    return new AdminSemesterResultResponse(
        result.getStudentId(),
        result.getSemester(),
        result.getSgpa(),
        result.getCreditsRegistered(),
        result.getCreditsEarned(),
        result.getResultStatus(),
        result.isPublished());
  }
}
