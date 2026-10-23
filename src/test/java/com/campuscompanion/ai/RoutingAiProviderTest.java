package com.campuscompanion.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campuscompanion.ai.provider.AiCompletion;
import com.campuscompanion.ai.provider.AiProvider;
import com.campuscompanion.ai.provider.AiProviderException;
import com.campuscompanion.ai.provider.AiProviderException.Kind;
import com.campuscompanion.ai.provider.AiRequest;
import com.campuscompanion.ai.provider.RoutingAiProvider;
import com.campuscompanion.ai.provider.RoutingAiProvider.Route;
import com.campuscompanion.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The router's fallback rules (docs/12 section 2b), with scripted adapters and a clock the test
 * moves: which model answers, which are paused and for how long, and that nothing is tried more
 * often than allowed.
 */
class RoutingAiProviderTest {

  private final MutableClock clock = new MutableClock(Instant.parse("2026-10-23T10:00:00Z"));
  private final List<String> calls = new ArrayList<>();

  /** An adapter that answers with its script, one entry per call, and then with "ok". */
  private class Scripted implements AiProvider {
    private final String name;
    private final Deque<Object> script = new ArrayDeque<>();
    private Duration lastTimeout;

    Scripted(String name, Object... answers) {
      this.name = name;
      script.addAll(List.of(answers));
    }

    @Override
    public AiCompletion complete(AiRequest request) throws AiProviderException {
      calls.add(name);
      lastTimeout = request.timeout();
      Object next = script.isEmpty() ? "ok from " + name : script.poll();
      if (next instanceof AiProviderException failure) {
        throw failure;
      }
      if (next instanceof Duration slow) {
        clock.advance(slow);
        throw new AiProviderException(Kind.TIMEOUT);
      }
      return new AiCompletion((String) next, 1, ((String) next).length());
    }
  }

  private static AiProviderException fail(Kind kind) {
    return new AiProviderException(kind);
  }

  private static AiRequest request() {
    return new AiRequest("explain-v1", "rules", "text", 100, Duration.ofSeconds(20));
  }

  private RoutingAiProvider router(int maxAttempts, Route... routes) {
    return new RoutingAiProvider(
        List.of(routes), maxAttempts, Duration.ofSeconds(15), Duration.ofSeconds(25), clock);
  }

  private static Route route(String provider, String model, int key, AiProvider adapter) {
    return new Route(provider, model, key, adapter);
  }

  @Test
  void theFirstHealthyModelAnswersAndNobodyElseIsCalled() throws Exception {
    Scripted groq = new Scripted("groq");
    Scripted ollama = new Scripted("ollama");
    RoutingAiProvider router =
        router(3, route("groq", "a", 1, groq), route("ollama", "b", 1, ollama));

    assertThat(router.complete(request()).text()).isEqualTo("ok from groq");
    assertThat(calls).containsExactly("groq");
  }

  @Test
  void aRateLimitPausesTheWholeProviderSoItsOtherKeysAreNotUsedToGetAroundIt() throws Exception {
    Scripted groqKey1 = new Scripted("groq#1", fail(Kind.RATE_LIMITED));
    Scripted groqKey2 = new Scripted("groq#2");
    Scripted groqOtherModel = new Scripted("groq-other");
    Scripted ollama = new Scripted("ollama");
    RoutingAiProvider router =
        router(
            3,
            route("groq", "a", 1, groqKey1),
            route("groq", "a", 2, groqKey2),
            route("groq", "b", 1, groqOtherModel),
            route("ollama", "c", 1, ollama));

    assertThat(router.complete(request()).text()).isEqualTo("ok from ollama");
    assertThat(calls).containsExactly("groq#1", "ollama");

    // Still paused 59 seconds later (the default pause is a minute): Groq is not asked at all.
    calls.clear();
    clock.advance(Duration.ofSeconds(59));
    router.complete(request());
    assertThat(calls).containsExactly("ollama");

    // After the pause, Groq is first again.
    calls.clear();
    clock.advance(Duration.ofSeconds(2));
    assertThat(router.complete(request()).text()).isEqualTo("ok from groq#1");
  }

  @Test
  void theProvidersRetryAfterDecidesHowLongItRests() throws Exception {
    Scripted groq =
        new Scripted("groq", new AiProviderException(Kind.RATE_LIMITED, Duration.ofSeconds(300)));
    Scripted ollama = new Scripted("ollama");
    RoutingAiProvider router =
        router(3, route("groq", "a", 1, groq), route("ollama", "b", 1, ollama));

    router.complete(request());
    clock.advance(Duration.ofSeconds(120));
    calls.clear();
    router.complete(request());
    assertThat(calls).containsExactly("ollama");
    clock.advance(Duration.ofSeconds(181));
    calls.clear();
    router.complete(request());
    assertThat(calls).containsExactly("groq");
  }

  @Test
  void aRefusedKeyIsSetAsideAndTheProvidersNextKeyIsTried() throws Exception {
    Scripted key1 = new Scripted("groq#1", fail(Kind.AUTH_FAILED));
    Scripted key2 = new Scripted("groq#2");
    RoutingAiProvider router = router(3, route("groq", "a", 1, key1), route("groq", "a", 2, key2));

    assertThat(router.complete(request()).text()).isEqualTo("ok from groq#2");
    calls.clear();
    router.complete(request());
    assertThat(calls).containsExactly("groq#2");
  }

  @Test
  void anUnknownModelIsSetAsideForEveryKeyAndAnotherModelIsUsed() throws Exception {
    Scripted retiredKey1 = new Scripted("retired#1", fail(Kind.MODEL_UNAVAILABLE));
    Scripted retiredKey2 = new Scripted("retired#2");
    Scripted other = new Scripted("other");
    RoutingAiProvider router =
        router(
            3,
            route("gemini", "retired", 1, retiredKey1),
            route("gemini", "retired", 2, retiredKey2),
            route("gemini", "current", 1, other));

    assertThat(router.complete(request()).text()).isEqualTo("ok from other");
    assertThat(calls).containsExactly("retired#1", "other");
  }

  @Test
  void outagesAndTimeoutsFallBackAndRecoverAfterAShortPause() throws Exception {
    Scripted down = new Scripted("down", fail(Kind.UNAVAILABLE));
    Scripted slow = new Scripted("slow", Duration.ofSeconds(15));
    Scripted fine = new Scripted("fine");
    RoutingAiProvider router =
        router(
            3,
            route("groq", "a", 1, down),
            route("ollama", "b", 1, slow),
            route("openrouter", "c", 1, fine));

    assertThat(router.complete(request()).text()).isEqualTo("ok from fine");
    assertThat(calls).containsExactly("down", "slow", "fine");

    clock.advance(Duration.ofSeconds(31));
    calls.clear();
    assertThat(router.complete(request()).text()).isEqualTo("ok from down");
  }

  @Test
  void aReplyThatFailsTheCallersCheckGoesToTheNextModelWithoutAPause() throws Exception {
    Scripted sloppy = new Scripted("sloppy", "not json");
    Scripted careful = new Scripted("careful", "{\"ok\":true}");
    RoutingAiProvider router =
        router(3, route("groq", "a", 1, sloppy), route("ollama", "b", 1, careful));

    AiCompletion answer = router.complete(request(), reply -> reply.startsWith("{"));
    assertThat(answer.text()).isEqualTo("{\"ok\":true}");
    calls.clear();
    // Not paused: the next request asks the first model again.
    router.complete(request(), reply -> true);
    assertThat(calls).containsExactly("sloppy");
  }

  @Test
  void anUnusableReplyIsNotAskedForAgainFromTheSameModelWithAnotherKey() throws Exception {
    Scripted key1 = new Scripted("ollama#1", "bad");
    Scripted key2 = new Scripted("ollama#2", "{}");
    Scripted otherModel = new Scripted("groq", "{}");
    RoutingAiProvider router =
        router(
            3,
            route("ollama", "gpt-oss:20b", 1, key1),
            route("ollama", "gpt-oss:20b", 2, key2),
            route("groq", "b", 1, otherModel));

    assertThat(router.complete(request(), reply -> reply.startsWith("{")).text()).isEqualTo("{}");
    assertThat(calls).containsExactly("ollama#1", "groq");
  }

  @Test
  void whenEveryReplyIsUnusableTheCallerIsToldSo() {
    Scripted one = new Scripted("one", "bad");
    Scripted two = new Scripted("two", "bad");
    RoutingAiProvider router = router(3, route("groq", "a", 1, one), route("ollama", "b", 1, two));

    assertThatThrownBy(() -> router.complete(request(), reply -> false))
        .isInstanceOfSatisfying(
            AiProviderException.class,
            failure -> assertThat(failure.getKind()).isEqualTo(Kind.INVALID_OUTPUT));
  }

  @Test
  void whenEverythingFailsTheRequestFailsThenFailsFastThenRecovers() throws Exception {
    Scripted groq = new Scripted("groq", fail(Kind.RATE_LIMITED));
    Scripted ollama = new Scripted("ollama", fail(Kind.UNAVAILABLE));
    RoutingAiProvider router =
        router(3, route("groq", "a", 1, groq), route("ollama", "b", 1, ollama));

    assertThatThrownBy(() -> router.complete(request()))
        .isInstanceOfSatisfying(
            AiProviderException.class,
            failure -> assertThat(failure.getKind()).isEqualTo(Kind.UNAVAILABLE));
    assertThat(calls).containsExactly("groq", "ollama");

    // Everything is resting: the next request fails at once and calls nobody.
    calls.clear();
    assertThatThrownBy(() -> router.complete(request())).isInstanceOf(AiProviderException.class);
    assertThat(calls).isEmpty();

    // The outage pause (30 s) ends first, then the rate-limit pause (60 s).
    clock.advance(Duration.ofSeconds(31));
    assertThat(router.complete(request()).text()).isEqualTo("ok from ollama");
    clock.advance(Duration.ofSeconds(30));
    assertThat(router.complete(request()).text()).isEqualTo("ok from groq");
  }

  @Test
  void noRequestCausesMoreCallsThanAllowed() {
    List<Route> routes = new ArrayList<>();
    for (int i = 1; i <= 6; i++) {
      routes.add(route("p" + i, "m", 1, new Scripted("p" + i, fail(Kind.UNAVAILABLE))));
    }
    RoutingAiProvider router =
        new RoutingAiProvider(routes, 3, Duration.ofSeconds(15), Duration.ofSeconds(25), clock);

    assertThatThrownBy(() -> router.complete(request())).isInstanceOf(AiProviderException.class);
    assertThat(calls).containsExactly("p1", "p2", "p3");
  }

  @Test
  void eachCallIsCappedAndTheTotalBudgetIsRespected() throws Exception {
    Scripted slow = new Scripted("slow", Duration.ofSeconds(15));
    Scripted slower = new Scripted("slower", Duration.ofSeconds(9));
    Scripted never = new Scripted("never");
    RoutingAiProvider router =
        router(3, route("a", "m", 1, slow), route("b", "m", 1, slower), route("c", "m", 1, never));

    assertThatThrownBy(() -> router.complete(request()))
        .isInstanceOfSatisfying(
            AiProviderException.class,
            failure -> assertThat(failure.getKind()).isEqualTo(Kind.TIMEOUT));
    // 20 s asked, 15 s per call allowed; the second call gets what is left of the 25 s budget.
    assertThat(slow.lastTimeout).isEqualTo(Duration.ofSeconds(15));
    assertThat(slower.lastTimeout).isEqualTo(Duration.ofSeconds(10));
    // One second is left: too little to start another call.
    assertThat(calls).containsExactly("slow", "slower");
  }
}
