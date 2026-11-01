package com.campuscompanion.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campuscompanion.ai.provider.AiCompletion;
import com.campuscompanion.ai.provider.AiImage;
import com.campuscompanion.ai.provider.AiProviderException;
import com.campuscompanion.ai.provider.AiRequest;
import com.campuscompanion.ai.provider.GeminiAiProvider;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The Gemini adapter against a small server of this test's own: what it sends, how it reads the
 * answer, and how each failure becomes a kind. No test reaches the real provider, and the key used
 * here is a made-up value.
 */
class GeminiAiProviderTest {

  private static final String KEY = "test-key-not-real";
  private static final ObjectMapper JSON = new ObjectMapper();

  private HttpServer server;
  private final AtomicReference<String> path = new AtomicReference<>();
  private final AtomicReference<String> keyHeader = new AtomicReference<>();
  private final AtomicReference<String> body = new AtomicReference<>();
  private volatile int status = 200;
  private volatile String answer = "{}";
  private volatile long delayMs = 0;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          path.set(exchange.getRequestURI().getPath());
          keyHeader.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
          body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          try {
            Thread.sleep(delayMs);
          } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
          }
          byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
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

  private GeminiAiProvider provider(String model) {
    String base = "http://127.0.0.1:" + server.getAddress().getPort();
    return GeminiAiProvider.forTest(KEY, model, Duration.ofSeconds(2), base);
  }

  private static AiRequest request(Duration timeout) {
    return new AiRequest(
        "assistant-quick-v1", "Be brief.", "Student: What is a stack?", 1500, timeout);
  }

  @Test
  void itSendsTheInstructionsAndTheTextAndReadsTheAnswer() throws Exception {
    answer =
        "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"A stack is last in, first out.\"}]}}]}";

    AiCompletion completion =
        provider("gemini-test-model").complete(request(Duration.ofSeconds(5)));

    assertThat(completion.text()).isEqualTo("A stack is last in, first out.");
    assertThat(path.get()).isEqualTo("/v1beta/models/gemini-test-model:generateContent");
    assertThat(keyHeader.get()).isEqualTo(KEY);
    JsonNode sent = JSON.readTree(body.get());
    assertThat(sent.path("systemInstruction").path("parts").path(0).path("text").asString())
        .isEqualTo("Be brief.");
    assertThat(sent.path("contents").path(0).path("role").asString()).isEqualTo("user");
    assertThat(sent.path("contents").path(0).path("parts").path(0).path("text").asString())
        .isEqualTo("Student: What is a stack?");
    assertThat(sent.path("generationConfig").path("maxOutputTokens").asInt()).isEqualTo(1500);
    // The key is never part of the body.
    assertThat(body.get()).doesNotContain(KEY);
  }

  @Test
  void aBlankModelUsesTheDefaultAndPartsAreJoinedWithoutTheModelsThoughts() throws Exception {
    answer =
        "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"private reasoning\",\"thought\":true},"
            + "{\"text\":\"One. \"},{\"text\":\"Two.\"}]}}]}";

    AiCompletion completion = provider("  ").complete(request(Duration.ofSeconds(5)));

    assertThat(completion.text()).isEqualTo("One. Two.");
    assertThat(path.get())
        .isEqualTo("/v1beta/models/" + GeminiAiProvider.DEFAULT_MODEL + ":generateContent");
  }

  @Test
  void anAnswerWithoutTextIsEmptyForTheCallerToRefuse() throws Exception {
    answer = "{\"candidates\":[{\"finishReason\":\"SAFETY\"}]}";
    assertThat(provider("m").complete(request(Duration.ofSeconds(5))).text()).isEmpty();
    answer = "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}";
    assertThat(provider("m").complete(request(Duration.ofSeconds(5))).text()).isEmpty();
  }

  @Test
  void everyFailureBecomesItsKindWithoutTheProvidersWords() {
    answer = "{\"error\":{\"message\":\"repeats the student's text here\"}}";
    for (int[] pair :
        new int[][] {{429, 1}, {500, 2}, {503, 2}, {400, 3}, {401, 4}, {403, 4}, {404, 5}}) {
      status = pair[0];
      AiProviderException.Kind expected = AiProviderException.Kind.values()[pair[1]];
      assertThatThrownBy(() -> provider("m").complete(request(Duration.ofSeconds(5))))
          .isInstanceOfSatisfying(
              AiProviderException.class,
              failure -> {
                assertThat(failure.getKind()).isEqualTo(expected);
                assertThat(failure.getMessage()).doesNotContain("student's text");
              });
    }
  }

  @Test
  void aSlowAnswerIsATimeout() {
    delayMs = 1500;
    assertThatThrownBy(() -> provider("m").complete(request(Duration.ofMillis(300))))
        .isInstanceOfSatisfying(
            AiProviderException.class,
            failure -> assertThat(failure.getKind()).isEqualTo(AiProviderException.Kind.TIMEOUT));
  }

  @Test
  void anAnswerThatIsNotJsonIsUnavailable() {
    answer = "<html>gateway</html>";
    assertThatThrownBy(() -> provider("m").complete(request(Duration.ofSeconds(5))))
        .isInstanceOfSatisfying(
            AiProviderException.class,
            failure ->
                assertThat(failure.getKind()).isEqualTo(AiProviderException.Kind.UNAVAILABLE));
  }

  @Test
  void aServerThatIsNotThereIsUnavailable() {
    server.stop(0);
    assertThatThrownBy(() -> provider("m").complete(request(Duration.ofSeconds(2))))
        .isInstanceOfSatisfying(
            AiProviderException.class,
            failure ->
                assertThat(failure.getKind()).isEqualTo(AiProviderException.Kind.UNAVAILABLE));
  }

  @Test
  void aQuestionWithAPictureSendsItAsInlineData() throws Exception {
    answer = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"A diagram.\"}]}}]}";
    byte[] bytes = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 9, 8, 7};

    provider("gemini-test-model")
        .complete(
            request(Duration.ofSeconds(5)).withImages(List.of(new AiImage("image/jpeg", bytes))));

    JsonNode parts = JSON.readTree(body.get()).path("contents").path(0).path("parts");
    assertThat(parts.path(0).path("text").asString()).isEqualTo("Student: What is a stack?");
    assertThat(parts.path(1).path("inlineData").path("mimeType").asString())
        .isEqualTo("image/jpeg");
    assertThat(parts.path(1).path("inlineData").path("data").asString())
        .isEqualTo(java.util.Base64.getEncoder().encodeToString(bytes));
  }
}
