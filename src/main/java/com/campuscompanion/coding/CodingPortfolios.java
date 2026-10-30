package com.campuscompanion.coding;

import com.campuscompanion.coding.dto.CodingAccountResponse;
import com.campuscompanion.coding.dto.JourneyPoint;
import com.campuscompanion.coding.dto.PortfolioResponse;
import com.campuscompanion.coding.dto.StudentCard;
import com.campuscompanion.coding.platform.PlatformDetails;
import com.campuscompanion.common.config.CampusTime;
import com.campuscompanion.student.StudentContext;
import com.campuscompanion.student.StudentExtras;
import com.campuscompanion.student.StudentProfileService;
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
  private final StudentProfileService students;
  private final Clock clock;

  public CodingPortfolios(
      CodingAccountRepository accounts,
      CodingSnapshotRepository snapshots,
      LeaderboardOptinRepository optins,
      StudentProfileService students,
      Clock clock) {
    this.accounts = accounts;
    this.snapshots = snapshots;
    this.optins = optins;
    this.students = students;
    this.clock = clock;
  }

  /** The portfolio of one student; the codes of pending accounts only when {@code forOwner}. */
  public PortfolioResponse of(StudentContext student, boolean forOwner) {
    Long studentId = student.studentId();
    List<CodingAccount> linked = accounts.findByStudentIdOrderByPlatform(studentId);
    LocalDate today = CampusTime.today(clock);
    List<CodingAccountResponse> shown =
        linked.stream().map(account -> toResponse(account, forOwner, today)).toList();
    int total = linked.stream().mapToInt(CodingAccount::countedProblems).sum();
    int verified = (int) linked.stream().filter(CodingAccount::isVerified).count();
    List<PortfolioInsights.Source> sources =
        linked.stream()
            .filter(CodingAccount::isVerified)
            .map(
                account ->
                    new PortfolioInsights.Source(
                        account, CodingDetailsJson.read(account.getDetails())))
            .toList();
    StudentExtras extras = students.extrasOf(studentId);
    StudentCard card =
        new StudentCard(
            student.fullName(),
            student.rollNo(),
            student.branch(),
            student.currentSemester(),
            student.section(),
            extras.bio(),
            extras.hasPhoto());
    return new PortfolioResponse(
        card,
        total,
        verified,
        optins.existsById(studentId),
        shown,
        journey(linked),
        PortfolioInsights.problemSolving(sources, today),
        PortfolioInsights.development(sources, today));
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

  static CodingAccountResponse toResponse(
      CodingAccount account, boolean forOwner, LocalDate today) {
    boolean pending = !account.isVerified();
    PlatformDetails details = pending ? null : CodingDetailsJson.read(account.getDetails());
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
            : null,
        details,
        details == null ? null : PortfolioInsights.summarise(details.calendar(), today));
  }
}
