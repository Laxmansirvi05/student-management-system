package com.campuscompanion.planner;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentExamRepository extends JpaRepository<StudentExam, Long> {

  /** Every query is by the student, so another student's exam is simply not found. */
  Optional<StudentExam> findByIdAndStudentId(Long id, Long studentId);

  List<StudentExam> findByStudentIdOrderByExamDateAscIdAsc(Long studentId);
}
