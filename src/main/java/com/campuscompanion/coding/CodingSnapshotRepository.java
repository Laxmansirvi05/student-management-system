package com.campuscompanion.coding;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CodingSnapshotRepository
    extends JpaRepository<CodingSnapshot, CodingSnapshot.Key> {

  /** Today's numbers of an account; a later read on the same day replaces them. */
  @Modifying
  @Query(
      value =
          """
          insert into app.coding_snapshots (account_id, captured_on, problems_solved, contest_rating)
          values (:accountId, :day, :solved, :rating)
          on conflict (account_id, captured_on)
          do update set problems_solved = excluded.problems_solved,
                        contest_rating = excluded.contest_rating
          """,
      nativeQuery = true)
  void record(
      @Param("accountId") Long accountId,
      @Param("day") LocalDate day,
      @Param("solved") Integer solved,
      @Param("rating") Integer rating);

  @Query(
      """
      select s from CodingSnapshot s
      where s.accountId in :accountIds and s.capturedOn >= :from
      order by s.capturedOn asc
      """)
  List<CodingSnapshot> since(
      @Param("accountIds") Collection<Long> accountIds, @Param("from") LocalDate from);
}
