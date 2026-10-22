package com.campuscompanion.complaint;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Who raised a complaint. Read by the student's own pages and, for public and private complaints,
 * by the administrator's service. The administrator's service never reads it for a secure complaint
 * (ADR 0013).
 */
@Entity
@Table(name = "complaint_owners")
public class ComplaintOwner {

  @Id
  @Column(name = "complaint_id")
  private Long complaintId;

  @Column(name = "student_id", nullable = false, updatable = false)
  private Long studentId;

  /** Required by JPA. */
  protected ComplaintOwner() {}

  public ComplaintOwner(Long complaintId, Long studentId) {
    this.complaintId = complaintId;
    this.studentId = studentId;
  }

  public Long getComplaintId() {
    return complaintId;
  }

  public Long getStudentId() {
    return studentId;
  }
}
