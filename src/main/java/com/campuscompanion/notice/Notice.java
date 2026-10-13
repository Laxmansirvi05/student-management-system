package com.campuscompanion.notice;

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

/**
 * An announcement. It starts as a draft that only admins see, and reaches students when it is
 * published. A null target means "all branches" or "all semesters". A due date makes it a deadline.
 */
@Entity
@Table(name = "notices")
public class Notice {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, columnDefinition = "text")
  private String body;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private NoticeCategory category;

  @Column(name = "target_branch", length = 20)
  private String targetBranch;

  @Column(name = "target_semester")
  private Short targetSemester;

  @Column(name = "due_date")
  private LocalDate dueDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private NoticeStatus status = NoticeStatus.DRAFT;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "created_by", nullable = false, updatable = false)
  private Long createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  /** Required by JPA. */
  protected Notice() {}

  public Notice(
      String title,
      String body,
      NoticeCategory category,
      String targetBranch,
      Integer targetSemester,
      LocalDate dueDate,
      Long createdBy) {
    this.createdBy = createdBy;
    update(title, body, category, targetBranch, targetSemester, dueDate);
  }

  public void update(
      String title,
      String body,
      NoticeCategory category,
      String targetBranch,
      Integer targetSemester,
      LocalDate dueDate) {
    this.title = title;
    this.body = body;
    this.category = category;
    this.targetBranch = targetBranch;
    this.targetSemester = targetSemester == null ? null : targetSemester.shortValue();
    this.dueDate = dueDate;
    this.updatedAt = Instant.now();
  }

  public void publish(Instant now) {
    status = NoticeStatus.PUBLISHED;
    publishedAt = now;
    updatedAt = now;
  }

  /** Returns the notice to a draft. Students stop seeing it at once. */
  public void unpublish(Instant now) {
    status = NoticeStatus.DRAFT;
    publishedAt = null;
    updatedAt = now;
  }

  public Long getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public String getBody() {
    return body;
  }

  public NoticeCategory getCategory() {
    return category;
  }

  public String getTargetBranch() {
    return targetBranch;
  }

  public Integer getTargetSemester() {
    return targetSemester == null ? null : targetSemester.intValue();
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public NoticeStatus getStatus() {
    return status;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }
}
