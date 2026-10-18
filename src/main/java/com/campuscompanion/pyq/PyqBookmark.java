package com.campuscompanion.pyq;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** One paper saved by one student. A student can save a paper only once. */
@Entity
@Table(name = "pyq_bookmarks")
@IdClass(PyqBookmark.Key.class)
public class PyqBookmark {

  @Id
  @Column(name = "student_id")
  private Long studentId;

  @Id
  @Column(name = "paper_id")
  private Long paperId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  /** Required by JPA. */
  protected PyqBookmark() {}

  public Long getStudentId() {
    return studentId;
  }

  public Long getPaperId() {
    return paperId;
  }

  /** The two IDs that make a bookmark unique. */
  public static class Key implements Serializable {

    private Long studentId;
    private Long paperId;

    protected Key() {}

    public Key(Long studentId, Long paperId) {
      this.studentId = studentId;
      this.paperId = paperId;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Key key
          && Objects.equals(studentId, key.studentId)
          && Objects.equals(paperId, key.paperId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(studentId, paperId);
    }
  }
}
