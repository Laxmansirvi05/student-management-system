package com.campuscompanion.ai;

import static com.campuscompanion.support.ApiTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.ai.provider.AiCompletion;
import com.campuscompanion.ai.provider.AiProvider;
import com.campuscompanion.ai.provider.AiProviderException;
import com.campuscompanion.ai.provider.AiRequest;
import com.campuscompanion.ai.provider.MockAiProvider;
import com.campuscompanion.ai.provider.OpenAiCompatibleAiProvider;
import com.campuscompanion.ai.provider.RoutingAiProvider;
import com.campuscompanion.ai.provider.RoutingAiProvider.Route;
import com.campuscompanion.support.ApiIntegrationTest;
import com.campuscompanion.support.ApiTestClient;
import com.campuscompanion.support.MutableClock;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Both assistants end to end through the router and the real HTTP adapter (docs/12 section 2b): two
 * fake providers on this machine stand in for the real ones, so no real AI is called. The first
 * provider is made to fail in each way the router handles; the second answers like the mock.
 */
@ExtendWith(OutputCaptureExtension.class)
@Import(AiRouterWorkflowIT.FakeProviders.class)
class AiRouterWorkflowIT extends ApiIntegrationTest {

  private static final String EXPLAIN = "/api/v1/ai/explain";
  private static final String SUMMARIZE = "/api/v1/ai/summarize";
  private static final String QUIZ = "/api/v1/ai/generate-quiz";
  private static final String USAGE = "/api/v1/ai/usage/me";
  private static final String CHAT = "/api/v1/assistant/chat";
  private static final String CONVERSATIONS = "/api/v1/assistant/conversations";

  /** Made-up keys that would be easy to spot in a response or a log. */
  static final String FIRST_KEY = "gsk_FIRST_made_up_key_0001";

  static final String SECOND_KEY = "sk-or-v1-SECOND_made_up_key_0002";

  static final MutableClock CLOCK = new MutableClock(Instant.parse("2026-10-23T09:00:00Z"));
  static final FakeProvider FIRST = new FakeProvider();
  static final FakeProvider SECOND = new FakeProvider();

  /** The router the application uses in this test, over the two fake providers. */
  @TestConfiguration
  static class FakeProviders {
    @Bean
    @Primary
    AiProvider routingAiProvider() {
      return new RoutingAiProvider(
          List.of(
              new Route(
                  "groq",
                  "first-model",
                  1,
                  new OpenAiCompatibleAiProvider(
                      FIRST.base(), FIRST_KEY, "first-model", Duration.ofSeconds(2))),
              new Route(
                  "openrouter",
                  "second-model",
                  1,
                  new OpenAiCompatibleAiProvider(
                      SECOND.base(), SECOND_KEY, "second-model", Duration.ofSeconds(2)))),
          3,
          Duration.ofSeconds(10),
          Duration.ofSeconds(25),
          CLOCK);
    }
  }

  /**
   * A provider on this machine. In "answer" mode it replies like the mock would to the same prompt;
   * otherwise it fails with the HTTP status set, or replies with the text set.
   */
  static final class FakeProvider {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final MockAiProvider MOCK = new MockAiProvider();
    private static final Map<String, String> TEMPLATES =
        Map.of(
            "explain-v1", firstLine("prompts/explain-v1.txt"),
            "summarize-v1", firstLine("prompts/summarize-v1.txt"),
            "quiz-v1", firstLine("prompts/quiz-v1.txt"),
            "assistant-quick-v3", firstLine("prompts/assistant-quick-v3.txt"),
            "assistant-explain-v2", firstLine("prompts/assistant-explain-v2.txt"));

    private final HttpServer server;
    final AtomicInteger calls = new AtomicInteger();
    volatile int failWith = 0;
    volatile String replyWith = null;

    FakeProvider() {
      try {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      server.createContext(
          "/",
          exchange -> {
            calls.incrementAndGet();
            JsonNode sent =
                JSON.readTree(
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String body;
            int status = failWith == 0 ? 200 : failWith;
            if (failWith != 0) {
              body = "{\"error\":{\"message\":\"failing on purpose\"}}";
            } else {
              String text = replyWith != null ? replyWith : mockReply(sent);
              body =
                  JSON.createObjectNode()
                      .set(
                          "choices",
                          JSON.createArrayNode()
                              .add(
                                  JSON.createObjectNode()
                                      .set(
                                          "message",
                                          JSON.createObjectNode()
                                              .put("role", "assistant")
                                              .put("content", text))))
                      .toString();
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
          });
      server.start();
    }

    String base() {
      return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    void answer() {
      failWith = 0;
      replyWith = null;
    }

    private static String mockReply(JsonNode sent) {
      String system = sent.get("messages").get(0).get("content").asString();
      String user = sent.get("messages").get(1).get("content").asString();
      String version =
          TEMPLATES.entrySet().stream()
              .filter(template -> system.startsWith(template.getValue()))
              .map(Map.Entry::getKey)
              .findFirst()
              .orElseThrow();
      try {
        AiCompletion reply =
            MOCK.complete(new AiRequest(version, system, user, 1200, Duration.ofSeconds(5)));
        return reply.text();
      } catch (AiProviderException e) {
        throw new IllegalStateException(e);
      }
    }

    private static String firstLine(String path) {
      try {
        String text = new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        return text.lines().findFirst().orElseThrow();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  @BeforeEach
  void resetProviders() {
    // An hour later, every pause from an earlier test is over.
    CLOCK.advance(Duration.ofHours(1));
    FIRST.answer();
    SECOND.answer();
    FIRST.calls.set(0);
    SECOND.calls.set(0);
  }

  private ApiTestClient student(String rollNo) throws Exception {
    accounts.student(rollNo, "RTR", 3, "A");
    return studentClient(rollNo);
  }

  private static int used(ApiTestClient student) throws Exception {
    return json(student.get(USAGE)).get("usedToday").asInt();
  }

  @Test
  void theFirstHealthyProviderAnswersAndTheSecondIsNotCalled() throws Exception {
    ApiTestClient me = student("23RTR001");
    HttpResponse<String> explained =
        me.post(EXPLAIN, "{\"topic\":\"binary search trees\",\"answerFormat\":\"SIMPLE\"}");
    assertThat(explained.statusCode()).as(explained.body()).isEqualTo(200);
    assertThat(json(explained).get("explanation").asString()).contains("binary search trees");
    assertThat(FIRST.calls.get()).isEqualTo(1);
    assertThat(SECOND.calls.get()).isZero();
  }

  @Test
  void aRateLimitedProviderIsPassedOverForEveryFeatureAndRestsUntilItsPauseEnds() throws Exception {
    ApiTestClient me = student("23RTR002");
    FIRST.failWith = 429;

    HttpResponse<String> explained =
        me.post(EXPLAIN, "{\"topic\":\"deadlock\",\"answerFormat\":\"SIMPLE\"}");
    assertThat(explained.statusCode()).as(explained.body()).isEqualTo(200);
    HttpResponse<String> summary =
        me.post(
            SUMMARIZE,
            "{\"text\":\"%s\",\"style\":\"SHORT\"}"
                .formatted("A deadlock needs four conditions at once. ".repeat(8)));
    assertThat(summary.statusCode()).as(summary.body()).isEqualTo(200);
    HttpResponse<String> quiz =
        me.post(QUIZ, "{\"topic\":\"TCP and UDP\",\"questionType\":\"MCQ\",\"count\":3}");
    assertThat(quiz.statusCode()).as(quiz.body()).isEqualTo(200);
    assertThat(json(quiz).get("questions")).hasSize(3);

    // Asked once, then left alone for the whole minute: the other two went straight to SECOND.
    assertThat(FIRST.calls.get()).isEqualTo(1);
    assertThat(SECOND.calls.get()).isEqualTo(3);
    // One usage row per student request, however many providers were asked.
    assertThat(used(me)).isEqualTo(3);

    FIRST.answer();
    CLOCK.advance(Duration.ofSeconds(61));
    me.post(EXPLAIN, "{\"topic\":\"paging\",\"answerFormat\":\"SIMPLE\"}");
    assertThat(FIRST.calls.get()).isEqualTo(2);
  }

  @Test
  void aReplyThatFailsTheParserOrTheQuestionChecksIsReplacedByTheNextProvidersReply()
      throws Exception {
    ApiTestClient me = student("23RTR003");
    FIRST.replyWith = "Sure! Here is some text that is not the JSON the rules ask for.";
    HttpResponse<String> explained =
        me.post(EXPLAIN, "{\"topic\":\"hashing\",\"answerFormat\":\"SIMPLE\"}");
    assertThat(explained.statusCode()).as(explained.body()).isEqualTo(200);

    // Questions without options or answers do not pass GeneratedQuestionValidator.
    FIRST.replyWith = "{\"questions\":[{\"question\":\"?\"}]}";
    HttpResponse<String> quiz =
        me.post(QUIZ, "{\"topic\":\"stacks\",\"questionType\":\"MCQ\",\"count\":2}");
    assertThat(quiz.statusCode()).as(quiz.body()).isEqualTo(200);
    for (JsonNode question : json(quiz).get("questions")) {
      assertThat(question.get("options")).hasSize(4);
    }
    assertThat(FIRST.calls.get()).isEqualTo(2);
    assertThat(SECOND.calls.get()).isEqualTo(2);
  }

  @Test
  void theQuickAssistantFallsBackKeepsItsHistoryAndKeepsItPrivate() throws Exception {
    ApiTestClient me = student("23RTR004");
    ApiTestClient other = student("23RTR005");
    FIRST.failWith = 503;

    HttpResponse<String> answered =
        me.post(
            CHAT, "{\"conversationId\":null,\"message\":\"What is a stack?\",\"mode\":\"quick\"}");
    assertThat(answered.statusCode()).as(answered.body()).isEqualTo(200);
    long id = json(answered).get("conversation").get("id").asLong();
    assertThat(json(answered).get("reply").get("content").asString()).isNotBlank();

    JsonNode messages = json(me.get(CONVERSATIONS + "/" + id + "/messages")).get("messages");
    assertThat(messages).hasSize(2);
    assertProblem(other.get(CONVERSATIONS + "/" + id + "/messages"), 404, "NOT_FOUND");
    assertThat(me.delete(CONVERSATIONS + "/" + id).statusCode()).isEqualTo(204);
  }

  @Test
  void whenEveryProviderFailsTheStudentIsToldAndNoRequestIsUsedUpThenItRecovers() throws Exception {
    ApiTestClient me = student("23RTR006");
    FIRST.failWith = 500;
    SECOND.failWith = 502;

    assertProblem(
        me.post(EXPLAIN, "{\"topic\":\"queues\",\"answerFormat\":\"SIMPLE\"}"),
        503,
        "AI_UNAVAILABLE");
    assertProblem(
        me.post(CHAT, "{\"conversationId\":null,\"message\":\"Hello?\",\"mode\":\"quick\"}"),
        503,
        "AI_UNAVAILABLE");
    // The second request found both providers resting and called neither.
    assertThat(FIRST.calls.get()).isEqualTo(1);
    assertThat(SECOND.calls.get()).isEqualTo(1);
    assertThat(used(me)).isZero();
    assertThat(json(me.get(CONVERSATIONS)).get("conversations")).isEmpty();

    FIRST.answer();
    SECOND.answer();
    CLOCK.advance(Duration.ofSeconds(31));
    assertThat(me.post(EXPLAIN, "{\"topic\":\"queues\",\"answerFormat\":\"SIMPLE\"}").statusCode())
        .isEqualTo(200);
  }

  @Test
  void whenEveryReplyIsUnusableTheStudentGetsTheInvalidAnswerError() throws Exception {
    ApiTestClient me = student("23RTR007");
    FIRST.replyWith = "not json";
    SECOND.replyWith = "still not json";
    assertProblem(
        me.post(EXPLAIN, "{\"topic\":\"graphs\",\"answerFormat\":\"SIMPLE\"}"),
        502,
        "AI_INVALID_RESPONSE");
  }

  @Test
  void noKeyEverReachesAResponseOrTheLog(CapturedOutput output) throws Exception {
    ApiTestClient me = student("23RTR008");
    FIRST.failWith = 401;
    HttpResponse<String> answered =
        me.post(EXPLAIN, "{\"topic\":\"recursion\",\"answerFormat\":\"SIMPLE\"}");
    SECOND.failWith = 500;
    HttpResponse<String> failed =
        me.post(CHAT, "{\"conversationId\":null,\"message\":\"Hi\",\"mode\":\"quick\"}");

    for (String text :
        List.of(answered.body(), failed.body(), json(me.get(USAGE)).toString(), output.getAll())) {
      assertThat(text).doesNotContain(FIRST_KEY).doesNotContain(SECOND_KEY);
    }
    // The log names the route and the kind of failure only.
    assertThat(output.getAll()).contains("groq:first-model (key 1) failed: AUTH_FAILED");
  }
}
