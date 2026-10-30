package com.campuscompanion.coding;

import com.campuscompanion.coding.platform.CodingPlatform;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** One student's linked accounts added up, for the leaderboards. */
record StudentTally(
    Long studentId,
    int totalSolved,
    int verifiedAccounts,
    int pendingAccounts,
    List<CodingPlatform> platforms,
    List<CodingAccount> accounts,
    Instant lastSyncedAt) {

  /** Groups accounts by student, keeping the students in the order they first appear. */
  static List<StudentTally> of(List<CodingAccount> all) {
    Map<Long, List<CodingAccount>> byStudent = new LinkedHashMap<>();
    all.forEach(
        account ->
            byStudent
                .computeIfAbsent(account.getStudentId(), id -> new ArrayList<>())
                .add(account));
    return byStudent.entrySet().stream()
        .map(entry -> tally(entry.getKey(), entry.getValue()))
        .toList();
  }

  private static StudentTally tally(Long studentId, List<CodingAccount> accounts) {
    List<CodingAccount> verified = accounts.stream().filter(CodingAccount::isVerified).toList();
    return new StudentTally(
        studentId,
        accounts.stream().mapToInt(CodingAccount::countedProblems).sum(),
        verified.size(),
        accounts.size() - verified.size(),
        verified.stream().map(CodingAccount::getPlatform).sorted().toList(),
        accounts,
        accounts.stream()
            .map(CodingAccount::getLastSyncedAt)
            .filter(Objects::nonNull)
            .max(Comparator.naturalOrder())
            .orElse(null));
  }
}
