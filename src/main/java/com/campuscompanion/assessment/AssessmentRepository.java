package com.campuscompanion.assessment;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentRepository extends JpaRepository<Assessment, Long> {

  /** The published assessments of these subjects: the only ones a student may see. */
  List<Assessment> findBySubjectIdInAndPublishedTrueOrderBySubjectIdAscIdAsc(
      Collection<Long> subjectIds);

  List<Assessment> findBySubjectIdOrderByIdAsc(Long subjectId);

  List<Assessment> findAllByOrderBySubjectIdAscIdAsc();

  boolean existsBySubjectIdAndNameAndAcademicYear(Long subjectId, String name, String academicYear);

  boolean existsBySubjectIdAndNameAndAcademicYearAndIdNot(
      Long subjectId, String name, String academicYear, Long id);
}
