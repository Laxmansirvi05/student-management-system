package com.campuscompanion.ai;

import static com.campuscompanion.support.ApiTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.support.ApiIntegrationTest;
import com.campuscompanion.support.ApiTestClient;
import com.campuscompanion.support.TestAccounts;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * FR-AI-006 over real HTTP with the mock provider: a flowchart of a topic or of the student's own
 * description, checked by Java before it is shown. Every test uses its own student because of the
 * per-minute AI limit.
 */
class AiFlowchartIT extends ApiIntegrationTest {

  private static final String FLOWCHART = "/api/v1/ai/flowchart";
  private static final String USAGE = "/api/v1/ai/usage/me";

  private ApiTestClient student(String rollNo, String branch) throws Exception {
    accounts.student(rollNo, branch, 3, "A");
    return studentClient(rollNo);
  }

  @Test
  void aTopicBecomesACheckedChartWithStepsAndMermaidText() throws Exception {
    ApiTestClient admin = adminClient("flow-admin@university.example");
    long subjectId = subject(admin, "FLW101", "FLW", 3);
    ApiTestClient student = student("23FLW001", "FLW");

    HttpResponse<String> response =
        student.post(
            FLOWCHART, "{\"topic\":\"  binary search \",\"subjectId\":%d}".formatted(subjectId));

    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    JsonNode body = json(response);
    assertThat(body.get("title").asString()).isEqualTo("Demo flowchart: binary search");
    assertThat(body.get("nodes").size()).isEqualTo(5);
    assertThat(body.get("edges").size()).isEqualTo(5);
    assertThat(body.get("steps").get(2).asString())
        .isEqualTo("Is it clear? (No: go to step 4; Yes: go to step 5)");
    assertThat(body.get("mermaid").asString()).startsWith("flowchart TD\n").contains("N3{");
    assertThat(body.get("basis").asString()).isEqualTo("GENERAL_KNOWLEDGE");
    assertThat(body.get("notice").asString())
        .isEqualTo("AI-generated. Check it against your course material.");
    assertThat(body.get("promptVersion").asString()).isEqualTo("flowchart-v1");

    HttpResponse<String> described =
        student.post(
            FLOWCHART,
            "{\"processText\":\"First the request is checked, then it is saved, then a reply is sent.\"}");
    assertThat(described.statusCode()).as(described.body()).isEqualTo(200);
    assertThat(json(described).get("basis").asString()).isEqualTo("PROVIDED_TEXT");

    assertThat(json(student.get(USAGE)).get("usedToday").asInt()).isEqualTo(2);
    assertThat(statuses("23FLW001")).containsExactly("SUCCESS", "SUCCESS");
    assertThat(
            jdbc.queryForList(
                "select feature::text from app.ai_request_logs where user_id = ?",
                String.class,
                userId("23FLW001")))
        .containsOnly("FLOWCHART");
  }

  @Test
  void theRequestNeedsExactlyOneSourceWithinItsLimits() throws Exception {
    ApiTestClient student = student("23FLX001", "FLX");

    String longText = "x".repeat(3001);
    for (String body :
        List.of(
            "{}",
            "{\"topic\":\"stacks\",\"processText\":\"A long enough description of a process.\"}",
            "{\"topic\":\"ab\"}",
            "{\"processText\":\"too short\"}",
            "{\"processText\":\"" + longText + "\"}")) {
      HttpResponse<String> response = student.post(FLOWCHART, body);
      assertProblem(response, 400, "VALIDATION_FAILED");
    }
    assertThat(statuses("23FLX001")).isEmpty();
  }

  @Test
  void aChartThatDoesNotHoldTogetherIsNeverShown() throws Exception {
    ApiTestClient student = student("23FLY001", "FLY");

    assertProblem(
        student.post(FLOWCHART, "{\"topic\":\"[[mock:badchart]] sorting\"}"),
        502,
        "AI_INVALID_RESPONSE");
    assertProblem(
        student.post(FLOWCHART, "{\"topic\":\"[[mock:invalid]] sorting\"}"),
        502,
        "AI_INVALID_RESPONSE");
    assertProblem(
        student.post(FLOWCHART, "{\"topic\":\"[[mock:decline]] a poem\"}"),
        422,
        "AI_REQUEST_DECLINED");

    assertThat(statuses("23FLY001")).containsExactly("FAILED", "FAILED", "DECLINED");
    assertThat(json(student.get(USAGE)).get("usedToday").asInt()).isEqualTo(1);
  }

  @Test
  void flowchartsAreForStudentsOnly() throws Exception {
    ApiTestClient admin = adminClient("flow-admin2@university.example");
    String body = "{\"topic\":\"stacks\"}";

    assertProblem(visitor().post(FLOWCHART, body), 401, "AUTH_REQUIRED");
    assertProblem(admin.post(FLOWCHART, body), 403, "ACCESS_DENIED");
  }

  private long userId(String rollNo) {
    return jdbc.queryForObject(
        "select id from app.users where login_email = ?", Long.class, TestAccounts.emailOf(rollNo));
  }

  private List<String> statuses(String rollNo) {
    return jdbc.queryForList(
        "select status::text from app.ai_request_logs where user_id = ? order by id",
        String.class,
        userId(rollNo));
  }
}
