package com.campuscompanion.coding;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/** The numbers of one account on one day: one point of the student's journey (FR-COD-003). */
@Entity
@Table(name = "coding_snapshots")
@IdClass(CodingSnapshot.Key.class)
public class CodingSnapshot {

  /** The primary key: one row per account per day. */
  public static class Key implements Serializable {
    private Long accountId;
    private LocalDate capturedOn;

    public Key() {}

    public Key(Long accountId, LocalDate capturedOn) {
      this.accountId = accountId;
      this.capturedOn = capturedOn;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Key key
          && Objects.equals(accountId, key.accountId)
          && Objects.equals(capturedOn, key.capturedOn);
    }

    @Override
    public int hashCode() {
      return Objects.hash(accountId, capturedOn);
    }
  }

  @Id
  @Column(name = "account_id")
  private Long accountId;

  @Id
  @Column(name = "captured_on")
  private LocalDate capturedOn;

  @Column(name = "problems_solved")
  private Integer problemsSolved;

  @Column(name = "contest_rating")
  private Integer contestRating;

  /** Required by JPA. */
  protected CodingSnapshot() {}

  public Long getAccountId() {
    return accountId;
  }

  public LocalDate getCapturedOn() {
    return capturedOn;
  }

  public Integer getProblemsSolved() {
    return problemsSolved;
  }

  public Integer getContestRating() {
    return contestRating;
  }
}
