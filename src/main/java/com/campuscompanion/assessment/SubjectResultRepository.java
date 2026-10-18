package com.campuscompanion.assessment;

import java.util.List;
import org.springframework.data.repository.Repository;

/** Reading subject grades. Rows are written by the results import, not through this interface. */
public interface SubjectResultRepository extends Repository<SubjectResult, Long> {

  List<SubjectResult> findByStudentIdOrderBySemesterAscSubjectCodeAscAttemptAsc(Long studentId);
}
