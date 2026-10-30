package com.campuscompanion.coding;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A student who chose to appear on the students' leaderboard (FR-COD-004). */
@Entity
@Table(name = "coding_leaderboard_optins")
public class LeaderboardOptin {

  @Id
  @Column(name = "student_id")
  private Long studentId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  /** Required by JPA. */
  protected LeaderboardOptin() {}

  public LeaderboardOptin(Long studentId) {
    this.studentId = studentId;
  }

  public Long getStudentId() {
    return studentId;
  }
}
