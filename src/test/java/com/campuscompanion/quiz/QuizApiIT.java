package com.campuscompanion.quiz;

import static com.campuscompanion.support.ApiTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.support.ApiIntegrationTest;
import com.campuscompanion.support.ApiTestClient;
import com.campuscompanion.support.TestPdfs;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Quick Exam and progress over real HTTP: the question bank, starting and submitting a test, the
 * time limit, the history, and the access rules Z3, Z4, Z7, Z11 and Z13 of
 * docs/17-testing-strategy.md.
 */
class QuizApiIT extends ApiIntegrationTest {

  private static final String ADMIN = "quiz-admin@university.example";

  private ApiTestClient admin;

  @BeforeEach
  void logInAsAdmin() throws Exception {
    admin = adminClient(ADMIN);
  }

  // --- The question bank ----------------------------------------------------------------------

  @Test
  void anAdminWritesPublishesAndFiltersQuestions() throws Exception {
    long subjectId = subject(admin, "QZA101", "QZA", 3);
    long unitId = unit(subjectId, 1);
    long otherUnitId = unit(subjectId, 2);

    HttpResponse<String> created =
        admin.post(
            "/api/v1/admin/quiz-questions", question(subjectId, unitId, "First?", "B", "DRAFT"));
    assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
    JsonNode draft = json(created);
    assertThat(draft.get("status").asString()).isEqualTo("DRAFT");
    assertThat(draft.get("source").asString()).isEqualTo("ADMIN");
    assertThat(draft.get("correctOption").asString()).isEqualTo("B");
    assertThat(draft.get("used").asBoolean()).isFalse();
    assertThat(draft.get("unitNo").asInt()).isEqualTo(1);

    HttpResponse<String> published =
        admin.put(
            "/api/v1/admin/quiz-questions/" + draft.get("id").asLong(),
            question(subjectId, unitId, "First, reworded?", "C", "PUBLISHED"));
    assertThat(published.statusCode()).as(published.body()).isEqualTo(200);
    assertThat(json(published).get("questionText").asString()).isEqualTo("First, reworded?");
    assertThat(json(published).get("correctOption").asString()).isEqualTo("C");

    questionId(subjectId, otherUnitId, "Second?", "A", "DRAFT");

    assertThat(
            json(admin.get("/api/v1/admin/quiz-questions?subjectId=" + subjectId))
                .get("totalElements")
                .asInt())
        .isEqualTo(2);
    assertThat(
            texts(
                json(admin.get("/api/v1/admin/quiz-questions?unitId=" + otherUnitId))
                    .get("content"),
                "questionText"))
        .containsExactly("Second?");
    assertThat(
            texts(
                json(admin.get(
                        "/api/v1/admin/quiz-questions?status=PUBLISHED&subjectId=" + subjectId))
                    .get("content"),
                "questionText"))
        .containsExactly("First, reworded?");
  }

  @Test
  void aQuestionMustBeCompleteAndBelongToAUnitOfItsSubject() throws Exception {
    long subjectId = subject(admin, "QZB101", "QZB", 3);
    long unitId = unit(subjectId, 1);
    long otherSubjectId = subject(admin, "QZB102", "QZB", 3);

    HttpResponse<String> empty =
        admin.post(
            "/api/v1/admin/quiz-questions",
            """
            {"subjectId":%d,"unitId":%d,"questionText":" ","optionA":"","optionB":"b","optionC":"c",
             "optionD":"d","correctOption":null,"explanation":"","difficulty":null,"status":null}
            """
                .formatted(subjectId, unitId));
    assertThat(invalidFields(empty))
        .containsExactly(
            "correctOption", "difficulty", "explanation", "optionA", "questionText", "status");

    assertThat(
            invalidFields(
                admin.post(
                    "/api/v1/admin/quiz-questions",
                    question(otherSubjectId, unitId, "Wrong unit?", "A", "DRAFT"))))
        .containsExactly("unitId");
    assertThat(
            invalidFields(
                admin.post(
                    "/api/v1/admin/quiz-questions",
                    question(subjectId, unitId, "Retired at once?", "A", "RETIRED"))))
        .containsExactly("status");
    assertThat(
            admin
                .post(
                    "/api/v1/admin/quiz-questions",
                    question(subjectId, unitId, "Fifth option?", "E", "DRAFT"))
                .statusCode())
        .isEqualTo(400);
    assertProblem(
        admin.put(
            "/api/v1/admin/quiz-questions/999999999",
            question(subjectId, unitId, "Missing?", "A", "DRAFT")),
        404,
        "NOT_FOUND");
  }

  @Test
  void aQuestionUsedInATestCanOnlyBeRetiredOrExplainedBetter() throws Exception {
    long subjectId = subject(admin, "QZC101", "QZC", 3);
    long unitId = unit(subjectId, 1);
    List<Long> ids = publishedQuestions(subjectId, unitId, 5);
    accounts.student("23QZC001", "QZC", 3, "A");
    start(studentClient("23QZC001"), subjectId, unitId, 5);
    long used = ids.get(0);

    assertProblem(
        admin.put(
            "/api/v1/admin/quiz-questions/" + used,
            question(subjectId, unitId, "Rewritten after the test?", "A", "PUBLISHED")),
        409,
        "CONFLICT");
    assertProblem(
        admin.put(
            "/api/v1/admin/quiz-questions/" + used,
            question(subjectId, unitId, "Question 1?", "B", "PUBLISHED")),
        409,
        "CONFLICT");
    assertThat(
            invalidFields(
                admin.put(
                    "/api/v1/admin/quiz-questions/" + used,
                    question(subjectId, unitId, "Question 1?", "A", "DRAFT"))))
        .containsExactly("status");

    // The explanation and the status may still change.
    HttpResponse<String> retired =
        admin.put(
            "/api/v1/admin/quiz-questions/" + used,
            question(subjectId, unitId, "Question 1?", "A", "RETIRED")
                .replace("Because it is.", "A clearer explanation."));
    assertThat(retired.statusCode()).as(retired.body()).isEqualTo(200);
    assertThat(json(retired).get("used").asBoolean()).isTrue();
    assertThat(json(retired).get("explanation").asString()).isEqualTo("A clearer explanation.");

    // A retired question is not used in a new test: four are left, and five were asked for.
    HttpResponse<String> second =
        studentClient("23QZC001")
            .post(
                "/api/v1/quiz-attempts",
                "{\"subjectId\":%d,\"unitId\":%d,\"questionCount\":5}"
                    .formatted(subjectId, unitId));
    assertProblem(second, 409, "QUIZ_NOT_ENOUGH_QUESTIONS");
    assertThat(json(second).get("available").asInt()).isEqualTo(4);
  }

  // --- Starting a test ------------------------------------------------------------------------

  @Test
  void aStartedTestHasItsQuestionsButNoneOfTheirAnswers() throws Exception {
    long subjectId = subject(admin, "QZD101", "QZD", 3);
    long unitId = unit(subjectId, 1);
    long otherUnitId = unit(subjectId, 2);
    List<Long> ids = publishedQuestions(subjectId, unitId, 7);
    questionId(subjectId, unitId, "Still a draft?", "A", "DRAFT");
    publishedQuestions(subjectId, otherUnitId, 5);
    accounts.student("23QZD001", "QZD", 3, "A");
    ApiTestClient student = studentClient("23QZD001");

    HttpResponse<String> response =
        student.post(
            "/api/v1/quiz-attempts",
            "{\"subjectId\":%d,\"unitId\":%d,\"questionCount\":5}".formatted(subjectId, unitId));

    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    // The correct option and the explanation are nowhere in what the browser receives.
    assertThat(response.body())
        .doesNotContain("correctOption", "correct", "explanation", "Because it is.");
    JsonNode started = json(response);
    assertThat(started.get("durationSeconds").asInt()).isEqualTo(300);
    Instant startedAt = Instant.parse(started.get("startedAt").asString());
    Instant expiresAt = Instant.parse(started.get("expiresAt").asString());
    assertThat(Duration.between(startedAt, expiresAt)).isEqualTo(Duration.ofMinutes(5));

    List<Long> served = new ArrayList<>();
    for (JsonNode question : started.get("questions")) {
      served.add(question.get("questionId").asLong());
      assertThat(question.get("position").asInt()).isEqualTo(served.size());
      assertThat(new TreeSet<>(texts(question.get("options"), "key")))
          .containsExactly("A", "B", "C", "D");
    }
    // Five different published questions of this unit: no draft, none of the other unit.
    assertThat(served).hasSize(5).doesNotHaveDuplicates();
    assertThat(ids).containsAll(served);
  }

  @Test
  void aTestNeedsEnoughQuestionsAndAValidLength() throws Exception {
    long subjectId = subject(admin, "QZE101", "QZE", 3);
    long unitId = unit(subjectId, 1);
    long otherSubjectId = subject(admin, "QZE102", "QZE", 3);
    publishedQuestions(subjectId, unitId, 7);
    accounts.student("23QZE001", "QZE", 3, "A");
    ApiTestClient student = studentClient("23QZE001");

    HttpResponse<String> tooMany =
        student.post(
            "/api/v1/quiz-attempts",
            "{\"subjectId\":%d,\"unitId\":%d,\"questionCount\":10}".formatted(subjectId, unitId));
    assertProblem(tooMany, 409, "QUIZ_NOT_ENOUGH_QUESTIONS");
    assertThat(json(tooMany).get("available").asInt()).isEqualTo(7);

    assertThat(
            invalidFields(
                student.post(
                    "/api/v1/quiz-attempts",
                    "{\"subjectId\":%d,\"unitId\":%d,\"questionCount\":7}"
                        .formatted(subjectId, unitId))))
        .containsExactly("questionCount");
    assertThat(
            invalidFields(
                student.post("/api/v1/quiz-attempts", "{\"subjectId\":null,\"unitId\":null}")))
        .containsExactly("questionCount", "subjectId", "unitId");
    assertProblem(
        student.post(
            "/api/v1/quiz-attempts",
            "{\"subjectId\":%d,\"unitId\":%d,\"questionCount\":5}"
                .formatted(otherSubjectId, unitId)),
        404,
        "NOT_FOUND");
    assertThat(attemptsOf("23QZE001")).isZero();
  }

  // --- Submitting -----------------------------------------------------------------------------

  @Test
  void theServerMarksTheTestAndItCanBeSubmittedOnce() throws Exception {
    long subjectId = subject(admin, "QZF101", "QZF", 3);
    long unitId = unit(subjectId, 1);
    publishedQuestions(subjectId, unitId, 5);
    accounts.student("23QZF001", "QZF", 3, "A");
    ApiTestClient student = studentClient("23QZF001");
    JsonNode started = start(student, subjectId, unitId, 5);
    long attemptId = started.get("attemptId").asLong();
    List<Long> served = servedIds(started);
    Map<Long, String> correct = correctOptions(unitId);

    // While the test is running, its result is not available.
    assertProblem(student.get("/api/v1/quiz-attempts/" + attemptId), 409, "CONFLICT");

    // Two right, one wrong, one sent without an option, one left out.
    String answers =
        "{\"answers\":["
            + answer(served.get(0), correct.get(served.get(0)))
            + ","
            + answer(served.get(1), correct.get(served.get(1)))
            + ","
            + answer(served.get(2), wrong(correct.get(served.get(2))))
            + ",{\"questionId\":"
            + served.get(3)
            + ",\"selectedOption\":null}]}";
    HttpResponse<String> response =
        student.post("/api/v1/quiz-attempts/" + attemptId + "/submit", answers);

    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    JsonNode result = json(response);
    assertThat(result.get("status").asString()).isEqualTo("SUBMITTED");
    assertThat(result.get("score").asInt()).isEqualTo(2);
    assertThat(result.get("questionCount").asInt()).isEqualTo(5);
    assertThat(result.get("percent").decimalValue()).isEqualByComparingTo("40.00");
    assertThat(result.get("subjectCode").asString()).isEqualTo("QZF101");
    JsonNode items = result.get("items");
    assertThat(items).hasSize(5);
    assertThat(items.get(0).get("correct").asBoolean()).isTrue();
    assertThat(items.get(0).get("correctOption").asString()).isEqualTo(correct.get(served.get(0)));
    assertThat(items.get(0).get("explanation").asString()).isEqualTo("Because it is.");
    assertThat(texts(items.get(0).get("options"), "key")).containsExactly("A", "B", "C", "D");
    assertThat(items.get(2).get("correct").asBoolean()).isFalse();
    assertThat(items.get(3).get("selectedOption").isNull()).isTrue();
    assertThat(items.get(4).get("selectedOption").isNull()).isTrue();
    assertThat(items.get(4).get("correct").asBoolean()).isFalse();

    // A second submission changes nothing.
    String allRight = allAnswers(served, correct);
    assertProblem(
        student.post("/api/v1/quiz-attempts/" + attemptId + "/submit", allRight),
        409,
        "QUIZ_ALREADY_SUBMITTED");
    JsonNode reopened = json(student.get("/api/v1/quiz-attempts/" + attemptId));
    assertThat(reopened.get("score").asInt()).isEqualTo(2);
    assertThat(reopened.get("items")).isEqualTo(items);
  }

  @Test
  void theBrowserCannotSendItsOwnScoreOrAnswerAnotherQuestion() throws Exception {
    long subjectId = subject(admin, "QZG101", "QZG", 3);
    long unitId = unit(subjectId, 1);
    long otherUnitId = unit(subjectId, 2);
    publishedQuestions(subjectId, unitId, 5);
    long foreignQuestion = publishedQuestions(subjectId, otherUnitId, 1).get(0);
    accounts.student("23QZG001", "QZG", 3, "A");
    ApiTestClient student = studentClient("23QZG001");
    JsonNode started = start(student, subjectId, unitId, 5);
    long attemptId = started.get("attemptId").asLong();
    List<Long> served = servedIds(started);
    String submit = "/api/v1/quiz-attempts/" + attemptId + "/submit";

    assertThat(student.post(submit, "{\"answers\":[],\"score\":5}").statusCode()).isEqualTo(400);
    assertThat(
            student
                .post(
                    submit,
                    "{\"answers\":[{\"questionId\":%d,\"correct\":true}]}".formatted(served.get(0)))
                .statusCode())
        .isEqualTo(400);
    assertThat(
            invalidFields(
                student.post(submit, "{\"answers\":[" + answer(foreignQuestion, "A") + "]}")))
        .containsExactly("answers[0].questionId");
    assertThat(
            invalidFields(
                student.post(
                    submit,
                    "{\"answers\":["
                        + answer(served.get(0), "A")
                        + ","
                        + answer(served.get(0), "B")
                        + "]}")))
        .containsExactly("answers[1].questionId");
    assertThat(invalidFields(student.post(submit, "{}"))).containsExactly("answers");

    // None of the refused requests closed the test or gave it a score.
    assertThat(statusAndScore(attemptId)).isEqualTo("IN_PROGRESS|null");
    assertThat(student.post(submit, "{\"answers\":[]}").statusCode()).isEqualTo(200);
    assertThat(statusAndScore(attemptId)).isEqualTo("SUBMITTED|0");
  }

  @Test
  void lateAnswersAreRefusedAndTheTestIsClosedAsExpired() throws Exception {
    long subjectId = subject(admin, "QZH101", "QZH", 3);
    long unitId = unit(subjectId, 1);
    publishedQuestions(subjectId, unitId, 5);
    accounts.student("23QZH001", "QZH", 3, "A");
    ApiTestClient student = studentClient("23QZH001");
    Map<Long, String> correct = correctOptions(unitId);

    // Ten seconds late is inside the grace period, so the test still counts.
    JsonNode inGrace = start(student, subjectId, unitId, 5);
    endedAgo(inGrace.get("attemptId").asLong(), "10 seconds");
    HttpResponse<String> accepted =
        student.post(
            "/api/v1/quiz-attempts/" + inGrace.get("attemptId").asLong() + "/submit",
            allAnswers(servedIds(inGrace), correct));
    assertThat(accepted.statusCode()).as(accepted.body()).isEqualTo(200);
    assertThat(json(accepted).get("score").asInt()).isEqualTo(5);

    // A minute late is not.
    JsonNode late = start(student, subjectId, unitId, 5);
    long lateId = late.get("attemptId").asLong();
    endedAgo(lateId, "1 minute");
    assertProblem(
        student.post(
            "/api/v1/quiz-attempts/" + lateId + "/submit", allAnswers(servedIds(late), correct)),
        409,
        "QUIZ_ATTEMPT_EXPIRED");
    // The test was closed although the request failed, and no answer was accepted.
    assertThat(statusAndScore(lateId)).isEqualTo("EXPIRED|0");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from app.quiz_attempt_answers"
                    + " where attempt_id = ? and (selected_option is not null or is_correct)",
                Integer.class,
                lateId))
        .isZero();
    assertProblem(
        student.post("/api/v1/quiz-attempts/" + lateId + "/submit", "{\"answers\":[]}"),
        409,
        "QUIZ_ATTEMPT_EXPIRED");

    // An expired test can be opened, and shows the answers the student missed.
    JsonNode expired = json(student.get("/api/v1/quiz-attempts/" + lateId));
    assertThat(expired.get("status").asString()).isEqualTo("EXPIRED");
    assertThat(expired.get("score").asInt()).isZero();
    assertThat(expired.get("items").get(0).get("correctOption").asString()).isNotBlank();

    // A test that was simply abandoned is closed when the student next looks at the history.
    JsonNode abandoned = start(student, subjectId, unitId, 5);
    endedAgo(abandoned.get("attemptId").asLong(), "1 hour");
    JsonNode history = json(student.get("/api/v1/quiz-attempts/me")).get("content");
    assertThat(texts(history, "status"))
        .containsExactlyInAnyOrder("EXPIRED", "EXPIRED", "SUBMITTED");
  }

  // --- History and other students -------------------------------------------------------------

  @Test
  void aStudentSeesOnlyTheirOwnTests() throws Exception {
    long subjectId = subject(admin, "QZI101", "QZI", 3);
    long unitId = unit(subjectId, 1);
    publishedQuestions(subjectId, unitId, 5);
    accounts.student("23QZI001", "QZI", 3, "A");
    accounts.student("23QZI002", "QZI", 3, "A");
    ApiTestClient owner = studentClient("23QZI001");
    ApiTestClient other = studentClient("23QZI002");
    Map<Long, String> correct = correctOptions(unitId);
    JsonNode first = start(owner, subjectId, unitId, 5);
    long firstId = first.get("attemptId").asLong();
    owner.post(
        "/api/v1/quiz-attempts/" + firstId + "/submit", allAnswers(servedIds(first), correct));
    JsonNode running = start(owner, subjectId, unitId, 5);
    long runningId = running.get("attemptId").asLong();

    // Another student's test is "not found": to read, and to submit.
    assertProblem(other.get("/api/v1/quiz-attempts/" + firstId), 404, "NOT_FOUND");
    assertProblem(
        other.post(
            "/api/v1/quiz-attempts/" + runningId + "/submit",
            allAnswers(servedIds(running), correct)),
        404,
        "NOT_FOUND");
    assertThat(statusAndScore(runningId)).isEqualTo("IN_PROGRESS|null");
    assertThat(json(other.get("/api/v1/quiz-attempts/me")).get("content")).isEmpty();
    // A student ID in the address is not a way in either.
    assertThat(
            json(other.get(
                    "/api/v1/quiz-attempts/me?studentId="
                        + accounts.student("23QZI001", "QZI", 3, "A")))
                .get("content"))
        .isEmpty();

    JsonNode history = json(owner.get("/api/v1/quiz-attempts/me"));
    assertThat(history.get("totalElements").asInt()).isEqualTo(2);
    JsonNode newest = history.get("content").get(0);
    assertThat(newest.get("attemptId").asLong()).isEqualTo(runningId);
    assertThat(newest.get("status").asString()).isEqualTo("IN_PROGRESS");
    assertThat(newest.get("score").isNull()).isTrue();
    JsonNode older = history.get("content").get(1);
    assertThat(older.get("score").asInt()).isEqualTo(5);
    assertThat(older.get("subjectCode").asString()).isEqualTo("QZI101");
    assertThat(older.get("unitNo").asInt()).isEqualTo(1);
    assertThat(json(owner.get("/api/v1/quiz-attempts/me?size=1")).get("totalPages").asInt())
        .isEqualTo(2);
  }

  @Test
  void quizAndProgressAreForStudentsAndTheQuestionBankIsForAdmins() throws Exception {
    long subjectId = subject(admin, "QZJ101", "QZJ", 3);
    long unitId = unit(subjectId, 1);
    publishedQuestions(subjectId, unitId, 5);
    accounts.student("23QZJ001", "QZJ", 3, "A");
    ApiTestClient student = studentClient("23QZJ001");
    ApiTestClient visitor = visitor();
    String startBody =
        "{\"subjectId\":%d,\"unitId\":%d,\"questionCount\":5}".formatted(subjectId, unitId);

    for (String path :
        List.of(
            "/api/v1/quiz-attempts/me",
            "/api/v1/quiz-attempts/1",
            "/api/v1/progress/me",
            "/api/v1/admin/quiz-questions")) {
      assertProblem(visitor.get(path), 401, "AUTH_REQUIRED");
    }
    assertProblem(visitor.post("/api/v1/quiz-attempts", startBody), 401, "AUTH_REQUIRED");

    assertProblem(admin.post("/api/v1/quiz-attempts", startBody), 403, "ACCESS_DENIED");
    assertProblem(admin.get("/api/v1/quiz-attempts/me"), 403, "ACCESS_DENIED");
    assertProblem(admin.get("/api/v1/progress/me"), 403, "ACCESS_DENIED");

    assertProblem(student.get("/api/v1/admin/quiz-questions"), 403, "ACCESS_DENIED");
    assertProblem(
        student.post(
            "/api/v1/admin/quiz-questions",
            question(subjectId, unitId, "By a student?", "A", "PUBLISHED")),
        403,
        "ACCESS_DENIED");
    assertProblem(
        student.put(
            "/api/v1/admin/quiz-questions/1",
            question(subjectId, unitId, "By a student?", "A", "PUBLISHED")),
        403,
        "ACCESS_DENIED");
  }

  // --- Progress -------------------------------------------------------------------------------

  @Test
  void progressJudgesEachUnitAndRecommendsResourcesForTheWeakOnes() throws Exception {
    long subjectId = subject(admin, "QZK101", "QZK", 3);
    long weakUnit = unit(subjectId, 1);
    long strongUnit = unit(subjectId, 2);
    publishedQuestions(subjectId, weakUnit, 5);
    publishedQuestions(subjectId, strongUnit, 5);
    // Four resources for the weak unit; the notes were uploaded first and must still come first.
    resource(subjectId, weakUnit, "Unit notes", "NOTES");
    resource(subjectId, weakUnit, "Lab manual", "LAB_MANUAL");
    resource(subjectId, weakUnit, "Important questions", "IMPORTANT_QUESTIONS");
    resource(subjectId, weakUnit, "Syllabus", "SYLLABUS");
    accounts.student("23QZK001", "QZK", 3, "A");
    ApiTestClient student = studentClient("23QZK001");

    JsonNode before = json(student.get("/api/v1/progress/me"));
    assertThat(before.get("testsTaken").asInt()).isZero();
    assertThat(before.get("averagePercent").isNull()).isTrue();
    assertThat(before.get("units")).isEmpty();
    assertThat(before.get("recommendations")).isEmpty();

    submitWithRightAnswers(student, subjectId, weakUnit, 2);
    submitWithRightAnswers(student, subjectId, strongUnit, 5);
    // A test that ran out of time does not count for or against the student.
    JsonNode abandoned = start(student, subjectId, strongUnit, 5);
    endedAgo(abandoned.get("attemptId").asLong(), "1 hour");

    JsonNode progress = json(student.get("/api/v1/progress/me"));
    assertThat(progress.get("testsTaken").asInt()).isEqualTo(2);
    assertThat(progress.get("averagePercent").decimalValue()).isEqualByComparingTo("70.00");
    JsonNode units = progress.get("units");
    assertThat(units).hasSize(2);
    assertThat(units.get(0).get("unitNo").asInt()).isEqualTo(1);
    assertThat(units.get(0).get("answered").asInt()).isEqualTo(5);
    assertThat(units.get(0).get("correct").asInt()).isEqualTo(2);
    assertThat(units.get(0).get("accuracyPercent").decimalValue()).isEqualByComparingTo("40.00");
    assertThat(units.get(0).get("level").asString()).isEqualTo("NEEDS_REVISION");
    assertThat(units.get(1).get("level").asString()).isEqualTo("STRONG");

    JsonNode recommendations = progress.get("recommendations");
    assertThat(recommendations).hasSize(1);
    assertThat(recommendations.get(0).get("unitId").asLong()).isEqualTo(weakUnit);
    assertThat(recommendations.get(0).get("accuracyPercent").decimalValue())
        .isEqualByComparingTo("40.00");
    assertThat(texts(recommendations.get(0).get("resources"), "title"))
        .containsExactly("Unit notes", "Syllabus", "Important questions");

    // The dashboard shows the same recommendation.
    JsonNode dashboard = json(student.get("/api/v1/students/me/dashboard"));
    assertThat(dashboard.get("recommendations")).isEqualTo(recommendations);

    // After a better test, the unit is judged on all ten answers: 7 of 10 is improving.
    submitWithRightAnswers(student, subjectId, weakUnit, 5);
    JsonNode later = json(student.get("/api/v1/progress/me"));
    assertThat(later.get("units").get(0).get("answered").asInt()).isEqualTo(10);
    assertThat(later.get("units").get(0).get("level").asString()).isEqualTo("IMPROVING");
    assertThat(later.get("recommendations")).isEmpty();
  }

  // --- Helpers --------------------------------------------------------------------------------

  private static String question(
      long subjectId, long unitId, String text, String correctOption, String status) {
    return """
        {"subjectId":%d,"unitId":%d,"questionText":"%s","optionA":"Option A","optionB":"Option B",
         "optionC":"Option C","optionD":"Option D","correctOption":"%s",
         "explanation":"Because it is.","difficulty":"EASY","status":"%s"}
        """
        .formatted(subjectId, unitId, text, correctOption, status);
  }

  private long questionId(
      long subjectId, long unitId, String text, String correctOption, String status)
      throws Exception {
    HttpResponse<String> response =
        admin.post(
            "/api/v1/admin/quiz-questions",
            question(subjectId, unitId, text, correctOption, status));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    return json(response).get("id").asLong();
  }

  /**
   * Publishes "Question 1?" to "Question n?" in a unit, with the correct option going A, B, C, D.
   */
  private List<Long> publishedQuestions(long subjectId, long unitId, int count) throws Exception {
    List<Long> ids = new ArrayList<>();
    for (int i = 1; i <= count; i++) {
      String correct = String.valueOf("ABCD".charAt((i - 1) % 4));
      ids.add(questionId(subjectId, unitId, "Question " + i + "?", correct, "PUBLISHED"));
    }
    return ids;
  }

  private long unit(long subjectId, int unitNo) throws Exception {
    HttpResponse<String> response =
        admin.post(
            "/api/v1/admin/subjects/" + subjectId + "/units",
            "{\"unitNo\":%d,\"title\":\"Unit %d\"}".formatted(unitNo, unitNo));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    return json(response).get("id").asLong();
  }

  private void resource(long subjectId, long unitId, String title, String type) throws Exception {
    HttpResponse<String> response =
        admin.postMultipart(
            "/api/v1/admin/resources",
            "{\"title\":\"%s\",\"subjectId\":%d,\"unitId\":%d,\"type\":\"%s\"}"
                .formatted(title, subjectId, unitId, type),
            "file.pdf",
            "application/pdf",
            TestPdfs.valid(1));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
  }

  private static JsonNode start(ApiTestClient student, long subjectId, long unitId, int count)
      throws Exception {
    HttpResponse<String> response =
        student.post(
            "/api/v1/quiz-attempts",
            "{\"subjectId\":%d,\"unitId\":%d,\"questionCount\":%d}"
                .formatted(subjectId, unitId, count));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    return json(response);
  }

  private static List<Long> servedIds(JsonNode started) {
    List<Long> ids = new ArrayList<>();
    for (JsonNode question : started.get("questions")) {
      ids.add(question.get("questionId").asLong());
    }
    return ids;
  }

  /** Takes a five-question test in a unit and answers the first "right" questions correctly. */
  private void submitWithRightAnswers(ApiTestClient student, long subjectId, long unitId, int right)
      throws Exception {
    JsonNode started = start(student, subjectId, unitId, 5);
    List<Long> served = servedIds(started);
    Map<Long, String> correct = correctOptions(unitId);
    List<String> answers = new ArrayList<>();
    for (int i = 0; i < served.size(); i++) {
      String option = correct.get(served.get(i));
      answers.add(answer(served.get(i), i < right ? option : wrong(option)));
    }
    HttpResponse<String> response =
        student.post(
            "/api/v1/quiz-attempts/" + started.get("attemptId").asLong() + "/submit",
            "{\"answers\":[" + String.join(",", answers) + "]}");
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    assertThat(json(response).get("score").asInt()).isEqualTo(right);
  }

  private static String answer(long questionId, String option) {
    return "{\"questionId\":%d,\"selectedOption\":\"%s\"}".formatted(questionId, option);
  }

  private static String allAnswers(List<Long> served, Map<Long, String> correct) {
    List<String> answers = new ArrayList<>();
    for (Long id : served) {
      answers.add(answer(id, correct.get(id)));
    }
    return "{\"answers\":[" + String.join(",", answers) + "]}";
  }

  private static String wrong(String correctOption) {
    return correctOption.equals("A") ? "B" : "A";
  }

  /** The correct option of every question of a unit, read straight from the database. */
  private Map<Long, String> correctOptions(long unitId) {
    Map<Long, String> options = new HashMap<>();
    jdbc.query(
        "select id, correct_option from app.quiz_questions where unit_id = ?",
        row -> {
          options.put(row.getLong("id"), row.getString("correct_option"));
        },
        unitId);
    return options;
  }

  /** Moves a test into the past, so that its time limit ended the given time ago. */
  private void endedAgo(long attemptId, String interval) {
    jdbc.update(
        "update app.quiz_attempts set started_at = now() - ?::interval - interval '5 minutes',"
            + " expires_at = now() - ?::interval where id = ?",
        interval,
        interval,
        attemptId);
  }

  private String statusAndScore(long attemptId) {
    return jdbc.queryForObject(
        "select status || '|' || coalesce(score::text, 'null') from app.quiz_attempts where id = ?",
        String.class,
        attemptId);
  }

  private int attemptsOf(String rollNo) {
    return jdbc.queryForObject(
        "select count(*) from app.quiz_attempts a join app.student_profiles s on s.id = a.student_id"
            + " where s.roll_no = ?",
        Integer.class,
        rollNo);
  }
}
