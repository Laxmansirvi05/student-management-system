package com.campuscompanion.complaint;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A complaint. It has no student: who raised it is kept in {@link ComplaintOwner}, so a query on
 * complaints alone can never reveal the author of a secure complaint.
 */
@Entity
@Table(name = "complaints")
public class Complaint {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10, updatable = false)
  private ComplaintType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ComplaintCategory category;

  @Column(nullable = false, length = 120)
  private String title;

  @Column(nullable = false, length = 2000)
  private String description;

  @Column(name = "show_identity", nullable = false, updatable = false)
  private boolean showIdentity;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private ComplaintStatus status = ComplaintStatus.OPEN;

  @Column(name = "admin_response", length = 2000)
  private String adminResponse;

  @Column(name = "responded_at")
  private Instant respondedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  /** Required by JPA. */
  protected Complaint() {}

  public Complaint(
      ComplaintType type,
      ComplaintCategory category,
      String title,
      String description,
      boolean showIdentity) {
    this.type = type;
    this.category = category;
    this.title = title;
    this.description = description;
    // Only a public complaint can show its author; for the others the choice means nothing.
    this.showIdentity = type == ComplaintType.PUBLIC && showIdentity;
  }

  /** The administrator's handling: a status and, optionally, a written response. */
  public void handle(ComplaintStatus newStatus, String response, Instant now) {
    this.status = newStatus;
    if (response != null) {
      this.adminResponse = response;
      this.respondedAt = now;
    }
    this.updatedAt = now;
  }

  /** Whether other students can see this complaint on the public board. */
  public boolean isOnPublicBoard() {
    return type == ComplaintType.PUBLIC && status != ComplaintStatus.REJECTED;
  }

  public Long getId() {
    return id;
  }

  public ComplaintType getType() {
    return type;
  }

  public ComplaintCategory getCategory() {
    return category;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public boolean isShowIdentity() {
    return showIdentity;
  }

  public ComplaintStatus getStatus() {
    return status;
  }

  public String getAdminResponse() {
    return adminResponse;
  }

  public Instant getRespondedAt() {
    return respondedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
