package com.campuscompanion.coding;

import com.campuscompanion.coding.dto.CodingAccountResponse;
import com.campuscompanion.coding.dto.JourneyPoint;
import com.campuscompanion.coding.dto.PortfolioResponse;
import com.campuscompanion.common.config.CampusTime;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Builds a student's portfolio from the stored numbers, for the student and for admins. It reads
 * the database only; access is checked by the service that calls it.
 */
@Component
public class CodingPortfolios {

  /** The least time between two Verify or Refresh presses on one account. */
  static final Duration REFRESH_COOLDOWN = Duration.ofMinutes(15);

  /** The journey covers the last year. */
  private static final int JOURNEY_DAYS = 365;

  private final CodingAccountRepository accounts;
  private final CodingSnapshotRepository snapshots;
  private final LeaderboardOptinRepository optins;
  private final Clock clock;

  public CodingPortfolios(
      CodingAccountRepository accounts,
      CodingSnapshotRepository snapshots,
      LeaderboardOptinRepository optins,
      Clock clock) {
    this.accounts = accounts;
    this.snapshots = snapshots;
    this.optins = optins;
    this.clock = clock;
  }

  /** The portfolio of one student; the codes of pending accounts only when {@code forOwner}. */
  public PortfolioResponse of(Long studentId, boolean forOwner) {
    List<CodingAccount> linked = accounts.findByStudentIdOrderByPlatform(studentId);
    List<CodingAccountResponse> shown =
        linked.stream().map(account -> toResponse(account, forOwner)).toList();
    int total = linked.stream().mapToInt(CodingAccount::countedProblems).sum();
    int verified = (int) linked.stream().filter(CodingAccount::isVerified).count();
    return new PortfolioResponse(
        total, verified, optins.existsById(studentId), shown, journey(linked));
  }

  private List<JourneyPoint> journey(List<CodingAccount> linked) {
    List<Long> counted =
        linked.stream()
            .filter(account -> account.isVerified() && account.getPlatform().countsProblems())
            .map(CodingAccount::getId)
            .toList();
    if (counted.isEmpty()) {
      return List.of();
    }
    LocalDate from = CampusTime.today(clock).minusDays(JOURNEY_DAYS);
    List<CodingRules.DayCount> counts =
        snapshots.since(counted, from).stream()
            .filter(snapshot -> snapshot.getProblemsSolved() != null)
            .map(
                snapshot ->
                    new CodingRules.DayCount(
                        snapshot.getAccountId(),
                        snapshot.getCapturedOn(),
                        snapshot.getProblemsSolved()))
            .toList();
    return CodingRules.journey(counts);
  }

  static CodingAccountResponse toResponse(CodingAccount account, boolean forOwner) {
    boolean pending = !account.isVerified();
    return new CodingAccountResponse(
        account.getId(),
        account.getPlatform(),
        account.getPlatform().displayName(),
        account.getHandle(),
        account.getPlatform().profileUrl(account.getHandle()),
        account.getStatus(),
        forOwner && pending ? account.getVerificationCode() : null,
        account.getPlatform().codeHint(),
        account.getVerifiedAt(),
        account.getPlatform().countsProblems(),
        account.getProblemsSolved(),
        account.getEasySolved(),
        account.getMediumSolved(),
        account.getHardSolved(),
        account.getContestRating(),
        account.getMaxRating(),
        account.getContestsAttended(),
        account.getRankLabel(),
        account.getScore(),
        account.getPublicRepos(),
        account.getStars(),
        account.getFollowers(),
        account.getLastSyncedAt(),
        account.getLastSyncError(),
        account.isVerified() && account.getLastAttemptAt() != null
            ? account.getLastAttemptAt().plus(REFRESH_COOLDOWN)
            : null);
  }
}
