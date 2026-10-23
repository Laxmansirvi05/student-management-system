package com.campuscompanion.planner;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/** An exam date a student added for their own countdown (FR-PLN-002). Private to that student. */
@Entity
@Table(name = "student_exams")
public class StudentExam {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "student_id", nullable = false, updatable = false)
  private Long studentId;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(name = "subject_id")
  private Long subjectId;

  @Column(name = "exam_date", nullable = false)
  private LocalDate examDate;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  /** Required by JPA. */
  protected StudentExam() {}

  public StudentExam(
      Long studentId, String name, Long subjectId, LocalDate examDate, Instant createdAt) {
    this.studentId = studentId;
    this.createdAt = createdAt;
    change(name, subjectId, examDate);
  }

  public void change(String name, Long subjectId, LocalDate examDate) {
    this.name = name;
    this.subjectId = subjectId;
    this.examDate = examDate;
  }

  public Long getId() {
    return id;
  }

  public Long getStudentId() {
    return studentId;
  }

  public String getName() {
    return name;
  }

  public Long getSubjectId() {
    return subjectId;
  }

  public LocalDate getExamDate() {
    return examDate;
  }
}
