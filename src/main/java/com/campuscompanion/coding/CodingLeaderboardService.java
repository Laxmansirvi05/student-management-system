package com.campuscompanion.coding;

import com.campuscompanion.coding.dto.LeaderboardEntry;
import com.campuscompanion.coding.dto.LeaderboardResponse;
import com.campuscompanion.coding.dto.MyStanding;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.student.StudentContext;
import com.campuscompanion.student.StudentProfileService;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The students' leaderboard (FR-COD-004): the students who chose to show, with at least one
 * verified account, ranked by total problems solved across the whole college. Equal totals share a
 * rank. It shows names, class and totals only, never handles or roll numbers.
 */
@Service
@PreAuthorize("hasRole('STUDENT')")
public class CodingLeaderboardService {

  private final CodingAccountRepository accounts;
  private final LeaderboardOptinRepository optins;
  private final StudentProfileService students;

  public CodingLeaderboardService(
      CodingAccountRepository accounts,
      LeaderboardOptinRepository optins,
      StudentProfileService students) {
    this.accounts = accounts;
    this.optins = optins;
    this.students = students;
  }

  @Transactional(readOnly = true)
  public LeaderboardResponse leaderboard(CurrentUser user, int page, int size) {
    Long me = students.currentStudent(user).studentId();
    Set<Long> shown = new HashSet<>();
    optins.findAll().forEach(optin -> shown.add(optin.getStudentId()));
    List<StudentTally> tallies =
        StudentTally.of(accounts.findAllByOrderByStudentIdAscPlatformAsc());
    List<StudentTally> listed =
        tallies.stream()
            .filter(tally -> shown.contains(tally.studentId()) && tally.verifiedAccounts() > 0)
            .toList();
    Map<Long, StudentContext> who =
        students.studentsByIds(listed.stream().map(StudentTally::studentId).toList());
    List<StudentTally> ranked =
        listed.stream()
            .filter(tally -> who.containsKey(tally.studentId()))
            .sorted(
                Comparator.comparingInt(StudentTally::totalSolved)
                    .reversed()
                    .thenComparing(tally -> who.get(tally.studentId()).fullName()))
            .toList();
    List<Integer> ranks =
        CodingRules.ranks(ranked.stream().map(StudentTally::totalSolved).toList());

    int from = Math.min(page * size, ranked.size());
    int to = Math.min(from + size, ranked.size());
    List<LeaderboardEntry> content =
        IntStream.range(from, to)
            .mapToObj(
                i -> {
                  StudentTally tally = ranked.get(i);
                  StudentContext student = who.get(tally.studentId());
                  return new LeaderboardEntry(
                      ranks.get(i),
                      student.fullName(),
                      student.branch(),
                      student.currentSemester(),
                      tally.totalSolved(),
                      tally.platforms(),
                      tally.studentId().equals(me));
                })
            .toList();
    int totalPages = (ranked.size() + size - 1) / size;
    return new LeaderboardResponse(
        content,
        page,
        size,
        ranked.size(),
        totalPages,
        standing(me, shown, tallies, ranked, ranks));
  }

  private static MyStanding standing(
      Long me,
      Set<Long> shown,
      List<StudentTally> tallies,
      List<StudentTally> ranked,
      List<Integer> ranks) {
    StudentTally mine =
        tallies.stream().filter(tally -> tally.studentId().equals(me)).findFirst().orElse(null);
    Integer rank = null;
    for (int i = 0; i < ranked.size(); i++) {
      if (ranked.get(i).studentId().equals(me)) {
        rank = ranks.get(i);
        break;
      }
    }
    return new MyStanding(
        shown.contains(me),
        rank,
        mine == null ? 0 : mine.totalSolved(),
        mine == null ? 0 : mine.verifiedAccounts());
  }
}
