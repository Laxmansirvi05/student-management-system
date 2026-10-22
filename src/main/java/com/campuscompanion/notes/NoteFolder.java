package com.campuscompanion.notes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A folder of notes. Folders can sit inside other folders, and have a colour and an icon. */
@Entity
@Table(name = "note_folders")
public class NoteFolder {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "student_id", nullable = false, updatable = false)
  private Long studentId;

  @Column(nullable = false, length = 60)
  private String name;

  @Column(nullable = false, length = 10)
  private String color = "blue";

  @Column(length = 40)
  private String icon;

  @Column(name = "parent_id")
  private Long parentId;

  @Column(nullable = false)
  private int position;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  /** Required by JPA. */
  protected NoteFolder() {}

  public NoteFolder(
      Long studentId, String name, String color, String icon, Long parentId, int position) {
    this.studentId = studentId;
    this.name = name;
    this.color = color;
    this.icon = icon;
    this.parentId = parentId;
    this.position = position;
  }

  public Long getId() {
    return id;
  }

  public Long getStudentId() {
    return studentId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getColor() {
    return color;
  }

  public void setColor(String color) {
    this.color = color;
  }

  public String getIcon() {
    return icon;
  }

  public void setIcon(String icon) {
    this.icon = icon;
  }

  public Long getParentId() {
    return parentId;
  }

  public void setParentId(Long parentId) {
    this.parentId = parentId;
  }

  public int getPosition() {
    return position;
  }

  public void setPosition(int position) {
    this.position = position;
  }

  public void touch(Instant now) {
    this.updatedAt = now;
  }
}
