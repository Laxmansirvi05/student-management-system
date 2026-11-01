package com.campuscompanion.assistant;

import static com.campuscompanion.support.ApiTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.ai.provider.AiCompletion;
import com.campuscompanion.ai.provider.AiProvider;
import com.campuscompanion.ai.provider.AiProviderException;
import com.campuscompanion.ai.provider.AiRequest;
import com.campuscompanion.support.ApiIntegrationTest;
import com.campuscompanion.support.ApiTestClient;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.JsonNode;

/**
 * What actually reaches the model (Quick Assistant V2): the request is captured by a stand-in
 * provider, so the test can see the instructions of each answer mode and that a picture is truly
 * sent with the question, not only shown on the page.
 */
@Import(AssistantVisionIT.Capturing.class)
class AssistantVisionIT extends ApiIntegrationTest {

  static final AtomicReference<AiRequest> LAST = new AtomicReference<>();
  static volatile boolean failing = false;

  @TestConfiguration
  static class Capturing {
    @Bean
    @Primary
    AiProvider capturingProvider() {
      return new AiProvider() {
        @Override
        public AiCompletion complete(AiRequest request) throws AiProviderException {
          LAST.set(request);
          if (failing) {
            throw new AiProviderException(AiProviderException.Kind.UNAVAILABLE);
          }
          return new AiCompletion("ok", 1, 2);
        }
      };
    }
  }

  private ApiTestClient student(String rollNo) throws Exception {
    accounts.student(rollNo, "AVI", 3, "A");
    return studentClient(rollNo);
  }

  private static String styled(String text, String style) {
    return "{\"message\":\"%s\",\"mode\":\"quick\",\"style\":\"%s\"}".formatted(text, style);
  }

  @Test
  void eachAnswerModeSendsItsOwnInstructions() throws Exception {
    ApiTestClient me = student("AVI00001");

    me.post("/api/v1/assistant/chat", styled("What is a queue?", "quick"));
    AiRequest quick = LAST.get();
    assertThat(quick.promptVersion()).isEqualTo("assistant-quick-v3");
    assertThat(quick.systemInstructions()).contains("Quick Answer").doesNotContain("flowchart");
    assertThat(quick.images()).isEmpty();

    me.post("/api/v1/assistant/chat", styled("Explain a queue", "explain"));
    AiRequest explain = LAST.get();
    assertThat(explain.promptVersion()).isEqualTo("assistant-explain-v2");
    assertThat(explain.systemInstructions())
        .contains("Explanatory Answer", "flowchart", "mindmap", "chart", "steps");
    assertThat(explain.maxOutputTokens()).isGreaterThanOrEqualTo(AssistantService.EXPLAIN_TOKENS);

    // No style at all means Quick Answer.
    me.post("/api/v1/assistant/chat", "{\"message\":\"Hi\",\"mode\":\"quick\"}");
    assertThat(LAST.get().promptVersion()).isEqualTo("assistant-quick-v3");
  }

  @Test
  void aPictureIsReallySentToTheModelAsARedrawnJpeg() throws Exception {
    ApiTestClient me = student("AVI00002");
    ByteArrayOutputStream png = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2400, 1200, BufferedImage.TYPE_INT_RGB), "png", png);

    HttpResponse<String> response =
        me.postMultipart(
            "/api/v1/assistant/chat",
            styled("What does this show?", "explain"),
            "image",
            "capture.png",
            "image/png",
            png.toByteArray());

    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    assertThat(json(response).get("reply").get("content").asString()).isEqualTo("ok");
    AiRequest sent = LAST.get();
    assertThat(sent.images()).hasSize(1);
    assertThat(sent.images().get(0).mimeType()).isEqualTo("image/jpeg");
    assertThat(sent.images().get(0).data()).startsWith((byte) 0xFF, (byte) 0xD8, (byte) 0xFF);
    // Redrawn, not the file as it came: smaller than the 2400 pixels uploaded.
    BufferedImage seen =
        ImageIO.read(new java.io.ByteArrayInputStream(sent.images().get(0).data()));
    assertThat(seen.getWidth()).isLessThanOrEqualTo(1568);
    assertThat(sent.userContent()).contains("[Image] What does this show?");
  }

  @Test
  void aFailedSecondModeSavesNothingAndTheQuestionStaysAnswerable() throws Exception {
    ApiTestClient me = student("AVI00003");
    JsonNode first = json(me.post("/api/v1/assistant/chat", styled("What is a list?", "quick")));
    long chat = first.get("conversation").get("id").asLong();
    long question = first.get("question").get("id").asLong();
    String again =
        "{\"conversationId\":%d,\"questionId\":%d,\"mode\":\"quick\",\"style\":\"explain\"}"
            .formatted(chat, question);

    failing = true;
    try {
      assertProblem(me.post("/api/v1/assistant/chat", again), 503, "AI_UNAVAILABLE");
    } finally {
      failing = false;
    }
    assertThat(
            json(me.get("/api/v1/assistant/conversations/" + chat + "/messages")).get("messages"))
        .hasSize(2);
    assertThat(json(me.get("/api/v1/ai/usage/me")).get("usedToday").asInt()).isEqualTo(1);

    // A retry writes it once, from the chat before that question, in the Explanatory mode.
    assertThat(me.post("/api/v1/assistant/chat", again).statusCode()).isEqualTo(200);
    assertThat(LAST.get().promptVersion()).isEqualTo("assistant-explain-v2");
    assertThat(LAST.get().userContent()).contains("What is a list?");
    assertThat(
            json(me.get("/api/v1/assistant/conversations/" + chat + "/messages")).get("messages"))
        .hasSize(3);
  }

  @Test
  void whenAQuestionHasTwoAnswersTheModelGetsOnlyTheNewestOfEach() throws Exception {
    ApiTestClient me = student("AVI00004");
    JsonNode first = json(me.post("/api/v1/assistant/chat", styled("First question", "quick")));
    long chat = first.get("conversation").get("id").asLong();
    long question = first.get("question").get("id").asLong();
    me.post(
        "/api/v1/assistant/chat",
        "{\"conversationId\":%d,\"questionId\":%d,\"mode\":\"quick\",\"style\":\"explain\"}"
            .formatted(chat, question));

    me.post(
        "/api/v1/assistant/chat",
        "{\"conversationId\":%d,\"message\":\"Second question\",\"mode\":\"quick\"}"
            .formatted(chat));

    String conversation = LAST.get().userContent();
    assertThat(conversation.split("Student: First question", -1)).hasSize(2);
    assertThat(conversation.split("Assistant: ", -1).length - 1).isEqualTo(1);
    assertThat(conversation).contains("Student: Second question");
  }
}
