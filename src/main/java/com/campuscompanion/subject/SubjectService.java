package com.campuscompanion.subject;

import com.campuscompanion.auth.Role;
import com.campuscompanion.common.error.ResourceNotFoundException;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.student.StudentContext;
import com.campuscompanion.student.StudentProfileService;
import com.campuscompanion.subject.dto.SubjectResponse;
import com.campuscompanion.subject.dto.UnitResponse;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reading subjects and their units. Students see the active subjects of their own branch; an admin
 * sees the active subjects of every branch. Other features use this class to turn subject IDs into
 * codes and names.
 */
@Service
@Transactional(readOnly = true)
public class SubjectService {

  private final SubjectRepository subjects;
  private final SubjectUnitRepository units;
  private final StudentProfileService students;

  public SubjectService(
      SubjectRepository subjects, SubjectUnitRepository units, StudentProfileService students) {
    this.subjects = subjects;
    this.units = units;
    this.students = students;
  }

  /** The subjects the logged-in user may see, optionally for one semester. */
  public List<SubjectResponse> visibleTo(CurrentUser currentUser, Integer semester) {
    String branch =
        currentUser.role() == Role.STUDENT ? students.currentStudent(currentUser).branch() : null;
    return subjects.findActive(branch, semester).stream().map(SubjectService::toResponse).toList();
  }

  /** The numbered units of an active subject, in order. */
  public List<UnitResponse> unitsOf(Long subjectId) {
    subjects
        .findById(subjectId)
        .filter(Subject::isActive)
        .orElseThrow(ResourceNotFoundException::new);
    return units.findBySubjectIdOrderByUnitNo(subjectId).stream()
        .map(unit -> new UnitResponse(unit.getId(), unit.getUnitNo(), unit.getTitle()))
        .toList();
  }

  /** The active subjects a student of this branch and semester studies. */
  public List<SubjectResponse> subjectsOf(StudentContext student, int semester) {
    return subjects
        .findByBranchAndSemesterAndActiveTrueOrderByCode(student.branch(), semester)
        .stream()
        .map(SubjectService::toResponse)
        .toList();
  }

  /** True once at least one subject exists. */
  public boolean anySubjectExists() {
    return subjects.count() > 0;
  }

  public Optional<SubjectResponse> find(Long subjectId) {
    return subjects.findById(subjectId).map(SubjectService::toResponse);
  }

  /** Subjects by ID, for naming the subject of a record. Unknown IDs are simply absent. */
  public Map<Long, SubjectResponse> byIds(Collection<Long> subjectIds) {
    return subjects.findAllById(subjectIds).stream()
        .map(SubjectService::toResponse)
        .collect(Collectors.toMap(SubjectResponse::id, Function.identity()));
  }

  /** The unit with this ID, with the subject it belongs to. */
  public Optional<UnitOfSubject> findUnit(Long unitId) {
    return units
        .findById(unitId)
        .map(
            unit ->
                new UnitOfSubject(
                    unit.getId(), unit.getUnitNo(), unit.getTitle(), unit.getSubject().getId()));
  }

  /** Units by ID, for naming the unit of a record. */
  public Map<Long, UnitOfSubject> unitsByIds(Collection<Long> unitIds) {
    return units.findAllById(unitIds).stream()
        .map(
            unit ->
                new UnitOfSubject(
                    unit.getId(), unit.getUnitNo(), unit.getTitle(), unit.getSubject().getId()))
        .collect(Collectors.toMap(UnitOfSubject::id, Function.identity()));
  }

  /** A unit together with the ID of its subject. */
  public record UnitOfSubject(Long id, int unitNo, String title, Long subjectId) {}

  static SubjectResponse toResponse(Subject subject) {
    return new SubjectResponse(
        subject.getId(),
        subject.getCode(),
        subject.getName(),
        subject.getBranch(),
        subject.getSemester(),
        subject.getCredits());
  }
}
