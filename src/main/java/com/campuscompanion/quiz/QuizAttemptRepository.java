package com.campuscompanion.quiz;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

  /**
   * A student's own attempt, locked until the transaction ends. If two submissions of the same test
   * arrive together, the second waits here and then finds the test already submitted. The student
   * ID is part of the query, so another student's attempt is simply not found.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from QuizAttempt a where a.id = :id and a.studentId = :studentId")
  Optional<QuizAttempt> findOwnForUpdate(@Param("id") Long id, @Param("studentId") Long studentId);

  Page<QuizAttempt> findByStudentId(Long studentId, Pageable pageable);

  /** Tests of a student that are still open although their time ended before the given moment. */
  List<QuizAttempt> findByStudentIdAndStatusAndExpiresAtBefore(
      Long studentId, AttemptStatus status, Instant moment);

  List<QuizAttempt> findByStudentIdAndStatus(Long studentId, AttemptStatus status);
}
