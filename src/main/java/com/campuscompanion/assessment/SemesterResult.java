package com.campuscompanion.assessment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The official result of one semester for one student. CGPA is not stored anywhere: it is worked
 * out from these rows by {@link CgpaCalculator} each time it is needed.
 */
@Entity
@Table(name = "semester_results")
public class SemesterResult {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "student_id", nullable = false, updatable = false)
  private Long studentId;

  @JdbcTypeCode(SqlTypes.SMALLINT)
  @Column(nullable = false, updatable = false)
  private int semester;

  @Column(nullable = false, precision = 4, scale = 2)
  private BigDecimal sgpa;

  @JdbcTypeCode(SqlTypes.SMALLINT)
  @Column(name = "credits_registered", nullable = false)
  private int creditsRegistered;

  @JdbcTypeCode(SqlTypes.SMALLINT)
  @Column(name = "credits_earned", nullable = false)
  private int creditsEarned;

  @Enumerated(EnumType.STRING)
  @Column(name = "result_status", nullable = false, length = 10)
  private ResultStatus resultStatus;

  /** Set when the result is published. The student sees the result only when this is set. */
  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  /** Required by JPA. */
  protected SemesterResult() {}

  public SemesterResult(Long studentId, int semester) {
    this.studentId = studentId;
    this.semester = semester;
  }

  /** Stores the official values. Publishing keeps the first publication time. */
  public void record(
      BigDecimal sgpa,
      int creditsRegistered,
      int creditsEarned,
      ResultStatus resultStatus,
      boolean published,
      Instant now) {
    this.sgpa = sgpa;
    this.creditsRegistered = creditsRegistered;
    this.creditsEarned = creditsEarned;
    this.resultStatus = resultStatus;
    if (!published) {
      this.publishedAt = null;
    } else if (this.publishedAt == null) {
      this.publishedAt = now;
    }
    this.updatedAt = now;
  }

  public boolean isPublished() {
    return publishedAt != null;
  }

  public Long getId() {
    return id;
  }

  public Long getStudentId() {
    return studentId;
  }

  public int getSemester() {
    return semester;
  }

  public BigDecimal getSgpa() {
    return sgpa;
  }

  public int getCreditsRegistered() {
    return creditsRegistered;
  }

  public int getCreditsEarned() {
    return creditsEarned;
  }

  public ResultStatus getResultStatus() {
    return resultStatus;
  }
}
