package com.campuscompanion.assistant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One message of a chat: what the student wrote ("user") or what the assistant answered ("ai"). */
@Entity
@Table(name = "assistant_messages")
public class AssistantMessage {

  public static final String USER = "user";
  public static final String AI = "ai";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "conversation_id", nullable = false, updatable = false)
  private Long conversationId;

  @Column(nullable = false, length = 4, updatable = false)
  private String role;

  @Column(nullable = false, columnDefinition = "text", updatable = false)
  private String content;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  /** Required by JPA. */
  protected AssistantMessage() {}

  public AssistantMessage(Long conversationId, String role, String content, Instant now) {
    this.conversationId = conversationId;
    this.role = role;
    this.content = content;
    this.createdAt = now;
  }

  public Long getId() {
    return id;
  }

  public Long getConversationId() {
    return conversationId;
  }

  public String getRole() {
    return role;
  }

  public String getContent() {
    return content;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
