package com.campuscompanion.ai;

import com.campuscompanion.ai.dto.AiUsageResponse;
import com.campuscompanion.common.config.AiProperties;
import com.campuscompanion.common.config.CampusTime;
import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * The limits of the AI assistant, and its usage log (docs/12-AI-features-spec.md section 2).
 *
 * <p>Before any request goes to the AI provider, {@link #begin} checks, in this order: the kill
 * switch, the daily ceiling for everyone together, the student's own daily limit, the student's
 * limit per minute, and how many of the student's requests are running at once.
 *
 * <p>The two daily counts come from the usage log in the database, so a restart does not reset
 * them. The per-minute count and the running count are short-lived and are kept in memory.
 */
@Service
public class AiUsageService {

  private static final Duration MINUTE = Duration.ofMinutes(1);
  // Which outcomes use up a student's requests is decided in one place, AiRequestStatus.
  private static final Set<AiRequestStatus> COUNTED = countedStatuses();

  static final String SWITCHED_OFF = "The assistant is unavailable right now.";
  static final String CEILING_REACHED =
      "The assistant has reached today's limit for everyone. Try again tomorrow.";
  static final String QUOTA_REACHED = "You have used today's AI requests. Try again tomorrow.";
  static final String TOO_FAST = "Please wait a moment.";

  private final AiRequestLogRepository logs;
  private final AiProperties properties;
  private final Clock clock;

  /** For each user, when their requests of the last minute started. Guarded by "this". */
  private final Map<Long, Deque<Instant>> recentStarts = new HashMap<>();

  /** For each user, how many of their requests are running now. Guarded by "this". */
  private final Map<Long, Integer> running = new HashMap<>();

  public AiUsageService(AiRequestLogRepository logs, AiProperties properties, Clock clock) {
    this.logs = logs;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * Checks every limit and, if the request may go ahead, counts it as running. The caller must call
   * {@link #end} when the request is over, whatever happened to it.
   */
  public void begin(long userId) {
    if (!properties.enabled()) {
      throw new AiUnavailableException(SWITCHED_OFF);
    }
    Instant dayStart = startOfToday();
    if (logs.countByStatusInAndCreatedAtGreaterThanEqual(COUNTED, dayStart)
        >= properties.dailyLimitGlobal()) {
      throw new AiUnavailableException(CEILING_REACHED);
    }
    if (usedToday(userId, dayStart) >= properties.dailyLimitPerUser()) {
      throw new AiQuotaExceededException(QUOTA_REACHED);
    }
    takeSlot(userId);
  }

  /** Marks one of the user's requests as no longer running. */
  public synchronized void end(long userId) {
    running.computeIfPresent(userId, (user, count) -> count > 1 ? count - 1 : null);
  }

  /** Writes one row of the usage log: numbers and labels, never any text. */
  public void record(
      long userId,
      AiFeature feature,
      String promptVersion,
      int inputChars,
      Integer outputChars,
      AiRequestStatus status,
      long durationMs) {
    logs.save(
        new AiRequestLog(
            userId,
            feature,
            promptVersion,
            inputChars,
            outputChars,
            status,
            durationMs,
            clock.instant()));
  }

  public AiUsageResponse usageFor(long userId) {
    Instant dayStart = startOfToday();
    int used = (int) usedToday(userId, dayStart);
    boolean ceilingReached =
        logs.countByStatusInAndCreatedAtGreaterThanEqual(COUNTED, dayStart)
            >= properties.dailyLimitGlobal();
    return new AiUsageResponse(
        properties.dailyLimitPerUser(),
        used,
        Math.max(0, properties.dailyLimitPerUser() - used),
        startOfTomorrow(),
        properties.enabled() && !ceilingReached,
        "mock".equalsIgnoreCase(properties.provider()));
  }

  /**
   * Removes up to {@code limit} usage rows created before the cutoff and returns how many went.
   * Used by the nightly retention job (T7-09).
   */
  public int removeLogsOlderThan(Instant cutoff, int limit) {
    return logs.deleteOlderThan(cutoff, limit);
  }

  private long usedToday(long userId, Instant dayStart) {
    return logs.countByUserIdAndStatusInAndCreatedAtGreaterThanEqual(userId, COUNTED, dayStart);
  }

  /** The per-minute limit and the limit on requests running at once. Both are per user. */
  private synchronized void takeSlot(long userId) {
    Instant now = clock.instant();
    Deque<Instant> starts = recentStarts.computeIfAbsent(userId, user -> new ArrayDeque<>());
    while (!starts.isEmpty() && !starts.peekFirst().isAfter(now.minus(MINUTE))) {
      starts.removeFirst();
    }
    if (starts.size() >= properties.perMinuteLimit()
        || running.getOrDefault(userId, 0) >= properties.maxConcurrentPerUser()) {
      throw new ApiException(ErrorCode.RATE_LIMITED, TOO_FAST);
    }
    starts.addLast(now);
    running.merge(userId, 1, Integer::sum);
  }

  /** Midnight in India at the start of the current day there. The daily limits reset then. */
  private Instant startOfToday() {
    return CampusTime.today(clock).atStartOfDay(CampusTime.ZONE).toInstant();
  }

  private Instant startOfTomorrow() {
    ZonedDateTime tomorrow = CampusTime.today(clock).plusDays(1).atStartOfDay(CampusTime.ZONE);
    return tomorrow.toInstant();
  }

  private static Set<AiRequestStatus> countedStatuses() {
    Set<AiRequestStatus> counted = EnumSet.noneOf(AiRequestStatus.class);
    for (AiRequestStatus status : AiRequestStatus.values()) {
      if (status.countsTowardLimits()) {
        counted.add(status);
      }
    }
    return counted;
  }
}
