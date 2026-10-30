package com.campuscompanion.coding;

import com.campuscompanion.coding.dto.AdminLeaderboardEntry;
import com.campuscompanion.coding.dto.AdminPortfolioResponse;
import com.campuscompanion.common.config.CampusTime;
import com.campuscompanion.common.error.ResourceNotFoundException;
import com.campuscompanion.common.web.PageResponse;
import com.campuscompanion.student.StudentContext;
import com.campuscompanion.student.StudentProfileService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admins: every student with a linked coding account, whether or not they show on the students'
 * leaderboard, with how active they were in the last 30 days (FR-COD-005), and one student's
 * portfolio, read-only.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminCodingService {

  private static final int ACTIVITY_DAYS = 30;

  private final CodingAccountRepository accounts;
  private final CodingSnapshotRepository snapshots;
  private final LeaderboardOptinRepository optins;
  private final CodingPortfolios portfolios;
  private final StudentProfileService students;
  private final Clock clock;

  public AdminCodingService(
      CodingAccountRepository accounts,
      CodingSnapshotRepository snapshots,
      LeaderboardOptinRepository optins,
      CodingPortfolios portfolios,
      StudentProfileService students,
      Clock clock) {
    this.accounts = accounts;
    this.snapshots = snapshots;
    this.optins = optins;
    this.portfolios = portfolios;
    this.students = students;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public PageResponse<AdminLeaderboardEntry> leaderboard(
      String branch, Integer semester, String section, String query, int page, int size) {
    List<StudentTally> tallies =
        StudentTally.of(accounts.findAllByOrderByStudentIdAscPlatformAsc());
    Map<Long, StudentContext> who =
        students.studentsByIds(tallies.stream().map(StudentTally::studentId).toList());
    String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
    List<StudentTally> ranked =
        tallies.stream()
            .filter(tally -> who.containsKey(tally.studentId()))
            .filter(tally -> matches(who.get(tally.studentId()), branch, semester, section, needle))
            .sorted(
                Comparator.comparingInt(StudentTally::totalSolved)
                    .reversed()
                    .thenComparing(tally -> who.get(tally.studentId()).fullName()))
            .toList();
    List<Integer> ranks =
        CodingRules.ranks(ranked.stream().map(StudentTally::totalSolved).toList());
    Set<Long> shown = new HashSet<>();
    optins.findAll().forEach(optin -> shown.add(optin.getStudentId()));

    int from = Math.min(page * size, ranked.size());
    int to = Math.min(from + size, ranked.size());
    Map<Long, Integer> baselines = baselines(ranked.subList(from, to));
    List<AdminLeaderboardEntry> content =
        IntStream.range(from, to)
            .mapToObj(i -> entry(ranked.get(i), ranks.get(i), who, shown, baselines))
            .toList();
    int totalPages = (ranked.size() + size - 1) / size;
    return new PageResponse<>(content, page, size, ranked.size(), totalPages);
  }

  @Transactional(readOnly = true)
  public AdminPortfolioResponse portfolio(Long studentId) {
    StudentContext student =
        students.findStudent(studentId).orElseThrow(() -> new ResourceNotFoundException());
    return new AdminPortfolioResponse(
        student.studentId(),
        student.rollNo(),
        student.fullName(),
        student.branch(),
        student.currentSemester(),
        student.section(),
        portfolios.of(student, false));
  }

  private static boolean matches(
      StudentContext student, String branch, Integer semester, String section, String needle) {
    return (branch == null || branch.isBlank() || branch.equalsIgnoreCase(student.branch()))
        && (semester == null || semester == student.currentSemester())
        && (section == null || section.isBlank() || section.equalsIgnoreCase(student.section()))
        && (needle.isEmpty()
            || student.fullName().toLowerCase(Locale.ROOT).contains(needle)
            || student.rollNo().toLowerCase(Locale.ROOT).contains(needle));
  }

  /** The earliest count of each counted account in the last 30 days: the start of the activity. */
  private Map<Long, Integer> baselines(List<StudentTally> onPage) {
    List<Long> counted =
        onPage.stream()
            .flatMap(tally -> tally.accounts().stream())
            .filter(account -> account.isVerified() && account.getPlatform().countsProblems())
            .map(CodingAccount::getId)
            .toList();
    Map<Long, Integer> first = new HashMap<>();
    if (counted.isEmpty()) {
      return first;
    }
    LocalDate from = CampusTime.today(clock).minusDays(ACTIVITY_DAYS);
    snapshots.since(counted, from).stream()
        .filter(snapshot -> snapshot.getProblemsSolved() != null)
        .forEach(
            snapshot -> first.putIfAbsent(snapshot.getAccountId(), snapshot.getProblemsSolved()));
    return first;
  }

  private static AdminLeaderboardEntry entry(
      StudentTally tally,
      int rank,
      Map<Long, StudentContext> who,
      Set<Long> shown,
      Map<Long, Integer> baselines) {
    StudentContext student = who.get(tally.studentId());
    int recent =
        tally.accounts().stream()
            .filter(account -> account.countedProblems() > 0)
            .mapToInt(
                account ->
                    CodingRules.solvedSince(
                        account.countedProblems(), baselines.get(account.getId())))
            .sum();
    return new AdminLeaderboardEntry(
        rank,
        student.studentId(),
        student.rollNo(),
        student.fullName(),
        student.branch(),
        student.currentSemester(),
        student.section(),
        tally.totalSolved(),
        recent,
        tally.verifiedAccounts(),
        tally.pendingAccounts(),
        tally.platforms(),
        shown.contains(tally.studentId()),
        tally.lastSyncedAt());
  }
}
