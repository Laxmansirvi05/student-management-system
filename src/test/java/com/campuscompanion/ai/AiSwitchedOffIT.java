package com.campuscompanion.ai;

import static com.campuscompanion.support.ApiTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.support.ApiIntegrationTest;
import com.campuscompanion.support.ApiTestClient;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * The kill switch. With AI_ENABLED=false the application starts as usual, every AI endpoint answers
 * "unavailable", and nothing else is affected.
 */
@TestPropertySource(properties = "AI_ENABLED=false")
class AiSwitchedOffIT extends ApiIntegrationTest {

  @Test
  void everyAiEndpointIsUnavailableAndTheRestOfTheApplicationWorks() throws Exception {
    accounts.student("23AIZ001", "AIZ", 3, "A");
    ApiTestClient student = studentClient("23AIZ001");

    HttpResponse<String> explain =
        student.post("/api/v1/ai/explain", "{\"topic\":\"stacks\",\"answerFormat\":\"SIMPLE\"}");
    assertProblem(explain, 503, "AI_UNAVAILABLE");
    assertThat(json(explain).get("detail").asString())
        .isEqualTo("The assistant is unavailable right now.");
    assertProblem(
        student.post(
            "/api/v1/ai/generate-quiz",
            "{\"topic\":\"stacks\",\"questionType\":\"MCQ\",\"count\":1}"),
        503,
        "AI_UNAVAILABLE");

    assertThat(json(student.get("/api/v1/ai/usage/me")).get("enabled").asBoolean()).isFalse();
    assertThat(student.get("/api/v1/students/me").statusCode()).isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from app.ai_request_logs l join app.users u on u.id = l.user_id"
                    + " where u.login_email = '23aiz001@university.example'",
                Integer.class))
        .isZero();
  }
}
