package com.campuscompanion.notes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** The link-sharing state of one note: an unguessable token and who may use it. */
@Entity
@Table(name = "note_shares")
public class NoteShare {

  public static final String PRIVATE = "private";
  public static final String VIEW = "view";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "note_id", nullable = false, updatable = false)
  private Long noteId;

  @Column(name = "owner_id", nullable = false, updatable = false)
  private Long ownerId;

  @Column(nullable = false, length = 64, updatable = false)
  private String slug;

  @Column(name = "link_access", nullable = false, length = 10)
  private String linkAccess = PRIVATE;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  /** Required by JPA. */
  protected NoteShare() {}

  public NoteShare(Long noteId, Long ownerId, String slug, String linkAccess) {
    this.noteId = noteId;
    this.ownerId = ownerId;
    this.slug = slug;
    this.linkAccess = linkAccess;
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

  public String getSlug() {
    return slug;
  }

  public String getLinkAccess() {
    return linkAccess;
  }

  public void setLinkAccess(String linkAccess, Instant now) {
    this.linkAccess = linkAccess;
    this.updatedAt = now;
  }
}
