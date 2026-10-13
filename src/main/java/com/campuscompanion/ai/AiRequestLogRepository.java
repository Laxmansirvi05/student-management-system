package com.campuscompanion.ai;

import java.time.Instant;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiRequestLogRepository extends JpaRepository<AiRequestLog, Long> {

  /** One user's requests with one of the given outcomes, from a moment on. */
  long countByUserIdAndStatusInAndCreatedAtGreaterThanEqual(
      Long userId, Collection<AiRequestStatus> statuses, Instant from);

  /** Everyone's requests with one of the given outcomes, from a moment on. */
  long countByStatusInAndCreatedAtGreaterThanEqual(
      Collection<AiRequestStatus> statuses, Instant from);
}
