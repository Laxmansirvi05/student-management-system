package com.campuscompanion.planner;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyTaskRepository extends JpaRepository<StudyTask, Long> {

  /** Every query is by the student, so another student's task is simply not found. */
  Optional<StudyTask> findByIdAndStudentId(Long id, Long studentId);

  List<StudyTask> findByStudentId(Long studentId);
}
