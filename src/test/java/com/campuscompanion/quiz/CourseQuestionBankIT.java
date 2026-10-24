package com.campuscompanion.quiz;

import static com.campuscompanion.support.ApiTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.common.config.CourseQuestionBankLoader;
import com.campuscompanion.support.ApiIntegrationTest;
import com.campuscompanion.support.ApiTestClient;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * The bundled course question banks reach Quick Exam: loaded once into the subject with the
 * matching code, and a test of the whole subject draws its questions at random from them.
 */
class CourseQuestionBankIT extends ApiIntegrationTest {

  @Autowired private CourseQuestionBankLoader loader;

  @Test
  void theJavaBankIsLoadedOnceAndAWholeSubjectTestDrawsFromIt() throws Exception {
    ApiTestClient admin = adminClient("bank-admin@university.example");
    long subjectId = subject(admin, "25CS21002", "QBK", 3);

    assertThat(loader.load()).as("first load").isGreaterThanOrEqualTo(40);
    assertThat(loader.load()).as("nothing is added twice").isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from app.quiz_questions where subject_id = ? and unit_id is null"
                    + " and status = 'PUBLISHED'",
                Integer.class,
                subjectId))
        .isBetween(40, 50);

    accounts.student("23QBK001", "QBK", 3, "A");
    ApiTestClient student = studentClient("23QBK001");
    Set<Long> seen = new HashSet<>();
    long attemptId = 0;
    for (int test = 0; test < 3; test++) {
      HttpResponse<String> started =
          student.post(
              "/api/v1/quiz-attempts",
              "{\"subjectId\":%d,\"questionCount\":20}".formatted(subjectId));
      assertThat(started.statusCode()).as(started.body()).isEqualTo(201);
      JsonNode questions = json(started).get("questions");
      assertThat(questions).hasSize(20);
      questions.forEach(question -> seen.add(question.get("questionId").asLong()));
      attemptId = json(started).get("attemptId").asLong();
      if (test < 2) {
        student.post("/api/v1/quiz-attempts/" + attemptId + "/submit", "{\"answers\":[]}");
      }
    }
    assertThat(seen.size())
        .as("three tests of 20 drawn at random from the bank do not repeat the same 20")
        .isGreaterThan(20);

    JsonNode result =
        json(student.post("/api/v1/quiz-attempts/" + attemptId + "/submit", "{\"answers\":[]}"));
    assertThat(result.get("unitNo").isNull()).isTrue();
    JsonNode progress = json(student.get("/api/v1/progress/me"));
    List<JsonNode> units = progress.get("units").valueStream().toList();
    assertThat(units).hasSize(1);
    assertThat(units.get(0).get("subjectCode").asString()).isEqualTo("25CS21002");
    assertThat(units.get(0).get("unitId").isNull()).isTrue();
    assertThat(units.get(0).get("answered").asInt())
        .as("progress judges the most recent 20 answers")
        .isEqualTo(20);
  }
}
