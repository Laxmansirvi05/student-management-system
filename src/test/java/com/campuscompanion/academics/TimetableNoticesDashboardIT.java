package com.campuscompanion.academics;

import static com.campuscompanion.support.ApiTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.support.ApiIntegrationTest;
import com.campuscompanion.support.ApiTestClient;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Tasks T2-11, T2-12 and T2-14 over real HTTP: the timetable, notices and the student dashboard
 * (FR-TT-001, FR-NOT-001, FR-ADM-005, FR-ADM-006, FR-STU-001). Covers scenario Z8. All data is
 * synthetic.
 */
class TimetableNoticesDashboardIT extends ApiIntegrationTest {

  private ApiTestClient admin;

  @BeforeEach
  void setUp() throws Exception {
    admin = adminClient("ttn.admin@university.example");
  }

  @Test
  void timetableRejectsOverlapsAndShowsEachStudentTheirOwnSection() throws Exception {
    long subjectId = subject(admin, "TTN101", "TTN", 3);
    accounts.student("TTN23001", "TTN", 3, "A");
    accounts.student("TTN23002", "TTN", 3, "B");

    long nine = entry(subjectId, "A", 1, "09:00", "10:00", 201);
    assertProblem(
        admin.post("/api/v1/admin/timetable", slot(subjectId, "A", 1, "09:30", "10:30")),
        409,
        "CONFLICT");
    entry(subjectId, "A", 1, "10:00", "11:00", 201);
    entry(subjectId, "B", 1, "09:00", "10:00", 201);
    entry(subjectId, "A", 2, "09:00", "10:00", 201);
    assertThat(
            invalidFields(
                admin.post("/api/v1/admin/timetable", slot(subjectId, "A", 3, "10:00", "09:00"))))
        .containsExactly("endTime");
    assertThat(
            invalidFields(
                admin.post("/api/v1/admin/timetable", slot(subjectId, "A", 3, "25:00", "26:00"))))
        .containsExactly("startTime");
    assertThat(
            invalidFields(
                admin.post("/api/v1/admin/timetable", slot(subjectId, "A", 3, "9am", "10:00"))))
        .containsExactly("startTime");
    assertThat(
            invalidFields(
                admin.post("/api/v1/admin/timetable", slot(999999, "A", 3, "09:00", "10:00"))))
        .containsExactly("subjectId");
    assertProblem(
        admin.put("/api/v1/admin/timetable/" + nine, slot(subjectId, "A", 1, "10:30", "11:30")),
        409,
        "CONFLICT");
    assertThat(
            admin
                .put("/api/v1/admin/timetable/" + nine, slot(subjectId, "A", 1, "08:00", "09:00"))
                .statusCode())
        .as("an entry does not overlap itself")
        .isEqualTo(200);

    ApiTestClient student = studentClient("TTN23001");
    JsonNode week = json(student.get("/api/v1/timetable/me"));
    assertThat(texts(week, "startTime"))
        .as("day and time order")
        .containsExactly("08:00", "10:00", "09:00");
    assertThat(week.get(0).get("subjectCode").asString()).isEqualTo("TTN101");
    assertThat(week.get(0).get("room").asString()).isEqualTo("Room 1");
    assertThat(json(student.get("/api/v1/timetable/me?day=1")).size()).isEqualTo(2);
    assertThat(json(student.get("/api/v1/timetable/me?day=7")).size()).isZero();
    assertProblem(student.get("/api/v1/timetable/me?day=8"), 400, "VALIDATION_FAILED");
    assertThat(json(studentClient("TTN23002").get("/api/v1/timetable/me")).size())
        .as("the other section has its own timetable")
        .isEqualTo(1);

    assertThat(admin.delete("/api/v1/admin/timetable/" + nine).statusCode()).isEqualTo(204);
    assertThat(json(student.get("/api/v1/timetable/me")).size()).isEqualTo(2);
    assertThat(json(admin.get("/api/v1/admin/timetable?branch=TTN&semester=3&section=A")).size())
        .isEqualTo(2);

    assertProblem(
        student.post("/api/v1/admin/timetable", slot(subjectId, "A", 5, "09:00", "10:00")),
        403,
        "ACCESS_DENIED");
    assertProblem(student.delete("/api/v1/admin/timetable/" + nine), 403, "ACCESS_DENIED");
    assertProblem(admin.get("/api/v1/timetable/me"), 403, "ACCESS_DENIED");
    assertProblem(visitor().get("/api/v1/timetable/me"), 401, "AUTH_REQUIRED");
  }

  @Test
  void studentsSeeOnlyPublishedNoticesMeantForThem() throws Exception {
    accounts.student("TTN23010", "TTNN", 3, "A");
    ApiTestClient student = studentClient("TTN23010");

    long draft = notice("TTNN draft notice", "GENERAL", null, null, null);
    assertThat(texts(json(student.get("/api/v1/notices")).get("content"), "title"))
        .doesNotContain("TTNN draft notice");
    assertProblem(student.get("/api/v1/notices/" + draft), 404, "NOT_FOUND");

    publish(draft);
    long forClass = publish(notice("TTNN for my class", "EXAM", "ttnn", 3, today().plusDays(3)));
    long otherBranch = publish(notice("TTNN for another branch", "EXAM", "ELSE", 3, null));
    long otherSemester = publish(notice("TTNN for another semester", "EVENT", "TTNN", 5, null));

    JsonNode visible = json(student.get("/api/v1/notices?size=100")).get("content");
    assertThat(texts(visible, "title"))
        .contains("TTNN draft notice", "TTNN for my class")
        .doesNotContain("TTNN for another branch", "TTNN for another semester");
    assertThat(texts(visible, "title").indexOf("TTNN for my class"))
        .as("newest first")
        .isLessThan(texts(visible, "title").indexOf("TTNN draft notice"));
    assertThat(
            texts(
                json(student.get("/api/v1/notices?category=EXAM&size=100")).get("content"),
                "title"))
        .contains("TTNN for my class")
        .doesNotContain("TTNN draft notice");
    assertThat(json(student.get("/api/v1/notices/" + forClass)).get("dueDate").asString())
        .isEqualTo(today().plusDays(3).toString());
    assertProblem(student.get("/api/v1/notices/" + otherBranch), 404, "NOT_FOUND");
    assertProblem(student.get("/api/v1/notices/" + otherSemester), 404, "NOT_FOUND");
    assertProblem(student.get("/api/v1/notices?category=PARTY"), 400, "VALIDATION_FAILED");

    assertThat(admin.post("/api/v1/admin/notices/" + draft + "/unpublish", "").statusCode())
        .isEqualTo(204);
    assertProblem(student.get("/api/v1/notices/" + draft), 404, "NOT_FOUND");
    JsonNode all = json(admin.get("/api/v1/admin/notices?size=100")).get("content");
    assertThat(texts(all, "title"))
        .as("the admin sees drafts and every target")
        .contains("TTNN draft notice", "TTNN for another branch");
    assertThat(texts(json(admin.get("/api/v1/notices?size=100")).get("content"), "title"))
        .as("an admin reading the student list sees every published notice")
        .contains("TTNN for another branch")
        .doesNotContain("TTNN draft notice");

    HttpResponse<String> edited =
        admin.put(
            "/api/v1/admin/notices/" + forClass,
            noticeJson("TTNN for my class, edited", "EXAM", "TTNN", 3, null));
    assertThat(json(edited).get("title").asString()).isEqualTo("TTNN for my class, edited");
    assertThat(json(edited).get("status").asString())
        .as("editing keeps it published")
        .isEqualTo("PUBLISHED");
    assertThat(admin.delete("/api/v1/admin/notices/" + otherBranch).statusCode()).isEqualTo(204);
    assertProblem(admin.delete("/api/v1/admin/notices/" + otherBranch), 404, "NOT_FOUND");

    assertThat(
            invalidFields(
                admin.post("/api/v1/admin/notices", noticeJson("", "GENERAL", null, null, null))))
        .containsExactly("title");
    assertThat(
            invalidFields(
                admin.post(
                    "/api/v1/admin/notices",
                    noticeJson("Long", "GENERAL", null, null, null)
                        .replace("Synthetic text.", "x".repeat(5001)))))
        .containsExactly("body");
    assertThat(
            invalidFields(
                admin.post(
                    "/api/v1/admin/notices", noticeJson("Bad target", "GENERAL", null, 9, null))))
        .containsExactly("targetSemester");
    assertProblem(
        student.post("/api/v1/admin/notices", noticeJson("Mine", "GENERAL", null, null, null)),
        403,
        "ACCESS_DENIED");
    assertProblem(
        student.post("/api/v1/admin/notices/" + draft + "/publish", ""), 403, "ACCESS_DENIED");
    assertProblem(student.get("/api/v1/admin/notices"), 403, "ACCESS_DENIED");
    assertProblem(visitor().get("/api/v1/notices"), 401, "AUTH_REQUIRED");
  }

  @Test
  void theDashboardSummarisesEachFeatureForTheLoggedInStudent() throws Exception {
    long studentId = accounts.student("TTN23020", "TTND", 3, "A");
    ApiTestClient student = studentClient("TTN23020");
    JsonNode empty = json(student.get("/api/v1/students/me/dashboard"));
    assertThat(empty.get("attendance").get("overallPercent").isNull()).isTrue();
    assertThat(empty.get("results").get("cgpa").isNull()).isTrue();
    assertThat(empty.get("todayClasses").size()).isZero();
    assertThat(empty.get("recommendations").size()).isZero();

    long subjectId = subject(admin, "TTN301", "TTND", 3);
    for (int day = 1; day <= 7; day++) {
      admin.post(
          "/api/v1/admin/timetable",
          slot(subjectId, "A", day, "09:00", "10:00").replace("\"TTN\"", "\"TTND\""));
    }
    admin.put(
        "/api/v1/admin/attendance",
        "{\"subjectId\":%d,\"section\":\"A\",\"classDate\":\"%s\",\"periodNo\":1,\"entries\":[{\"studentId\":%d,\"status\":\"ABSENT\"}]}"
            .formatted(subjectId, today().minusDays(1), studentId));
    admin.put(
        "/api/v1/admin/semester-results",
        "{\"studentId\":%d,\"semester\":1,\"sgpa\":7.5,\"creditsRegistered\":20,\"creditsEarned\":20,\"resultStatus\":\"PASS\",\"published\":true}"
            .formatted(studentId));
    admin.put(
        "/api/v1/admin/semester-results",
        "{\"studentId\":%d,\"semester\":2,\"sgpa\":8.5,\"creditsRegistered\":20,\"creditsEarned\":20,\"resultStatus\":\"PASS\",\"published\":true}"
            .formatted(studentId));
    publish(notice("TTND due soon", "ASSIGNMENT", "TTND", 3, today().plusDays(3)));
    publish(notice("TTND due later", "ASSIGNMENT", "TTND", 3, today().plusDays(20)));
    publish(notice("TTND was due", "ASSIGNMENT", "TTND", 3, today().minusDays(1)));
    for (int number = 1; number <= 4; number++) {
      publish(notice("TTND notice " + number, "GENERAL", "TTND", 3, null));
    }

    JsonNode dashboard = json(student.get("/api/v1/students/me/dashboard"));

    assertThat(dashboard.get("attendance").get("overallPercent").decimalValue())
        .isEqualByComparingTo("0.00");
    assertThat(dashboard.get("attendance").get("requiredPercent").decimalValue())
        .isEqualByComparingTo("75");
    assertThat(dashboard.get("attendance").get("subjectsBelowRequired").asInt()).isEqualTo(1);
    assertThat(dashboard.get("results").get("latestSgpa").decimalValue())
        .isEqualByComparingTo("8.5");
    assertThat(dashboard.get("results").get("cgpa").decimalValue()).isEqualByComparingTo("8.00");
    assertThat(dashboard.get("todayClasses").size())
        .as("one class every day in this test")
        .isEqualTo(1);
    assertThat(dashboard.get("todayClasses").get(0).get("subjectCode").asString())
        .isEqualTo("TTN301");
    assertThat(texts(dashboard.get("upcomingDeadlines"), "title"))
        .as("due within the next 14 days, and not yet passed")
        .contains("TTND due soon")
        .doesNotContain("TTND due later", "TTND was due");
    assertThat(dashboard.get("recentNotices").size()).as("the five newest").isEqualTo(5);
    assertThat(texts(dashboard.get("recentNotices"), "title"))
        .contains("TTND notice 4")
        .doesNotContain("TTND due soon");

    assertProblem(admin.get("/api/v1/students/me/dashboard"), 403, "ACCESS_DENIED");
    assertProblem(visitor().get("/api/v1/students/me/dashboard"), 401, "AUTH_REQUIRED");
  }

  private long entry(
      long subjectId, String section, int day, String start, String end, int expected)
      throws Exception {
    HttpResponse<String> response =
        admin.post("/api/v1/admin/timetable", slot(subjectId, section, day, start, end));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(expected);
    return json(response).get("id").asLong();
  }

  private static String slot(long subjectId, String section, int day, String start, String end) {
    return """
        {"branch":"TTN","semester":3,"section":"%s","dayOfWeek":%d,"startTime":"%s","endTime":"%s","subjectId":%d,"room":"Room 1","facultyName":"Faculty A"}"""
        .formatted(section, day, start, end, subjectId);
  }

  private long notice(
      String title, String category, String branch, Integer semester, LocalDate dueDate)
      throws Exception {
    HttpResponse<String> response =
        admin.post("/api/v1/admin/notices", noticeJson(title, category, branch, semester, dueDate));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    assertThat(json(response).get("status").asString())
        .as("a new notice is a draft")
        .isEqualTo("DRAFT");
    return json(response).get("id").asLong();
  }

  private long publish(long id) throws Exception {
    assertThat(admin.post("/api/v1/admin/notices/" + id + "/publish", "").statusCode())
        .isEqualTo(204);
    return id;
  }

  private static String noticeJson(
      String title, String category, String branch, Integer semester, LocalDate dueDate) {
    return """
        {"title":"%s","body":"Synthetic text.","category":"%s","targetBranch":%s,"targetSemester":%s,"dueDate":%s}"""
        .formatted(
            title,
            category,
            branch == null ? "null" : "\"" + branch + "\"",
            semester == null ? "null" : semester.toString(),
            dueDate == null ? "null" : "\"" + dueDate + "\"");
  }
}
