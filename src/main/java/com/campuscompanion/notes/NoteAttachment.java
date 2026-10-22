package com.campuscompanion.notes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** An image or a file inside a note. The bytes are in storage; this row records them. */
@Entity
@Table(name = "note_attachments")
public class NoteAttachment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "student_id", nullable = false, updatable = false)
  private Long studentId;

  @Column(name = "note_id")
  private Long noteId;

  @Column(name = "storage_path", nullable = false, length = 300, updatable = false)
  private String storagePath;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(name = "mime_type", nullable = false, length = 100)
  private String mimeType;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  @Column(nullable = false, length = 5)
  private String kind;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  /** Required by JPA. */
  protected NoteAttachment() {}

  public NoteAttachment(
      Long studentId,
      Long noteId,
      String storagePath,
      String name,
      String mimeType,
      long sizeBytes,
      String kind) {
    this.studentId = studentId;
    this.noteId = noteId;
    this.storagePath = storagePath;
    this.name = name;
    this.mimeType = mimeType;
    this.sizeBytes = sizeBytes;
    this.kind = kind;
  }

  public Long getId() {
    return id;
  }

  public Long getStudentId() {
    return studentId;
  }

  public Long getNoteId() {
    return noteId;
  }

  public void setNoteId(Long noteId) {
    this.noteId = noteId;
  }

  public String getStoragePath() {
    return storagePath;
  }

  public String getName() {
    return name;
  }

  public String getMimeType() {
    return mimeType;
  }

  public long getSizeBytes() {
    return sizeBytes;
  }

  public String getKind() {
    return kind;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
