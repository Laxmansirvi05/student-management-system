package com.campuscompanion.assistant;

import static com.campuscompanion.support.ApiTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.support.ApiIntegrationTest;
import com.campuscompanion.support.ApiTestClient;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * The robot's Quick Assistant over real HTTP, with the mock provider (ADR 0014). A student may make
 * five AI requests a minute, so each test uses its own student and stays under that.
 */
class AssistantIT extends ApiIntegrationTest {

  private static final String CHAT = "/api/v1/assistant/chat";
  private static final String CONVERSATIONS = "/api/v1/assistant/conversations";

  private ApiTestClient student(String rollNo) throws Exception {
    accounts.student(rollNo, "AST", 3, "A");
    return studentClient(rollNo);
  }

  private static String message(Long conversationId, String text) {
    return "{\"conversationId\":%s,\"message\":\"%s\",\"mode\":\"quick\"}"
        .formatted(conversationId, text);
  }

  private JsonNode ask(ApiTestClient who, Long conversationId, String text) throws Exception {
    HttpResponse<String> response = who.post(CHAT, message(conversationId, text));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    return json(response);
  }

  @Test
  void onlyALoggedInStudentCanChat() throws Exception {
    assertProblem(visitor().post(CHAT, message(null, "hi")), 401, "AUTH_REQUIRED");
    ApiTestClient admin = adminClient("assistant-admin@university.example");
    assertProblem(admin.post(CHAT, message(null, "hi")), 403, "ACCESS_DENIED");
    assertProblem(admin.get(CONVERSATIONS), 403, "ACCESS_DENIED");
  }

  @Test
  void aQuestionStartsAChatAndTheFollowUpContinuesIt() throws Exception {
    ApiTestClient me = student("AST00001");
    JsonNode first = ask(me, null, "What is a binary tree?");
    long id = first.get("conversation").get("id").asLong();
    assertThat(first.get("conversation").get("source").asString()).isEqualTo("quick");
    assertThat(first.get("conversation").get("title").asString())
        .isEqualTo("What is a binary tree?");
    assertThat(first.get("question").get("role").asString()).isEqualTo("user");
    assertThat(first.get("reply").get("role").asString()).isEqualTo("ai");
    assertThat(first.get("reply").get("content").asString())
        .contains("You asked: What is a binary tree?");
    assertThat(first.get("notice").asString()).contains("AI-generated");

    JsonNode second = ask(me, id, "And a heap?");
    assertThat(second.get("conversation").get("id").asLong()).isEqualTo(id);
    assertThat(second.get("reply").get("content").asString()).contains("You asked: And a heap?");

    JsonNode messages = json(me.get(CONVERSATIONS + "/" + id + "/messages")).get("messages");
    assertThat(texts(messages, "role")).containsExactly("user", "ai", "user", "ai");
    assertThat(messages.get(2).get("content").asString()).isEqualTo("And a heap?");
    JsonNode list = json(me.get(CONVERSATIONS)).get("conversations");
    assertThat(list).hasSize(1);
    assertThat(list.get(0).get("id").asLong()).isEqualTo(id);
  }

  @Test
  void aLongFirstQuestionGivesAShortTitle() throws Exception {
    ApiTestClient me = student("AST00002");
    String question = "Explain " + "recursion ".repeat(20).strip();
    JsonNode answer = ask(me, null, question);
    assertThat(answer.get("conversation").get("title").asString()).hasSize(60);
  }

  @Test
  void theMessageIsChecked() throws Exception {
    ApiTestClient me = student("AST00003");
    assertProblem(me.post(CHAT, "{\"message\":\"   \"}"), 400, "VALIDATION_FAILED");
    assertProblem(me.post(CHAT, "{}"), 400, "VALIDATION_FAILED");
    assertProblem(me.post(CHAT, message(null, "x".repeat(4001))), 400, "VALIDATION_FAILED");
    assertThat(json(me.get(CONVERSATIONS)).get("conversations")).isEmpty();
  }

  @Test
  void aChatBelongsToItsStudent() throws Exception {
    ApiTestClient me = student("AST00004");
    ApiTestClient other = student("AST00005");
    long id = ask(me, null, "Private question").get("conversation").get("id").asLong();

    assertProblem(other.get(CONVERSATIONS + "/" + id + "/messages"), 404, "NOT_FOUND");
    assertProblem(other.delete(CONVERSATIONS + "/" + id), 404, "NOT_FOUND");
    assertProblem(other.post(CHAT, message(id, "Hello")), 404, "NOT_FOUND");
    assertThat(json(other.get(CONVERSATIONS)).get("conversations")).isEmpty();
    assertThat(json(me.get(CONVERSATIONS + "/" + id + "/messages")).get("messages")).hasSize(2);
  }

  @Test
  void aChatCanBeDeleted() throws Exception {
    ApiTestClient me = student("AST00006");
    long id = ask(me, null, "Delete me").get("conversation").get("id").asLong();
    assertThat(me.delete(CONVERSATIONS + "/" + id).statusCode()).isEqualTo(204);
    assertProblem(me.get(CONVERSATIONS + "/" + id + "/messages"), 404, "NOT_FOUND");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from app.assistant_messages where conversation_id = ?",
                Integer.class,
                id))
        .isZero();
  }

  @Test
  void onlyTheNewestSixQuickChatsAreKept() throws Exception {
    accounts.student("AST00007", "AST", 3, "A");
    Long studentId =
        jdbc.queryForObject(
            "select id from app.student_profiles where roll_no = 'AST00007'", Long.class);
    // Six chats already exist (made straight in the database: a student may ask only five questions
    // a minute).
    long oldest = 0;
    for (int i = 1; i <= 6; i++) {
      long id =
          jdbc.queryForObject(
              "insert into app.assistant_conversations (student_id, title, source, created_at, updated_at)"
                  + " values (?, ?, 'quick', now() - (? || ' hours')::interval, now() - (? || ' hours')::interval) returning id",
              Long.class,
              studentId,
              "Old question " + i,
              String.valueOf(10 - i),
              String.valueOf(10 - i));
      jdbc.update(
          "insert into app.assistant_messages (conversation_id, role, content) values (?, 'user', 'q')",
          id);
      if (i == 1) {
        oldest = id;
      }
    }
    ApiTestClient me = studentClient("AST00007");
    ask(me, null, "The seventh question");

    JsonNode list = json(me.get(CONVERSATIONS)).get("conversations");
    assertThat(list).hasSize(6);
    assertThat(texts(list, "title"))
        .containsExactly(
            "The seventh question",
            "Old question 6",
            "Old question 5",
            "Old question 4",
            "Old question 3",
            "Old question 2");
    // The oldest chat is gone, with its messages.
    assertProblem(me.get(CONVERSATIONS + "/" + oldest + "/messages"), 404, "NOT_FOUND");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from app.assistant_messages where conversation_id = ?",
                Integer.class,
                oldest))
        .isZero();
  }

  @Test
  void aFailedAnswerStoresNothingAndDoesNotUseUpARequest() throws Exception {
    ApiTestClient me = student("AST00008");
    assertProblem(
        me.post(CHAT, message(null, "[[mock:unavailable]] hello")), 503, "AI_UNAVAILABLE");
    assertProblem(me.post(CHAT, message(null, "[[mock:timeout]] hello")), 504, "AI_TIMEOUT");
    assertProblem(me.post(CHAT, message(null, "[[mock:empty]] hello")), 502, "AI_INVALID_RESPONSE");
    assertThat(json(me.get(CONVERSATIONS)).get("conversations")).isEmpty();
    assertThat(jdbc.queryForObject("select count(*) from app.assistant_messages", Integer.class))
        .isNotNull();
    // None of the three counted against the student's limit.
    assertThat(json(me.get("/api/v1/ai/usage/me")).get("usedToday").asInt()).isZero();
  }

  @Test
  void theReplyIsPlainTextAndTheUsageLogHoldsNoText() throws Exception {
    ApiTestClient me = student("AST00009");
    JsonNode answer = ask(me, null, "[[mock:html]] show me");
    assertThat(answer.get("reply").get("content").asString()).contains("<script>");

    List<String> columns =
        jdbc.queryForList(
            "select column_name from information_schema.columns where table_schema = 'app' and table_name = 'ai_request_logs'",
            String.class);
    assertThat(columns).doesNotContain("content", "prompt", "text", "response");
    String feature =
        jdbc.queryForObject(
            "select feature from app.ai_request_logs order by id desc limit 1", String.class);
    assertThat(feature).isEqualTo("QUICK_CHAT");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from app.ai_request_logs where prompt_version = 'assistant-quick-v2'",
                Integer.class))
        .isPositive();
  }

  @Test
  void theChatCountsAgainstTheSameDailyLimitAsTheStudyTools() throws Exception {
    ApiTestClient me = student("AST00010");
    for (int i = 0; i < 3; i++) {
      ask(me, null, "Question " + i);
    }
    assertThat(json(me.get("/api/v1/ai/usage/me")).get("usedToday").asInt()).isEqualTo(3);
    // The fifth request of a minute is accepted; the sixth is asked to wait.
    ask(me, null, "Question 4");
    ask(me, null, "Question 5");
    assertProblem(me.post(CHAT, message(null, "Question 6")), 429, "RATE_LIMITED");
  }

  // --- answer modes and pictures (Quick Assistant V2) -------------------------------------------

  private static String styled(String text, String style) {
    return "{\"message\":\"%s\",\"mode\":\"quick\",\"style\":\"%s\"}".formatted(text, style);
  }

  private static byte[] picture(String format, int width, int height) throws Exception {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, format, out);
    return out.toByteArray();
  }

  private HttpResponse<String> askWithPicture(
      ApiTestClient who, String metadata, String type, byte[] bytes) throws Exception {
    return who.postMultipart(CHAT, metadata, "image", "shot", type, bytes);
  }

  @Test
  void theAnswerModeChoosesTheInstructionsAndTheShapeOfTheAnswer() throws Exception {
    ApiTestClient me = student("AST00011");
    HttpResponse<String> quick = me.post(CHAT, styled("What is a stack?", "quick"));
    assertThat(quick.statusCode()).as(quick.body()).isEqualTo(200);
    assertThat(json(quick).get("reply").get("content").asString()).doesNotContain("```");

    HttpResponse<String> explain = me.post(CHAT, styled("Explain a stack", "explain"));
    assertThat(explain.statusCode()).as(explain.body()).isEqualTo(200);
    String text = json(explain).get("reply").get("content").asString();
    assertThat(text).contains("## ").contains("```flowchart").contains("| Part |");

    assertThat(
            jdbc.queryForList(
                "select prompt_version from app.ai_request_logs where prompt_version like 'assistant-%' order by id desc limit 2",
                String.class))
        .contains("assistant-quick-v2", "assistant-explain-v1");
  }

  @Test
  void aPictureGoesWithTheQuestionAndIsNotStored() throws Exception {
    ApiTestClient me = student("AST00012");
    HttpResponse<String> response =
        askWithPicture(
            me, styled("How do I fix this?", "quick"), "image/png", picture("png", 40, 30));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    JsonNode answer = json(response);
    assertThat(answer.get("question").get("content").asString())
        .isEqualTo("[Image] How do I fix this?");
    assertThat(answer.get("reply").get("content").asString()).contains("1 image was attached");

    // Only text is stored: the messages have no picture column and no bytes.
    List<String> columns =
        jdbc.queryForList(
            "select column_name from information_schema.columns where table_schema = 'app' and table_name = 'assistant_messages'",
            String.class);
    assertThat(columns).doesNotContain("image", "picture", "data");
  }

  @Test
  void aPictureAloneIsAQuestion() throws Exception {
    ApiTestClient me = student("AST00013");
    HttpResponse<String> response =
        askWithPicture(me, styled("", "explain"), "image/jpeg", picture("jpeg", 64, 48));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    assertThat(json(response).get("question").get("content").asString())
        .isEqualTo("[Image] Explain this image.");
    // Without a picture, a blank message is still refused.
    assertThat(invalidFields(me.post(CHAT, styled("", "quick")))).containsExactly("message");
  }

  @Test
  void aPictureThatIsNotAJpegOrPngIsRefusedAndStoresNothing() throws Exception {
    ApiTestClient me = student("AST00014");
    assertProblem(
        askWithPicture(me, styled("hi", "quick"), "text/plain", "hello".getBytes()),
        415,
        "UPLOAD_INVALID_TYPE");
    assertProblem(
        askWithPicture(me, styled("hi", "quick"), "image/gif", picture("gif", 10, 10)),
        415,
        "UPLOAD_INVALID_TYPE");
    // Declared PNG, but the bytes are something else.
    assertProblem(
        askWithPicture(me, styled("hi", "quick"), "image/png", "not a picture".getBytes()),
        415,
        "UPLOAD_INVALID_TYPE");
    // Declared PNG, but really a JPEG.
    assertProblem(
        askWithPicture(me, styled("hi", "quick"), "image/png", picture("jpeg", 10, 10)),
        415,
        "UPLOAD_INVALID_TYPE");
    assertThat(json(me.get(CONVERSATIONS)).get("conversations")).isEmpty();
    // None of them used up a request.
    assertThat(json(me.get("/api/v1/ai/usage/me")).get("usedToday").asInt()).isZero();
  }

  @Test
  void aPictureOverFiveMegabytesIsRefused() throws Exception {
    ApiTestClient me = student("AST00015");
    byte[] big = new byte[5 * 1024 * 1024 + 1];
    big[0] = (byte) 0xFF;
    big[1] = (byte) 0xD8;
    big[2] = (byte) 0xFF;
    assertProblem(
        askWithPicture(me, styled("hi", "quick"), "image/jpeg", big), 413, "UPLOAD_TOO_LARGE");
  }

  @Test
  void onlyALoggedInStudentCanSendAPicture() throws Exception {
    byte[] png = picture("png", 10, 10);
    assertProblem(
        askWithPicture(visitor(), styled("hi", "quick"), "image/png", png), 401, "AUTH_REQUIRED");
    ApiTestClient admin = adminClient("assistant-admin@university.example");
    assertProblem(
        askWithPicture(admin, styled("hi", "quick"), "image/png", png), 403, "ACCESS_DENIED");
  }
}
