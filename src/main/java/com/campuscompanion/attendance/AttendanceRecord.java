package com.campuscompanion.attendance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Whether one student was present in one class: a subject, a date and a period. */
@Entity
@Table(name = "attendance_records")
public class AttendanceRecord {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "student_id", nullable = false, updatable = false)
  private Long studentId;

  @Column(name = "subject_id", nullable = false, updatable = false)
  private Long subjectId;

  @Column(name = "class_date", nullable = false, updatable = false)
  private LocalDate classDate;

  @JdbcTypeCode(SqlTypes.SMALLINT)
  @Column(name = "period_no", nullable = false, updatable = false)
  private int periodNo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private AttendanceStatus status;

  /** The account of the admin who last saved this row. */
  @Column(name = "marked_by", nullable = false)
  private Long markedBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  /** Required by JPA. */
  protected AttendanceRecord() {}

  public AttendanceRecord(
      Long studentId,
      Long subjectId,
      LocalDate classDate,
      int periodNo,
      AttendanceStatus status,
      Long markedBy) {
    this.studentId = studentId;
    this.subjectId = subjectId;
    this.classDate = classDate;
    this.periodNo = periodNo;
    this.status = status;
    this.markedBy = markedBy;
  }

  /** Changes a saved status. Attendance is corrected, never deleted. */
  public void correct(AttendanceStatus newStatus, Long correctedBy) {
    status = newStatus;
    markedBy = correctedBy;
    updatedAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public Long getStudentId() {
    return studentId;
  }

  public Long getSubjectId() {
    return subjectId;
  }

  public LocalDate getClassDate() {
    return classDate;
  }

  public int getPeriodNo() {
    return periodNo;
  }

  public AttendanceStatus getStatus() {
    return status;
  }
}
