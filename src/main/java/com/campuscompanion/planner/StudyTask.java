package com.campuscompanion.planner;

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

/** A study task of one student (FR-PLN-001). Private to that student. */
@Entity
@Table(name = "study_tasks")
public class StudyTask {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "student_id", nullable = false, updatable = false)
  private Long studentId;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(length = 1000)
  private String notes;

  @Column(name = "subject_id")
  private Long subjectId;

  @Column(name = "due_date")
  private LocalDate dueDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private TaskStatus status = TaskStatus.PENDING;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  /** Required by JPA. */
  protected StudyTask() {}

  public StudyTask(
      Long studentId,
      String title,
      String notes,
      Long subjectId,
      LocalDate dueDate,
      Instant createdAt) {
    this.studentId = studentId;
    this.createdAt = createdAt;
    change(title, notes, subjectId, dueDate);
  }

  public void change(String title, String notes, Long subjectId, LocalDate dueDate) {
    this.title = title;
    this.notes = notes;
    this.subjectId = subjectId;
    this.dueDate = dueDate;
  }

  /** Marks the task done. Doing it twice keeps the first completion time. */
  public void complete(Instant now) {
    if (status != TaskStatus.DONE) {
      status = TaskStatus.DONE;
      completedAt = now;
    }
  }

  /** Back to pending. */
  public void reopen() {
    status = TaskStatus.PENDING;
    completedAt = null;
  }

  public Long getId() {
    return id;
  }

  public Long getStudentId() {
    return studentId;
  }

  public String getTitle() {
    return title;
  }

  public String getNotes() {
    return notes;
  }

  public Long getSubjectId() {
    return subjectId;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public TaskStatus getStatus() {
    return status;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
