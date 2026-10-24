package com.campuscompanion.resource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A student's report of a problem with a resource (FR-LIB-005). */
@Entity
@Table(name = "resource_reports")
public class ResourceReport {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "resource_id", nullable = false, updatable = false)
  private Long resourceId;

  @Column(name = "student_id", nullable = false, updatable = false)
  private Long studentId;

  @Column(nullable = false, length = 500, updatable = false)
  private String reason;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private ResourceReportStatus status = ResourceReportStatus.OPEN;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  /** Required by JPA. */
  protected ResourceReport() {}

  public ResourceReport(Long resourceId, Long studentId, String reason, Instant createdAt) {
    this.resourceId = resourceId;
    this.studentId = studentId;
    this.reason = reason;
    this.createdAt = createdAt;
  }

  /** Marks the report handled. Resolving twice keeps the first time. */
  public void resolve(Instant now) {
    if (status != ResourceReportStatus.RESOLVED) {
      status = ResourceReportStatus.RESOLVED;
      resolvedAt = now;
    }
  }

  public Long getId() {
    return id;
  }

  public Long getResourceId() {
    return resourceId;
  }

  public Long getStudentId() {
    return studentId;
  }

  public String getReason() {
    return reason;
  }

  public ResourceReportStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getResolvedAt() {
    return resolvedAt;
  }
}
