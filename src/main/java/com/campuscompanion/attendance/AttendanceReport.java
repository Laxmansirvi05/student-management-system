package com.campuscompanion.attendance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * The college's attendance total for one student over a period: classes held and classes attended.
 * The percentage is not stored; {@link AttendanceCalculator} works it out.
 */
@Entity
@Table(name = "attendance_reports")
public class AttendanceReport {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "student_id", nullable = false, updatable = false)
  private Long studentId;

  @Column(name = "from_date", nullable = false, updatable = false)
  private LocalDate fromDate;

  @Column(name = "to_date", nullable = false, updatable = false)
  private LocalDate toDate;

  @Column(name = "classes_held", nullable = false)
  private int classesHeld;

  @Column(name = "classes_attended", nullable = false)
  private int classesAttended;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  /** Required by JPA. */
  protected AttendanceReport() {}

  public AttendanceReport(
      Long studentId, LocalDate fromDate, LocalDate toDate, int classesHeld, int classesAttended) {
    this.studentId = studentId;
    this.fromDate = fromDate;
    this.toDate = toDate;
    this.classesHeld = classesHeld;
    this.classesAttended = classesAttended;
  }

  /** A corrected report for the same period replaces the numbers. */
  public void correct(int classesHeld, int classesAttended) {
    this.classesHeld = classesHeld;
    this.classesAttended = classesAttended;
    this.updatedAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public Long getStudentId() {
    return studentId;
  }

  public LocalDate getFromDate() {
    return fromDate;
  }

  public LocalDate getToDate() {
    return toDate;
  }

  public int getClassesHeld() {
    return classesHeld;
  }

  public int getClassesAttended() {
    return classesAttended;
  }
}
