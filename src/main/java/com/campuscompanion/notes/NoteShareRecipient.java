package com.campuscompanion.notes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A classmate a note is shared with, and whether they may only read it or also edit it. */
@Entity
@Table(name = "note_share_recipients")
public class NoteShareRecipient {

  public static final String VIEW = "view";
  public static final String EDIT = "edit";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "note_id", nullable = false, updatable = false)
  private Long noteId;

  @Column(name = "owner_id", nullable = false, updatable = false)
  private Long ownerId;

  @Column(name = "recipient_id", nullable = false, updatable = false)
  private Long recipientId;

  @Column(nullable = false, length = 10)
  private String permission = VIEW;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  /** Required by JPA. */
  protected NoteShareRecipient() {}

  public NoteShareRecipient(Long noteId, Long ownerId, Long recipientId, String permission) {
    this.noteId = noteId;
    this.ownerId = ownerId;
    this.recipientId = recipientId;
    this.permission = permission;
  }

  public Long getId() {
    return id;
  }

  public Long getNoteId() {
    return noteId;
  }

  public Long getOwnerId() {
    return ownerId;
  }

  public Long getRecipientId() {
    return recipientId;
  }

  public String getPermission() {
    return permission;
  }

  public void setPermission(String permission) {
    this.permission = permission;
  }
}
