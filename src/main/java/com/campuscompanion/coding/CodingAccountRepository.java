package com.campuscompanion.coding;

import com.campuscompanion.coding.platform.CodingPlatform;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CodingAccountRepository extends JpaRepository<CodingAccount, Long> {

  List<CodingAccount> findByStudentIdOrderByPlatform(Long studentId);

  /** An account of one student; another student's ID never matches. */
  Optional<CodingAccount> findByIdAndStudentId(Long id, Long studentId);

  boolean existsByStudentIdAndPlatform(Long studentId, CodingPlatform platform);

  /** Whether another account has already verified this handle on the site, ignoring case. */
  @Query(
      """
      select count(a) > 0 from CodingAccount a
      where a.platform = :platform
        and lower(a.handle) = lower(:handle)
        and a.status = com.campuscompanion.coding.CodingAccountStatus.VERIFIED
        and a.id <> :exceptId
      """)
  boolean verifiedElsewhere(
      @Param("platform") CodingPlatform platform,
      @Param("handle") String handle,
      @Param("exceptId") Long exceptId);

  /** Every linked account, for the leaderboards. */
  List<CodingAccount> findAllByOrderByStudentIdAscPlatformAsc();

  /** The verified accounts not read since the cutoff, least recently read first. */
  @Query(
      """
      select a.id from CodingAccount a
      where a.status = com.campuscompanion.coding.CodingAccountStatus.VERIFIED
        and (a.lastSyncedAt is null or a.lastSyncedAt < :cutoff)
      order by a.lastSyncedAt asc nulls first, a.id asc
      """)
  List<Long> dueForSync(@Param("cutoff") Instant cutoff, Pageable pageable);
}
