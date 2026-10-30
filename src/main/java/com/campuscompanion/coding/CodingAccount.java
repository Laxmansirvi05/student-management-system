package com.campuscompanion.coding;

import com.campuscompanion.coding.platform.CodingPlatform;
import com.campuscompanion.coding.platform.PlatformStats;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A student's account on one coding site (FR-COD-001), and the numbers last read from it. One per
 * student and site.
 */
@Entity
@Table(name = "coding_accounts")
public class CodingAccount {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "student_id", nullable = false, updatable = false)
  private Long studentId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 20)
  private CodingPlatform platform;

  @Column(nullable = false, updatable = false, length = 50)
  private String handle;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private CodingAccountStatus status = CodingAccountStatus.PENDING;

  @Column(name = "verification_code", nullable = false, updatable = false, length = 12)
  private String verificationCode;

  @Column(name = "verified_at")
  private Instant verifiedAt;

  @Column(name = "problems_solved")
  private Integer problemsSolved;

  @Column(name = "easy_solved")
  private Integer easySolved;

  @Column(name = "medium_solved")
  private Integer mediumSolved;

  @Column(name = "hard_solved")
  private Integer hardSolved;

  @Column(name = "contest_rating")
  private Integer contestRating;

  @Column(name = "max_rating")
  private Integer maxRating;

  @Column(name = "contests_attended")
  private Integer contestsAttended;

  @Column(name = "rank_label", length = 40)
  private String rankLabel;

  private Integer score;

  @Column(name = "public_repos")
  private Integer publicRepos;

  private Integer stars;

  private Integer followers;

  @Column(name = "last_synced_at")
  private Instant lastSyncedAt;

  @Column(name = "last_sync_error", length = 20)
  private String lastSyncError;

  @Column(name = "last_attempt_at")
  private Instant lastAttemptAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  /** Required by JPA. */
  protected CodingAccount() {}

  public CodingAccount(
      Long studentId, CodingPlatform platform, String handle, String verificationCode) {
    this.studentId = studentId;
    this.platform = platform;
    this.handle = handle;
    this.verificationCode = verificationCode;
  }

  /** The code was found in the student's profile on the site. */
  public void verify(Instant now) {
    status = CodingAccountStatus.VERIFIED;
    verifiedAt = now;
  }

  /** A good read: the numbers replace the old ones and any error is cleared. */
  public void apply(PlatformStats stats, Instant now) {
    problemsSolved = stats.problemsSolved();
    easySolved = stats.easySolved();
    mediumSolved = stats.mediumSolved();
    hardSolved = stats.hardSolved();
    contestRating = stats.contestRating();
    maxRating = stats.maxRating();
    contestsAttended = stats.contestsAttended();
    rankLabel = stats.rankLabel();
    score = stats.score();
    publicRepos = stats.publicRepos();
    stars = stats.stars();
    followers = stats.followers();
    lastSyncedAt = now;
    lastSyncError = null;
  }

  /** A failed read keeps the last good numbers and notes why it failed. */
  public void failed(String reason) {
    lastSyncError = reason;
  }

  /** The student pressed Verify or Refresh; used for the waiting time between presses. */
  public void attempted(Instant now) {
    lastAttemptAt = now;
  }

  public boolean isVerified() {
    return status == CodingAccountStatus.VERIFIED;
  }

  /** The problems that count on the leaderboard: verified, on a site that counts, or zero. */
  public int countedProblems() {
    return isVerified() && platform.countsProblems() && problemsSolved != null ? problemsSolved : 0;
  }

  public Long getId() {
    return id;
  }

  public Long getStudentId() {
    return studentId;
  }

  public CodingPlatform getPlatform() {
    return platform;
  }

  public String getHandle() {
    return handle;
  }

  public CodingAccountStatus getStatus() {
    return status;
  }

  public String getVerificationCode() {
    return verificationCode;
  }

  public Instant getVerifiedAt() {
    return verifiedAt;
  }

  public Integer getProblemsSolved() {
    return problemsSolved;
  }

  public Integer getEasySolved() {
    return easySolved;
  }

  public Integer getMediumSolved() {
    return mediumSolved;
  }

  public Integer getHardSolved() {
    return hardSolved;
  }

  public Integer getContestRating() {
    return contestRating;
  }

  public Integer getMaxRating() {
    return maxRating;
  }

  public Integer getContestsAttended() {
    return contestsAttended;
  }

  public String getRankLabel() {
    return rankLabel;
  }

  public Integer getScore() {
    return score;
  }

  public Integer getPublicRepos() {
    return publicRepos;
  }

  public Integer getStars() {
    return stars;
  }

  public Integer getFollowers() {
    return followers;
  }

  public Instant getLastSyncedAt() {
    return lastSyncedAt;
  }

  public String getLastSyncError() {
    return lastSyncError;
  }

  public Instant getLastAttemptAt() {
    return lastAttemptAt;
  }
}
