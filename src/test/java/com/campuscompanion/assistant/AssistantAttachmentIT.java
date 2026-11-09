package com.campuscompanion.assistant;

import static com.campuscompanion.support.ApiTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.ai.provider.AiCompletion;
import com.campuscompanion.ai.provider.AiProvider;
import com.campuscompanion.ai.provider.AiProviderException;
import com.campuscompanion.ai.provider.AiRequest;
import com.campuscompanion.support.ApiIntegrationTest;
import com.campuscompanion.support.ApiTestClient;
import com.campuscompanion.support.TestDocuments;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.JsonNode;

/**
 * Pictures attached to the Quick Assistant, over real HTTP: a stand-in provider captures what
 * really reaches the model, so the tests can see that the picture itself (not its name) is in the
 * request, in order, and that a picture is only used by the student who attached it.
 */
@Import(AssistantAttachmentIT.Capturing.class)
class AssistantAttachmentIT extends ApiIntegrationTest {

  private static final String ATTACH = "/api/v1/assistant/attachments";
  private static final String CHAT = "/api/v1/assistant/chat";

  static final AtomicReference<AiRequest> LAST = new AtomicReference<>();

  @TestConfiguration
  static class Capturing {
    @Bean
    @Primary
    AiProvider capturingProvider() {
      return new AiProvider() {
        @Override
        public AiCompletion complete(AiRequest request) throws AiProviderException {
          LAST.set(request);
          return new AiCompletion("ok", 1, 2);
        }
      };
    }
  }

  private ApiTestClient student(String rollNo) throws Exception {
    accounts.student(rollNo, "AFL", 3, "A");
    return studentClient(rollNo);
  }

  private JsonNode attach(ApiTestClient me, String name, String type, byte[] file)
      throws Exception {
    HttpResponse<String> response = me.postFile(ATTACH, name, type, file);
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    return json(response);
  }

  private static String ask(String text, String style, String... ids) {
    String list =
        ids.length == 0 ? "" : ",\"attachmentIds\":[\"" + String.join("\",\"", ids) + "\"]";
    return "{\"message\":\"%s\",\"mode\":\"quick\",\"style\":\"%s\"%s}"
        .formatted(text, style, list);
  }

  private static byte[] notes() {
    return TestDocuments.jpeg(TestDocuments.printedPage(List.of("Stacks are LIFO.")));
  }

  private static byte[] chart() {
    return TestDocuments.png(TestDocuments.flowchart());
  }

  @Test
  void aPictureIsReadAndReallyReachesTheModel() throws Exception {
    ApiTestClient me = student("AFL00001");
    JsonNode attached = attach(me, "notes.jpg", "image/jpeg", notes());
    assertThat(attached.get("kind").asString()).isEqualTo("image");
    assertThat(attached.get("name").asString()).isEqualTo("notes.jpg");
    assertThat(attached.get("pictures").asInt()).isEqualTo(1);

    HttpResponse<String> response =
        me.post(CHAT, ask("What do my notes say?", "quick", attached.get("id").asString()));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    AiRequest sent = LAST.get();
    assertThat(sent.images()).hasSize(1);
    assertThat(sent.images().get(0).mimeType()).isEqualTo("image/jpeg");
    assertThat(sent.images().get(0).data()).startsWith((byte) 0xFF, (byte) 0xD8, (byte) 0xFF);
    assertThat(sent.userContent())
        .contains("Picture 1: \"notes.jpg\"")
        .contains("Student: [Attached: notes.jpg] What do my notes say?");
    assertThat(sent.systemInstructions()).contains("<student_documents>");
    JsonNode saved = json(response);
    assertThat(saved.get("question").get("content").asString())
        .isEqualTo("[Attached: notes.jpg] What do my notes say?");
    assertThat(saved.get("conversation").get("title").asString())
        .isEqualTo("What do my notes say?");
  }

  @Test
  void severalPicturesInOneQuestionAreAllSentInOrder() throws Exception {
    ApiTestClient me = student("AFL00002");
    String first = attach(me, "notes.jpg", "image/jpeg", notes()).get("id").asString();
    String second = attach(me, "chart.png", "image/png", chart()).get("id").asString();
    HttpResponse<String> response = me.post(CHAT, ask("Compare them", "explain", first, second));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    assertThat(LAST.get().images()).hasSize(2);
    String text = LAST.get().userContent();
    assertThat(text.indexOf("Picture 1: \"notes.jpg\""))
        .isLessThan(text.indexOf("Picture 2: \"chart.png\""));
    assertThat(json(response).get("question").get("content").asString())
        .isEqualTo("[Attached: notes.jpg, chart.png] Compare them");
  }

  @Test
  void aFollowUpKeepsUsingTheSamePictureAndABlankMessageIsAllowed() throws Exception {
    ApiTestClient me = student("AFL00003");
    String id = attach(me, "notes.jpg", "image/jpeg", notes()).get("id").asString();
    JsonNode first = json(me.post(CHAT, ask("What is this?", "quick", id)));
    long chat = first.get("conversation").get("id").asLong();
    String followUp =
        "{\"conversationId\":%d,\"message\":\"\",\"mode\":\"quick\",\"attachmentIds\":[\"%s\"]}"
            .formatted(chat, id);
    HttpResponse<String> response = me.post(CHAT, followUp);
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    assertThat(json(response).get("question").get("content").asString())
        .isEqualTo("[Attached: notes.jpg] Read the attached file and explain what it contains.");
    assertThat(LAST.get().images()).hasSize(1);
    assertThat(LAST.get().userContent()).contains("What is this?");
  }

  @Test
  void aQuestionWithPicturesCannotBeAnsweredAgainInTheOtherModeBecauseThePicturesAreNotKept()
      throws Exception {
    ApiTestClient me = student("AFL00004");
    String id = attach(me, "notes.jpg", "image/jpeg", notes()).get("id").asString();
    JsonNode first = json(me.post(CHAT, ask("Summarise it", "quick", id)));
    String again =
        "{\"conversationId\":%d,\"questionId\":%d,\"mode\":\"quick\",\"style\":\"explain\"}"
            .formatted(
                first.get("conversation").get("id").asLong(),
                first.get("question").get("id").asLong());
    assertProblem(me.post(CHAT, again), 409, "CONFLICT");
  }

  @Test
  void aPictureBelongsOnlyToTheStudentWhoAttachedIt() throws Exception {
    ApiTestClient owner = student("AFL00005");
    ApiTestClient other = student("AFL00006");
    String id = attach(owner, "notes.jpg", "image/jpeg", notes()).get("id").asString();
    assertProblem(other.post(CHAT, ask("What does it say?", "quick", id)), 409, "CONFLICT");
    assertThat(other.delete(ATTACH + "/" + id).statusCode()).isEqualTo(204);
    assertThat(owner.post(CHAT, ask("Still there?", "quick", id)).statusCode()).isEqualTo(200);
    assertThat(owner.delete(ATTACH + "/" + id).statusCode()).isEqualTo(204);
    assertProblem(owner.post(CHAT, ask("Gone?", "quick", id)), 409, "CONFLICT");
    assertThat(owner.delete(ATTACH + "/" + id).statusCode()).isEqualTo(204);
  }

  @Test
  void aFileThatIsNotAPictureIsRefusedWithAReasonNotShownAsReady() throws Exception {
    ApiTestClient me = student("AFL00007");
    assertProblem(
        me.postFile(
            ATTACH, "notes.txt", "text/plain", "plain text".getBytes(StandardCharsets.UTF_8)),
        415,
        "UPLOAD_INVALID_TYPE");
    assertProblem(
        me.postFile(
            ATTACH, "paper.pdf", "application/pdf", "%PDF-1.4 x".getBytes(StandardCharsets.UTF_8)),
        415,
        "UPLOAD_INVALID_TYPE");
    assertProblem(
        me.postFile(ATTACH, "paper.docx", "x", new byte[] {'P', 'K', 3, 4, 1, 2, 3}),
        415,
        "UPLOAD_INVALID_TYPE");
    assertProblem(
        me.postFile(ATTACH, "empty.jpg", "image/jpeg", new byte[0]), 415, "UPLOAD_INVALID_TYPE");
    byte[] big = new byte[6 * 1024 * 1024];
    big[0] = (byte) 0xFF;
    assertProblem(me.postFile(ATTACH, "big.jpg", "image/jpeg", big), 413, "UPLOAD_TOO_LARGE");
    assertProblem(
        me.post(CHAT, ask("Use it", "quick", "0123456789abcdef0123456789abcdef")), 409, "CONFLICT");
  }

  @Test
  void aStudentMayKeepOnlyAFewPicturesAndAQuestionMayUseOnlyFour() throws Exception {
    ApiTestClient me = student("AFL00008");
    java.util.ArrayList<String> ids = new java.util.ArrayList<>();
    for (int i = 0; i < 6; i++) {
      ids.add(attach(me, "p" + i + ".jpg", "image/jpeg", notes()).get("id").asString());
    }
    assertProblem(me.postFile(ATTACH, "seventh.jpg", "image/jpeg", notes()), 409, "CONFLICT");
    assertProblem(
        me.post(CHAT, ask("All of them", "quick", ids.subList(0, 5).toArray(String[]::new))),
        400,
        "VALIDATION_FAILED");
    assertThat(
            me.post(CHAT, ask("Four", "quick", ids.subList(0, 4).toArray(String[]::new)))
                .statusCode())
        .isEqualTo(200);
  }

  @Test
  void onlyALoggedInStudentCanAttach() throws Exception {
    assertProblem(visitor().postFile(ATTACH, "p.jpg", "image/jpeg", notes()), 401, "AUTH_REQUIRED");
    ApiTestClient admin = adminClient("attach-admin@university.example");
    assertProblem(admin.postFile(ATTACH, "p.jpg", "image/jpeg", notes()), 403, "ACCESS_DENIED");
    assertProblem(visitor().delete(ATTACH + "/abc"), 401, "AUTH_REQUIRED");
  }
}
