package com.campuscompanion.quiz;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One test taken by one student. The server sets when it ends at the moment it starts, so the time
 * limit does not depend on anything the browser says.
 */
@Entity
@Table(name = "quiz_attempts")
public class QuizAttempt {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "student_id", nullable = false, updatable = false)
  private Long studentId;

  @Column(name = "subject_id", nullable = false, updatable = false)
  private Long subjectId;

  @Column(name = "unit_id", updatable = false)
  private Long unitId;

  @JdbcTypeCode(SqlTypes.SMALLINT)
  @Column(name = "question_count", nullable = false, updatable = false)
  private int questionCount;

  @Column(name = "started_at", nullable = false, updatable = false)
  private Instant startedAt;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 15)
  private AttemptStatus status = AttemptStatus.IN_PROGRESS;

  /** The number of correct answers. Null while the test is running. */
  @Column private Short score;

  /** Required by JPA. */
  protected QuizAttempt() {}

  public QuizAttempt(
      Long studentId,
      Long subjectId,
      Long unitId,
      int questionCount,
      Instant startedAt,
      Duration timeAllowed) {
    this.studentId = studentId;
    this.subjectId = subjectId;
    this.unitId = unitId;
    this.questionCount = questionCount;
    this.startedAt = startedAt;
    this.expiresAt = startedAt.plus(timeAllowed);
  }

  public boolean isInProgress() {
    return status == AttemptStatus.IN_PROGRESS;
  }

  /** True once the time limit and the grace period have both passed. */
  public boolean isOverdue(Instant now, Duration grace) {
    return now.isAfter(expiresAt.plus(grace));
  }

  public void submit(Instant at, int score) {
    this.status = AttemptStatus.SUBMITTED;
    this.submittedAt = at;
    this.score = (short) score;
  }

  /** Closes a test that was not submitted in time. Nothing was answered, so the score is zero. */
  public void expire() {
    this.status = AttemptStatus.EXPIRED;
    this.score = 0;
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

  public Long getUnitId() {
    return unitId;
  }

  public int getQuestionCount() {
    return questionCount;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getSubmittedAt() {
    return submittedAt;
  }

  public AttemptStatus getStatus() {
    return status;
  }

  public Integer getScore() {
    return score == null ? null : score.intValue();
  }
}
