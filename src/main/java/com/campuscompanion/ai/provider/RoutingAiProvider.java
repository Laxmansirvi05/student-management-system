package com.campuscompanion.ai.provider;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends each request to the first healthy model in a fixed priority list, and to the next one only
 * when that fails for a reason another model can get past. The list comes from AI_ROUTES and is
 * never reordered at run time.
 *
 * <p>Rules, so that a failure never turns into a storm of calls or a bill:
 *
 * <ul>
 *   <li>At most {@code maxAttempts} calls per request, each no longer than {@code attemptTimeout},
 *       all within the request's total time budget.
 *   <li>A rate limit or used-up quota pauses the <em>whole provider</em> (every model and every key
 *       of it) for the provider's Retry-After or a default wait. Keys of one provider are not tried
 *       one after another to get around its limits.
 *   <li>A refused key (401, 403) pauses only that key; the provider's next key may be tried.
 *   <li>An unknown model or a refused request pauses that model for a long while; an outage or a
 *       timeout pauses it briefly.
 *   <li>A reply that fails the caller's check moves on to the next <em>model</em> (not the same
 *       model with another key) without a pause.
 *   <li>When every model is paused, the request fails at once, without calling anyone; paused
 *       models come back by themselves when their pause ends.
 *   <li>The mock is never a route: when everything fails, the caller gets an error, never a made-up
 *       answer.
 * </ul>
 *
 * Only route names (provider, model, key number) and failure kinds are logged; never a key, a
 * prompt or a reply.
 */
public class RoutingAiProvider implements AiProvider {

  /** One way to reach a model: a provider, a model, one of the provider's keys, and the adapter. */
  public record Route(String provider, String model, int keyNumber, AiProvider adapter) {

    /** For logs: names and the key's number in the environment, never the key. */
    public String label() {
      return provider + ":" + model + " (key " + keyNumber + ")";
    }
  }

  static final Duration RATE_LIMIT_PAUSE = Duration.ofSeconds(60);
  static final Duration OUTAGE_PAUSE = Duration.ofSeconds(30);
  static final Duration MISCONFIGURED_PAUSE = Duration.ofMinutes(30);

  /** Below this, there is no point starting another call. */
  static final Duration MINIMUM_CALL_TIME = Duration.ofSeconds(2);

  private static final Logger log = LoggerFactory.getLogger(RoutingAiProvider.class);

  private final List<Route> routes;
  private final int maxAttempts;
  private final Duration attemptTimeout;
  private final Duration totalBudget;
  private final Clock clock;
  private final Map<String, Instant> pausedUntil = new ConcurrentHashMap<>();

  public RoutingAiProvider(
      List<Route> routes,
      int maxAttempts,
      Duration attemptTimeout,
      Duration totalBudget,
      Clock clock) {
    if (routes.isEmpty()) {
      throw new IllegalArgumentException("At least one route is needed.");
    }
    this.routes = List.copyOf(routes);
    this.maxAttempts = maxAttempts;
    this.attemptTimeout = attemptTimeout;
    this.totalBudget = totalBudget;
    this.clock = clock;
  }

  public List<Route> routes() {
    return routes;
  }

  @Override
  public AiCompletion complete(AiRequest request) throws AiProviderException {
    return complete(request, reply -> true);
  }

  @Override
  public AiCompletion complete(AiRequest request, Predicate<String> usable)
      throws AiProviderException {
    Instant deadline = clock.instant().plus(totalBudget);
    AiProviderException.Kind worst = null;
    int attempts = 0;
    // A model that gave an unusable reply is not asked again with another key in this request.
    Set<String> unusableModels = new HashSet<>();
    for (Route route : routes) {
      if (attempts >= maxAttempts) {
        break;
      }
      if (isPaused(route) || unusableModels.contains(modelKey(route))) {
        continue;
      }
      Duration left = Duration.between(clock.instant(), deadline);
      if (left.compareTo(MINIMUM_CALL_TIME) < 0) {
        worst = AiProviderException.Kind.TIMEOUT;
        break;
      }
      Duration timeout = shortest(request.timeout(), attemptTimeout, left);
      attempts++;
      try {
        AiCompletion completion = route.adapter().complete(request.withTimeout(timeout));
        if (usable.test(completion.text())) {
          if (attempts > 1) {
            log.info("AI request answered by {} after {} attempts", route.label(), attempts);
          }
          return completion;
        }
        log.warn("AI reply from {} failed the checks; trying the next model", route.label());
        unusableModels.add(modelKey(route));
        worst = worse(worst, AiProviderException.Kind.INVALID_OUTPUT);
      } catch (AiProviderException failure) {
        log.warn("AI call to {} failed: {}", route.label(), failure.getKind());
        pauseAfter(route, failure);
        worst = worse(worst, failure.getKind());
      }
    }
    throw new AiProviderException(worst == null ? AiProviderException.Kind.UNAVAILABLE : worst);
  }

  /** Whether a route is resting after a failure. A pause that has ended is forgotten. */
  boolean isPaused(Route route) {
    Instant now = clock.instant();
    for (String key : List.of(providerKey(route), modelKey(route), keyKey(route))) {
      Instant until = pausedUntil.get(key);
      if (until != null) {
        if (now.isBefore(until)) {
          return true;
        }
        pausedUntil.remove(key, until);
      }
    }
    return false;
  }

  private static Duration shortest(Duration a, Duration b, Duration c) {
    Duration shorter = a.compareTo(b) <= 0 ? a : b;
    return shorter.compareTo(c) <= 0 ? shorter : c;
  }

  private void pauseAfter(Route route, AiProviderException failure) {
    switch (failure.getKind()) {
      case RATE_LIMITED ->
          pause(
              providerKey(route),
              failure.getRetryAfter() == null ? RATE_LIMIT_PAUSE : failure.getRetryAfter());
      case AUTH_FAILED -> pause(keyKey(route), MISCONFIGURED_PAUSE);
      case MODEL_UNAVAILABLE, BAD_REQUEST -> pause(modelKey(route), MISCONFIGURED_PAUSE);
      case UNAVAILABLE, TIMEOUT -> pause(modelKey(route), OUTAGE_PAUSE);
      case INVALID_OUTPUT -> {}
    }
  }

  private void pause(String key, Duration length) {
    pausedUntil.merge(key, clock.instant().plus(length), (a, b) -> a.isAfter(b) ? a : b);
  }

  /**
   * What to tell the student when everything failed: a timeout if that was what happened, a reply
   * problem if every model answered badly, otherwise "unavailable".
   */
  private static AiProviderException.Kind worse(
      AiProviderException.Kind so, AiProviderException.Kind now) {
    if (so == null) {
      return now;
    }
    if (so == AiProviderException.Kind.TIMEOUT || now == AiProviderException.Kind.TIMEOUT) {
      return AiProviderException.Kind.TIMEOUT;
    }
    if (so == AiProviderException.Kind.INVALID_OUTPUT
        && now == AiProviderException.Kind.INVALID_OUTPUT) {
      return so;
    }
    return AiProviderException.Kind.UNAVAILABLE;
  }

  private static String providerKey(Route route) {
    return "provider:" + route.provider();
  }

  private static String modelKey(Route route) {
    return "model:" + route.provider() + ":" + route.model();
  }

  private static String keyKey(Route route) {
    return "key:" + route.provider() + ":" + route.keyNumber();
  }
}
