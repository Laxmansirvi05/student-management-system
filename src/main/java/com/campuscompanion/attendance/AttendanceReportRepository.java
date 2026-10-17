package com.campuscompanion.attendance;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceReportRepository extends JpaRepository<AttendanceReport, Long> {

  /** The most recent report of a student: the one whose period ends last. */
  Optional<AttendanceReport> findFirstByStudentIdOrderByToDateDescFromDateDesc(Long studentId);

  Optional<AttendanceReport> findByStudentIdAndFromDateAndToDate(
      Long studentId, LocalDate fromDate, LocalDate toDate);
}
