package com.campuscompanion.resource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** One resource saved by one student. A student can save a resource only once. */
@Entity
@Table(name = "bookmarks")
@IdClass(Bookmark.Key.class)
public class Bookmark {

  @Id
  @Column(name = "student_id")
  private Long studentId;

  @Id
  @Column(name = "resource_id")
  private Long resourceId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  /** Required by JPA. */
  protected Bookmark() {}

  public Bookmark(Long studentId, Long resourceId) {
    this.studentId = studentId;
    this.resourceId = resourceId;
  }

  public Long getResourceId() {
    return resourceId;
  }

  /** The two columns that together identify a bookmark. */
  public static class Key implements Serializable {

    private Long studentId;
    private Long resourceId;

    public Key() {}

    public Key(Long studentId, Long resourceId) {
      this.studentId = studentId;
      this.resourceId = resourceId;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Key key
          && Objects.equals(studentId, key.studentId)
          && Objects.equals(resourceId, key.resourceId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(studentId, resourceId);
    }
  }
}
