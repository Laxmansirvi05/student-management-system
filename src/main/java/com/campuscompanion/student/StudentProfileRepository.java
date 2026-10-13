package com.campuscompanion.student;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {

  /** Finds the profile that belongs to a login account. */
  Optional<StudentProfile> findByUserId(Long userId);

  /** The roll number must already be in upper case. */
  boolean existsByRollNo(String rollNo);
}
