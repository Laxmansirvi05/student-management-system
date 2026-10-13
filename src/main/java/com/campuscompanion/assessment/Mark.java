package com.campuscompanion.assessment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** One student's result in one assessment. There are no marks when the student was absent. */
@Entity
@Table(name = "marks")
public class Mark {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "student_id", nullable = false, updatable = false)
  private Long studentId;

  @Column(name = "assessment_id", nullable = false, updatable = false)
  private Long assessmentId;

  @Column(name = "marks_obtained", precision = 5, scale = 2)
  private BigDecimal marksObtained;

  @Column(nullable = false)
  private boolean absent;

  /** The account of the admin who last saved this row. */
  @Column(name = "entered_by", nullable = false)
  private Long enteredBy;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  /** Required by JPA. */
  protected Mark() {}

  public Mark(
      Long studentId, Long assessmentId, BigDecimal marksObtained, boolean absent, Long enteredBy) {
    this.studentId = studentId;
    this.assessmentId = assessmentId;
    record(marksObtained, absent, enteredBy);
  }

  /** Stores the marks, or the absence. An absent student has no marks. */
  public void record(BigDecimal marksObtained, boolean absent, Long enteredBy) {
    this.marksObtained = absent ? null : marksObtained;
    this.absent = absent;
    this.enteredBy = enteredBy;
    this.updatedAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public Long getStudentId() {
    return studentId;
  }

  public Long getAssessmentId() {
    return assessmentId;
  }

  public BigDecimal getMarksObtained() {
    return marksObtained;
  }

  public boolean isAbsent() {
    return absent;
  }
}
