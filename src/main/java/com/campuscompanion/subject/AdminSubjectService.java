package com.campuscompanion.subject;

import com.campuscompanion.common.error.ConflictException;
import com.campuscompanion.common.error.ResourceNotFoundException;
import com.campuscompanion.subject.dto.AdminSubjectResponse;
import com.campuscompanion.subject.dto.SubjectWriteRequest;
import com.campuscompanion.subject.dto.UnitResponse;
import com.campuscompanion.subject.dto.UnitWriteRequest;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Managing subjects and their units (FR-ADM-005). A subject is never deleted: it is switched to
 * inactive, so the records that point at it stay valid.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminSubjectService {

  private final SubjectRepository subjects;
  private final SubjectUnitRepository units;

  public AdminSubjectService(SubjectRepository subjects, SubjectUnitRepository units) {
    this.subjects = subjects;
    this.units = units;
  }

  @Transactional(readOnly = true)
  public List<AdminSubjectResponse> list(String branch, Integer semester) {
    return subjects.findAllFiltered(branch, semester).stream()
        .map(AdminSubjectService::toResponse)
        .toList();
  }

  @Transactional
  public AdminSubjectResponse create(SubjectWriteRequest request) {
    String code = normalise(request.code());
    if (subjects.findByCode(code).isPresent()) {
      throw new ConflictException("A subject with this code already exists.");
    }
    String name = request.name().strip();
    String branch = normalise(request.branch());
    Subject subject = new Subject(code, name, branch, request.semester(), request.credits());
    subject.update(code, name, branch, request.semester(), request.credits(), request.active());
    return toResponse(subjects.save(subject));
  }

  @Transactional
  public AdminSubjectResponse update(Long id, SubjectWriteRequest request) {
    Subject subject = subjects.findById(id).orElseThrow(ResourceNotFoundException::new);
    String code = normalise(request.code());
    if (subjects.existsByCodeAndIdNot(code, id)) {
      throw new ConflictException("A subject with this code already exists.");
    }
    subject.update(
        code,
        request.name().strip(),
        normalise(request.branch()),
        request.semester(),
        request.credits(),
        request.active());
    return toResponse(subject);
  }

  @Transactional
  public UnitResponse addUnit(Long subjectId, UnitWriteRequest request) {
    Subject subject = subjects.findById(subjectId).orElseThrow(ResourceNotFoundException::new);
    if (units.existsBySubjectIdAndUnitNo(subjectId, request.unitNo())) {
      throw new ConflictException("This subject already has a unit with that number.");
    }
    SubjectUnit unit =
        units.save(new SubjectUnit(subject, request.unitNo(), request.title().strip()));
    return new UnitResponse(unit.getId(), unit.getUnitNo(), unit.getTitle());
  }

  @Transactional
  public UnitResponse updateUnit(Long unitId, UnitWriteRequest request) {
    SubjectUnit unit = units.findById(unitId).orElseThrow(ResourceNotFoundException::new);
    Long subjectId = unit.getSubject().getId();
    if (units.existsBySubjectIdAndUnitNoAndIdNot(subjectId, request.unitNo(), unitId)) {
      throw new ConflictException("This subject already has a unit with that number.");
    }
    unit.update(request.unitNo(), request.title().strip());
    return new UnitResponse(unit.getId(), unit.getUnitNo(), unit.getTitle());
  }

  /** Removes a unit. A unit that resources or questions point at cannot be removed. */
  @Transactional
  public void deleteUnit(Long unitId) {
    SubjectUnit unit = units.findById(unitId).orElseThrow(ResourceNotFoundException::new);
    try {
      units.delete(unit);
      units.flush();
    } catch (DataIntegrityViolationException inUse) {
      throw new ConflictException("This unit is in use and cannot be removed.");
    }
  }

  /** Codes and branches are stored trimmed and in upper case, so "cs301" and "CS301" are one. */
  private static String normalise(String text) {
    return text.strip().toUpperCase(Locale.ROOT);
  }

  static AdminSubjectResponse toResponse(Subject subject) {
    return new AdminSubjectResponse(
        subject.getId(),
        subject.getCode(),
        subject.getName(),
        subject.getBranch(),
        subject.getSemester(),
        subject.getCredits(),
        subject.isActive());
  }
}
