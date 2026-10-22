package com.campuscompanion.complaint;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** One student's support for one public complaint. A student can support a complaint only once. */
@Entity
@Table(name = "complaint_supports")
@IdClass(ComplaintSupport.Key.class)
public class ComplaintSupport {

  @Id
  @Column(name = "complaint_id")
  private Long complaintId;

  @Id
  @Column(name = "student_id")
  private Long studentId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  /** Required by JPA. */
  protected ComplaintSupport() {}

  public Long getComplaintId() {
    return complaintId;
  }

  public Long getStudentId() {
    return studentId;
  }

  /** The two IDs that make a support unique. */
  public static class Key implements Serializable {
    private Long complaintId;
    private Long studentId;

    public Key() {}

    public Key(Long complaintId, Long studentId) {
      this.complaintId = complaintId;
      this.studentId = studentId;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Key key
          && Objects.equals(complaintId, key.complaintId)
          && Objects.equals(studentId, key.studentId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(complaintId, studentId);
    }
  }
}
