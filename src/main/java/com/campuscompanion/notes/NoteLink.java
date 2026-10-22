package com.campuscompanion.notes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A reference from a note to another note, a folder or a learning resource. */
@Entity
@Table(name = "note_links")
public class NoteLink {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "student_id", nullable = false, updatable = false)
  private Long studentId;

  @Column(name = "source_note_id", nullable = false, updatable = false)
  private Long sourceNoteId;

  @Column(name = "target_type", nullable = false, length = 10, updatable = false)
  private String targetType;

  @Column(name = "target_id", nullable = false, updatable = false)
  private Long targetId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  /** Required by JPA. */
  protected NoteLink() {}

  public NoteLink(Long studentId, Long sourceNoteId, String targetType, Long targetId) {
    this.studentId = studentId;
    this.sourceNoteId = sourceNoteId;
    this.targetType = targetType;
    this.targetId = targetId;
  }

  public Long getId() {
    return id;
  }

  public Long getStudentId() {
    return studentId;
  }

  public Long getSourceNoteId() {
    return sourceNoteId;
  }

  public String getTargetType() {
    return targetType;
  }

  public Long getTargetId() {
    return targetId;
  }
}
