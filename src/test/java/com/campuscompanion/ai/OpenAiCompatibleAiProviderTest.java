package com.campuscompanion.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campuscompanion.ai.provider.AiProviderException;
import com.campuscompanion.ai.provider.AiProviderException.Kind;
import com.campuscompanion.ai.provider.AiRequest;
import com.campuscompanion.ai.provider.OpenAiCompatibleAiProvider;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The adapter for Groq, OpenRouter and Ollama's cloud, against a small server of this test's own:
 * what it sends, how it reads the answer, and how each failure is classified for the router. The
 * key never appears in a failure.
 */
class OpenAiCompatibleAiProviderTest {

  private static final String KEY = "gsk_made_up_test_key_123456";
  private static final ObjectMapper JSON = new ObjectMapper();

  private HttpServer server;
  private final AtomicReference<String> path = new AtomicReference<>();
  private final AtomicReference<String> authorization = new AtomicReference<>();
  private final AtomicReference<String> userAgent = new AtomicReference<>();
  private final AtomicReference<String> body = new AtomicReference<>();
  private volatile int status = 200;
  private volatile String answer = "{}";
  private volatile String retryAfter = null;
  private volatile long delayMs = 0;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          path.set(exchange.getRequestURI().getPath());
          authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
          body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          try {
            Thread.sleep(delayMs);
          } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
          }
          byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          if (retryAfter != null) {
            exchange.getResponseHeaders().add("Retry-After", retryAfter);
          }
          exchange.sendResponseHeaders(status, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  private OpenAiCompatibleAiProvider provider() {
    String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/openai/v1/";
    return new OpenAiCompatibleAiProvider(base, KEY, "openai/gpt-oss-120b", Duration.ofSeconds(2));
  }

  private static AiRequest request(Duration timeout) {
    return new AiRequest("explain-v1", "Follow the rules.", "<topic>stacks</topic>", 900, timeout);
  }

  private AiProviderException failureFor(int code) {
    status = code;
    try {
      provider().complete(request(Duration.ofSeconds(5)));
    } catch (AiProviderException failure) {
      return failure;
    }
    throw new AssertionError("expected a failure for " + code);
  }

  @Test
  void itSendsTheRulesAndTheTextSeparatelyWithTheKeyOnlyInTheHeader() throws Exception {
    answer =
        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"A stack is LIFO.\"}}]}";

    var completion = provider().complete(request(Duration.ofSeconds(5)));

    assertThat(completion.text()).isEqualTo("A stack is LIFO.");
    assertThat(path.get()).isEqualTo("/openai/v1/chat/completions");
    assertThat(authorization.get()).isEqualTo("Bearer " + KEY);
    assertThat(userAgent.get()).startsWith("CampusCompanion/");
    JsonNode sent = JSON.readTree(body.get());
    assertThat(sent.get("model").asString()).isEqualTo("openai/gpt-oss-120b");
    assertThat(sent.get("max_tokens").asInt()).isEqualTo(900);
    assertThat(sent.get("messages").get(0).get("role").asString()).isEqualTo("system");
    assertThat(sent.get("messages").get(0).get("content").asString())
        .isEqualTo("Follow the rules.");
    assertThat(sent.get("messages").get(1).get("role").asString()).isEqualTo("user");
    assertThat(sent.get("messages").get(1).get("content").asString())
        .isEqualTo("<topic>stacks</topic>");
    assertThat(body.get()).doesNotContain(KEY);
  }

  @Test
  void anAnswerWithoutTextIsEmptyForTheCallerToRefuse() throws Exception {
    answer = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null}}]}";
    assertThat(provider().complete(request(Duration.ofSeconds(5))).text()).isEmpty();
  }

  @Test
  void eachFailureIsClassifiedForTheRouterWithoutTheProvidersWordsOrTheKey() {
    answer = "{\"error\":{\"message\":\"Invalid API Key " + KEY + " for the student's text\"}}";
    int[][] cases = {
      {401, 4}, {403, 4}, {404, 5}, {400, 3}, {422, 3}, {408, 0}, {429, 1}, {500, 2}, {502, 2},
      {503, 2}
    };
    for (int[] pair : cases) {
      AiProviderException failure = failureFor(pair[0]);
      assertThat(failure.getKind()).as("status %s", pair[0]).isEqualTo(Kind.values()[pair[1]]);
      assertThat(failure.getMessage()).doesNotContain(KEY).doesNotContain("student's text");
    }
  }

  @Test
  void aRateLimitCarriesTheProvidersRetryAfterCappedAtTenMinutes() {
    retryAfter = "37";
    assertThat(failureFor(429).getRetryAfter()).isEqualTo(Duration.ofSeconds(37));
    retryAfter = "86400";
    assertThat(failureFor(429).getRetryAfter()).isEqualTo(Duration.ofMinutes(10));
    retryAfter = "Wed, 21 Oct 2026 07:28:00 GMT";
    assertThat(failureFor(429).getRetryAfter()).isNull();
  }

  @Test
  void anErrorInsideASuccessfulAnswerOrAnAnswerThatIsNotJsonIsUnavailable() {
    for (String broken : new String[] {"{\"error\":{\"message\":\"upstream\"}}", "<html>oops"}) {
      answer = broken;
      assertThatThrownBy(() -> provider().complete(request(Duration.ofSeconds(5))))
          .isInstanceOfSatisfying(
              AiProviderException.class,
              failure -> assertThat(failure.getKind()).isEqualTo(Kind.UNAVAILABLE));
    }
  }

  @Test
  void aSlowAnswerIsATimeoutAndAMissingServerIsUnavailable() {
    delayMs = 1500;
    assertThatThrownBy(() -> provider().complete(request(Duration.ofMillis(300))))
        .isInstanceOfSatisfying(
            AiProviderException.class,
            failure -> assertThat(failure.getKind()).isEqualTo(Kind.TIMEOUT));
    delayMs = 0;
    server.stop(0);
    assertThatThrownBy(() -> provider().complete(request(Duration.ofSeconds(2))))
        .isInstanceOfSatisfying(
            AiProviderException.class,
            failure -> assertThat(failure.getKind()).isEqualTo(Kind.UNAVAILABLE));
  }
}
