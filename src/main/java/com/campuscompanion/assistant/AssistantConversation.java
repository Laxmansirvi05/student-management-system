package com.campuscompanion.assistant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A chat with the assistant, owned by one student. */
@Entity
@Table(name = "assistant_conversations")
public class AssistantConversation {

  public static final String QUICK = "quick";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "student_id", nullable = false, updatable = false)
  private Long studentId;

  @Column(nullable = false, length = 60)
  private String title;

  @Column(nullable = false, length = 10, updatable = false)
  private String source;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Required by JPA. */
  protected AssistantConversation() {}

  public AssistantConversation(Long studentId, String title, String source, Instant now) {
    this.studentId = studentId;
    this.title = title;
    this.source = source;
    this.createdAt = now;
    this.updatedAt = now;
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

  public String getSource() {
    return source;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void touch(Instant now) {
    this.updatedAt = now;
  }
}
