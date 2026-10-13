package com.campuscompanion.attendance;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

  /** How many classes of one subject were held for a student, and how many they attended. */
  interface SubjectCount {
    Long getSubjectId();

    long getHeld();

    long getAttended();
  }

  /** One row per subject, counted in the database. The student ID always comes from the session. */
  @Query(
      """
      select r.subjectId as subjectId,
             count(r) as held,
             sum(case when r.status = com.campuscompanion.attendance.AttendanceStatus.PRESENT
                      then 1 else 0 end) as attended
      from AttendanceRecord r
      where r.studentId = :studentId
      group by r.subjectId
      """)
  List<SubjectCount> countBySubject(@Param("studentId") Long studentId);

  /** One student's history in one subject, newest first. */
  Page<AttendanceRecord> findByStudentIdAndSubjectIdOrderByClassDateDescPeriodNoDesc(
      Long studentId, Long subjectId, Pageable pageable);

  /** The saved rows of one class, for the admin's attendance sheet. */
  List<AttendanceRecord> findBySubjectIdAndClassDateAndPeriodNoAndStudentIdIn(
      Long subjectId, LocalDate classDate, int periodNo, Collection<Long> studentIds);
}
