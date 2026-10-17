package com.campuscompanion.student;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Looks up the roll number of students by ID, for screens that show who a record belongs to. */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class StudentDirectory {

  private final StudentProfileRepository profiles;

  public StudentDirectory(StudentProfileRepository profiles) {
    this.profiles = profiles;
  }

  /** Roll number to student ID, for the roll numbers that exist. Roll numbers are upper case. */
  @Transactional(readOnly = true)
  public Map<String, Long> idsByRollNumber(Collection<String> rollNumbers) {
    Map<String, Long> ids = new HashMap<>();
    profiles
        .findByRollNoIn(rollNumbers)
        .forEach(profile -> ids.put(profile.getRollNo(), profile.getId()));
    return ids;
  }

  /** Student ID to roll number, for the IDs that exist. */
  @Transactional(readOnly = true)
  public Map<Long, String> rollNumbersOf(Collection<Long> studentIds) {
    Map<Long, String> rollNumbers = new HashMap<>();
    profiles
        .findAllById(studentIds)
        .forEach(profile -> rollNumbers.put(profile.getId(), profile.getRollNo()));
    return rollNumbers;
  }
}
