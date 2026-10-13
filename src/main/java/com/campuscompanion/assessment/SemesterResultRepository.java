package com.campuscompanion.assessment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SemesterResultRepository extends JpaRepository<SemesterResult, Long> {

  List<SemesterResult> findByStudentIdOrderBySemesterAsc(Long studentId);

  Optional<SemesterResult> findByStudentIdAndSemester(Long studentId, int semester);
}
