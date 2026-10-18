package com.campuscompanion.assessment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One attempt at one subject, as the college published it: the grade, the credits and the
 * examination it was in. The attempt with the highest number is the subject's current grade.
 */
@Entity
@Table(name = "subject_results")
public class SubjectResult {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "student_id", nullable = false, updatable = false)
  private Long studentId;

  @JdbcTypeCode(SqlTypes.SMALLINT)
  @Column(nullable = false, updatable = false)
  private int semester;

  @Column(name = "subject_code", nullable = false, updatable = false, length = 20)
  private String subjectCode;

  @Column(name = "subject_name", nullable = false, length = 80)
  private String subjectName;

  /** Null for a course without credits. */
  private BigDecimal credits;

  @Column(nullable = false, length = 2)
  private String grade;

  @JdbcTypeCode(SqlTypes.SMALLINT)
  @Column(nullable = false, updatable = false)
  private int attempt;

  @Column(nullable = false, length = 100)
  private String exam;

  /** Required by JPA. */
  protected SubjectResult() {}

  public Long getStudentId() {
    return studentId;
  }

  public int getSemester() {
    return semester;
  }

  public String getSubjectCode() {
    return subjectCode;
  }

  public String getSubjectName() {
    return subjectName;
  }

  public BigDecimal getCredits() {
    return credits;
  }

  public String getGrade() {
    return grade;
  }

  public int getAttempt() {
    return attempt;
  }

  public String getExam() {
    return exam;
  }
}
