package com.campuscompanion.subject;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectUnitRepository extends JpaRepository<SubjectUnit, Long> {

  List<SubjectUnit> findBySubjectIdOrderByUnitNo(Long subjectId);

  boolean existsBySubjectIdAndUnitNo(Long subjectId, int unitNo);

  boolean existsBySubjectIdAndUnitNoAndIdNot(Long subjectId, int unitNo, Long id);
}
