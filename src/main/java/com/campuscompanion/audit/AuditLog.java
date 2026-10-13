package com.campuscompanion.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One line of the audit log: who changed what, when, and from where. Rows are only ever added. This
 * class has no setters, and the database refuses updates and deletes on the table.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Null only for actions taken by the system itself, such as creating the first admin. */
  @Column(name = "actor_user_id", updatable = false)
  private Long actorUserId;

  @Column(nullable = false, length = 50, updatable = false)
  private String action;

  @Column(name = "entity_type", nullable = false, length = 50, updatable = false)
  private String entityType;

  @Column(name = "entity_id", nullable = false, length = 50, updatable = false)
  private String entityId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "old_value", updatable = false)
  private String oldValue;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "new_value", updatable = false)
  private String newValue;

  @Column(name = "ip_address", length = 45, updatable = false)
  private String ipAddress;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  /** Required by JPA. */
  protected AuditLog() {}

  AuditLog(
      Long actorUserId,
      String action,
      String entityType,
      String entityId,
      String oldValue,
      String newValue,
      String ipAddress) {
    this.actorUserId = actorUserId;
    this.action = action;
    this.entityType = entityType;
    this.entityId = entityId;
    this.oldValue = oldValue;
    this.newValue = newValue;
    this.ipAddress = ipAddress;
  }

  public Long getId() {
    return id;
  }

  public Long getActorUserId() {
    return actorUserId;
  }

  public String getAction() {
    return action;
  }

  public String getEntityType() {
    return entityType;
  }

  public String getEntityId() {
    return entityId;
  }

  public String getOldValue() {
    return oldValue;
  }

  public String getNewValue() {
    return newValue;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
