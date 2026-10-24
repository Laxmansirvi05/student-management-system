package com.campuscompanion.ai;

import java.time.Instant;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AiRequestLogRepository extends JpaRepository<AiRequestLog, Long> {

  /** One user's requests with one of the given outcomes, from a moment on. */
  long countByUserIdAndStatusInAndCreatedAtGreaterThanEqual(
      Long userId, Collection<AiRequestStatus> statuses, Instant from);

  /** Everyone's requests with one of the given outcomes, from a moment on. */
  long countByStatusInAndCreatedAtGreaterThanEqual(
      Collection<AiRequestStatus> statuses, Instant from);

  /**
   * Deletes up to {@code limit} rows older than the cutoff and returns how many went. Small batches
   * keep each transaction and its locks short (T7-09).
   */
  @Modifying
  @Transactional
  @Query(
      value =
          "delete from app.ai_request_logs where id in (select id from app.ai_request_logs"
              + " where created_at < :cutoff order by id limit :limit)",
      nativeQuery = true)
  int deleteOlderThan(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}
