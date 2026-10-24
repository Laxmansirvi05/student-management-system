package com.campuscompanion.planner;

import static com.campuscompanion.support.ApiTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.support.ApiIntegrationTest;
import com.campuscompanion.support.ApiTestClient;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Study tasks and the exam countdown (T7-02, FR-PLN-001, FR-PLN-002) through the API: what a
 * student can do with their own, what they can never do with someone else's, the checks on input,
 * and the college's exam dates joining the countdown. All data is synthetic.
 */
class PlannerIT extends ApiIntegrationTest {

  private static final String TASKS = "/api/v1/planner/tasks";
  private static final String EXAMS = "/api/v1/planner/exams";
  private static final String ADMIN = "planner-admin@university.example";

  private ApiTestClient student(String rollNo) throws Exception {
    accounts.student(rollNo, "PLN", 3, "A");
    return studentClient(rollNo);
  }

  private static String task(String title, String notes, Long subjectId, LocalDate due) {
    return "{\"title\":%s,\"notes\":%s,\"subjectId\":%s,\"dueDate\":%s}"
        .formatted(
            title == null ? "null" : "\"" + title + "\"",
            notes == null ? "null" : "\"" + notes + "\"",
            subjectId,
            due == null ? "null" : "\"" + due + "\"");
  }

  private static String exam(String name, Long subjectId, LocalDate date) {
    return "{\"name\":%s,\"subjectId\":%s,\"examDate\":%s}"
        .formatted(
            name == null ? "null" : "\"" + name + "\"",
            subjectId,
            date == null ? "null" : "\"" + date + "\"");
  }

  private long createTask(ApiTestClient who, String body) throws Exception {
    HttpResponse<String> created = who.post(TASKS, body);
    assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
    return json(created).get("id").asLong();
  }

  private long createExam(ApiTestClient who, String body) throws Exception {
    HttpResponse<String> created = who.post(EXAMS, body);
    assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
    return json(created).get("id").asLong();
  }

  private JsonNode tasks(ApiTestClient who, String view) throws Exception {
    HttpResponse<String> response = who.get(TASKS + "?view=" + view);
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    return json(response);
  }

  private static List<String> titles(JsonNode list) {
    return list.valueStream().map(node -> node.get("title").asString()).toList();
  }

  // --- access ----------------------------------------------------------------------------------

  @Test
  void onlyALoggedInStudentCanUseThePlanner() throws Exception {
    ApiTestClient admin = adminClient(ADMIN);
    for (String path : List.of(TASKS, EXAMS)) {
      assertProblem(visitor().get(path), 401, "AUTH_REQUIRED");
      assertProblem(admin.get(path), 403, "ACCESS_DENIED");
    }
    assertProblem(admin.post(TASKS, task("x", null, null, null)), 403, "ACCESS_DENIED");
  }

  @Test
  void anotherStudentsTasksAndExamsAreNeverSeenOrChanged() throws Exception {
    ApiTestClient owner = student("23PLN001");
    ApiTestClient other = student("23PLN002");
    long taskId = createTask(owner, task("Read chapter 4", null, null, today().plusDays(2)));
    long examId = createExam(owner, exam("Data Structures mid", null, today().plusDays(9)));

    assertThat(titles(tasks(other, "all").get("tasks"))).isEmpty();
    assertThat(json(other.get(EXAMS)).get("upcoming")).isEmpty();
    assertProblem(
        other.put(TASKS + "/" + taskId, task("Stolen", null, null, null)), 404, "NOT_FOUND");
    assertProblem(other.post(TASKS + "/" + taskId + "/complete", ""), 404, "NOT_FOUND");
    assertProblem(other.post(TASKS + "/" + taskId + "/reopen", ""), 404, "NOT_FOUND");
    assertProblem(other.delete(TASKS + "/" + taskId), 404, "NOT_FOUND");
    assertProblem(other.put(EXAMS + "/" + examId, exam("Stolen", null, today())), 404, "NOT_FOUND");
    assertProblem(other.delete(EXAMS + "/" + examId), 404, "NOT_FOUND");

    assertThat(titles(tasks(owner, "pending").get("tasks"))).containsExactly("Read chapter 4");
    assertThat(json(owner.get(EXAMS)).get("upcoming").get(0).get("name").asString())
        .isEqualTo("Data Structures mid");
  }

  // --- study tasks -----------------------------------------------------------------------------

  @Test
  void aTaskIsCreatedEditedCompletedReopenedAndDeleted() throws Exception {
    ApiTestClient me = student("23PLN003");
    long subjectId = subject(adminClient(ADMIN), "PLN301", "PLN", 3);

    HttpResponse<String> created =
        me.post(TASKS, task("  Revise trees  ", "Unit 3 and 4", subjectId, today().plusDays(3)));
    assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
    JsonNode task = json(created);
    long id = task.get("id").asLong();
    assertThat(task.get("title").asString()).isEqualTo("Revise trees");
    assertThat(task.get("notes").asString()).isEqualTo("Unit 3 and 4");
    assertThat(task.get("subject").get("code").asString()).isEqualTo("PLN301");
    assertThat(task.get("status").asString()).isEqualTo("PENDING");
    assertThat(task.get("overdue").asBoolean()).isFalse();

    JsonNode edited =
        json(me.put(TASKS + "/" + id, task("Revise graphs", "", null, today().plusDays(5))));
    assertThat(edited.get("title").asString()).isEqualTo("Revise graphs");
    assertThat(edited.get("notes").isNull()).isTrue();
    assertThat(edited.get("subject").isNull()).isTrue();
    assertThat(edited.get("dueDate").asString()).isEqualTo(today().plusDays(5).toString());

    JsonNode done = json(me.post(TASKS + "/" + id + "/complete", ""));
    assertThat(done.get("status").asString()).isEqualTo("DONE");
    String completedAt = done.get("completedAt").asString();
    // Completing again changes nothing.
    assertThat(json(me.post(TASKS + "/" + id + "/complete", "")).get("completedAt").asString())
        .isEqualTo(completedAt);
    assertThat(titles(tasks(me, "done").get("tasks"))).containsExactly("Revise graphs");
    assertThat(titles(tasks(me, "pending").get("tasks"))).isEmpty();

    JsonNode reopened = json(me.post(TASKS + "/" + id + "/reopen", ""));
    assertThat(reopened.get("status").asString()).isEqualTo("PENDING");
    assertThat(reopened.get("completedAt").isNull()).isTrue();

    assertThat(me.delete(TASKS + "/" + id).statusCode()).isEqualTo(204);
    assertThat(titles(tasks(me, "all").get("tasks"))).isEmpty();
    assertProblem(me.delete(TASKS + "/" + id), 404, "NOT_FOUND");
  }

  @Test
  void overdueTasksAreFlaggedCountedAndListedSoonestFirst() throws Exception {
    ApiTestClient me = student("23PLN004");
    createTask(me, task("No date", null, null, null));
    createTask(me, task("Next week", null, null, today().plusDays(7)));
    createTask(me, task("Today", null, null, today()));
    long late = createTask(me, task("Was due", null, null, today().plusDays(1)));
    long finished = createTask(me, task("Finished", null, null, today().plusDays(1)));
    // Time passes: the due date is now behind us.
    jdbc.update(
        "update app.study_tasks set due_date = ? where id in (?, ?)",
        today().minusDays(2),
        late,
        finished);
    me.post(TASKS + "/" + finished + "/complete", "");

    JsonNode pending = tasks(me, "pending");
    assertThat(titles(pending.get("tasks")))
        .containsExactly("Was due", "Today", "Next week", "No date");
    assertThat(pending.get("tasks").get(0).get("overdue").asBoolean()).isTrue();
    assertThat(pending.get("tasks").get(1).get("overdue").asBoolean()).isFalse();
    assertThat(pending.get("counts").get("pending").asInt()).isEqualTo(4);
    assertThat(pending.get("counts").get("overdue").asInt()).isEqualTo(1);
    assertThat(pending.get("counts").get("done").asInt()).isEqualTo(1);
    assertThat(titles(tasks(me, "overdue").get("tasks"))).containsExactly("Was due");
    // A done task is never overdue, whatever its date.
    JsonNode done = tasks(me, "done").get("tasks").get(0);
    assertThat(done.get("overdue").asBoolean()).isFalse();
    assertThat(titles(tasks(me, "all").get("tasks")))
        .containsExactly("Was due", "Today", "Next week", "No date", "Finished");

    // An overdue task can still be edited without moving its date.
    HttpResponse<String> kept =
        me.put(TASKS + "/" + late, task("Was due (started)", null, null, today().minusDays(2)));
    assertThat(kept.statusCode()).as(kept.body()).isEqualTo(200);
  }

  @Test
  void invalidTasksAreRefusedWithTheFieldNamed() throws Exception {
    ApiTestClient me = student("23PLN005");
    long otherBranchSubject = subject(adminClient(ADMIN), "XYZ301", "XYZ", 3);

    assertThat(invalidFields(me.post(TASKS, task(null, null, null, null))))
        .containsExactly("title");
    assertThat(invalidFields(me.post(TASKS, task("   ", null, null, null))))
        .containsExactly("title");
    assertThat(invalidFields(me.post(TASKS, task("t".repeat(201), null, null, null))))
        .containsExactly("title");
    assertThat(invalidFields(me.post(TASKS, task("ok", "n".repeat(1001), null, null))))
        .containsExactly("notes");
    assertThat(invalidFields(me.post(TASKS, task("ok", null, null, today().minusDays(1)))))
        .containsExactly("dueDate");
    assertThat(invalidFields(me.post(TASKS, task("ok", null, null, today().plusYears(6)))))
        .containsExactly("dueDate");
    assertThat(invalidFields(me.post(TASKS, task("ok", null, otherBranchSubject, null))))
        .containsExactly("subjectId");
    assertThat(invalidFields(me.post(TASKS, task("ok", null, 999999L, null))))
        .containsExactly("subjectId");
    assertProblem(
        me.post(TASKS, "{\"title\":\"ok\",\"dueDate\":\"next friday\"}"), 400, "VALIDATION_FAILED");
    assertProblem(me.post(TASKS, "{\"title\":\"ok\",\"studentId\":1}"), 400, "VALIDATION_FAILED");
    assertProblem(me.get(TASKS + "?view=everything"), 400, "VALIDATION_FAILED");
    assertThat(titles(tasks(me, "all").get("tasks"))).isEmpty();
  }

  // --- exam countdown --------------------------------------------------------------------------

  @Test
  void examsAreAddedEditedDeletedAndListedInDateOrderWithPastOnesApart() throws Exception {
    ApiTestClient me = student("23PLN006");
    long later = createExam(me, exam("Maths end-sem", null, today().plusDays(20)));
    long sooner = createExam(me, exam("DBMS lab", null, today().plusDays(3)));
    long onToday = createExam(me, exam("Viva", null, today()));
    long gone = createExam(me, exam("Quiz 1", null, today().plusDays(1)));
    jdbc.update(
        "update app.student_exams set exam_date = ? where id = ?", today().minusDays(4), gone);

    JsonNode list = json(me.get(EXAMS));
    assertThat(list.get("upcoming").valueStream().map(e -> e.get("name").asString()).toList())
        .containsExactly("Viva", "DBMS lab", "Maths end-sem");
    assertThat(list.get("upcoming").get(0).get("past").asBoolean()).isFalse();
    assertThat(list.get("upcoming").get(0).get("source").asString()).isEqualTo("MINE");
    assertThat(list.get("past")).hasSize(1);
    assertThat(list.get("past").get(0).get("name").asString()).isEqualTo("Quiz 1");
    assertThat(list.get("past").get(0).get("past").asBoolean()).isTrue();

    JsonNode edited =
        json(
            me.put(EXAMS + "/" + later, exam("Maths end-sem (hall 2)", null, today().plusDays(1))));
    assertThat(edited.get("examDate").asString()).isEqualTo(today().plusDays(1).toString());
    assertThat(
            json(me.get(EXAMS))
                .get("upcoming")
                .valueStream()
                .map(e -> e.get("name").asString())
                .toList())
        .containsExactly("Viva", "Maths end-sem (hall 2)", "DBMS lab");
    // A past exam can be renamed without moving its date, but not moved to another past date.
    assertThat(
            me.put(EXAMS + "/" + gone, exam("Quiz 1 (done)", null, today().minusDays(4)))
                .statusCode())
        .isEqualTo(200);
    assertThat(
            invalidFields(me.put(EXAMS + "/" + gone, exam("Quiz 1", null, today().minusDays(5)))))
        .containsExactly("examDate");

    assertThat(me.delete(EXAMS + "/" + sooner).statusCode()).isEqualTo(204);
    assertThat(me.delete(EXAMS + "/" + onToday).statusCode()).isEqualTo(204);
    assertThat(json(me.get(EXAMS)).get("upcoming")).hasSize(1);
  }

  @Test
  void invalidExamsAreRefusedWithTheFieldNamed() throws Exception {
    ApiTestClient me = student("23PLN007");
    assertThat(invalidFields(me.post(EXAMS, exam(null, null, today())))).containsExactly("name");
    assertThat(invalidFields(me.post(EXAMS, exam("x".repeat(121), null, today()))))
        .containsExactly("name");
    assertThat(invalidFields(me.post(EXAMS, exam("Mid", null, null)))).containsExactly("examDate");
    assertThat(invalidFields(me.post(EXAMS, exam("Mid", null, today().minusDays(1)))))
        .containsExactly("examDate");
    assertThat(invalidFields(me.post(EXAMS, exam("Mid", 999999L, today()))))
        .containsExactly("subjectId");
  }

  @Test
  void theCollegesExamNoticesJoinTheCountdownAndTheDashboardShowsTheNextExam() throws Exception {
    ApiTestClient admin = adminClient(ADMIN);
    ApiTestClient me = student("23PLN008");
    ApiTestClient otherClass = student("23PLN009");
    jdbc.update("update app.student_profiles set branch = 'QQQ' where roll_no = ?", "23PLN009");

    // Published exam notices with a due date, for this class or for everyone, count; drafts,
    // other categories and other classes do not.
    long forClass =
        publish(
            admin, notice(admin, "Mid-term exams begin", "EXAM", "PLN", 3, today().plusDays(10)));
    publish(admin, notice(admin, "Fee deadline", "GENERAL", null, null, today().plusDays(2)));
    publish(admin, notice(admin, "Other branch exam", "EXAM", "ZZZ", 3, today().plusDays(1)));
    notice(admin, "Draft exam", "EXAM", null, null, today().plusDays(1));
    publish(admin, notice(admin, "Exam without a date", "EXAM", null, null, null));
    createExam(me, exam("My lab test", null, today().plusDays(4)));

    JsonNode upcoming = json(me.get(EXAMS)).get("upcoming");
    assertThat(upcoming.valueStream().map(e -> e.get("name").asString()).toList())
        .containsExactly("My lab test", "Mid-term exams begin");
    JsonNode college = upcoming.get(1);
    assertThat(college.get("source").asString()).isEqualTo("COLLEGE");
    assertThat(college.get("noticeId").asLong()).isEqualTo(forClass);
    assertThat(college.get("id").isNull()).isTrue();

    JsonNode dashboard = json(me.get("/api/v1/students/me/dashboard"));
    assertThat(dashboard.get("nextExam").get("name").asString()).isEqualTo("My lab test");
    assertThat(dashboard.get("nextExam").get("examDate").asString())
        .isEqualTo(today().plusDays(4).toString());
    // Another class sees neither this student's exam nor this class's notice.
    assertThat(json(otherClass.get(EXAMS)).get("upcoming")).isEmpty();
    assertThat(json(otherClass.get("/api/v1/students/me/dashboard")).get("nextExam").isNull())
        .isTrue();
  }

  @Test
  void aTimeTableWithPapersGivesOneCollegeExamPerPaper() throws Exception {
    ApiTestClient admin = adminClient(ADMIN);
    ApiTestClient me = student("23PLN010");
    String papers =
        "[{\"examDate\":\"%s\",\"subject\":\"  Advanced Data Structures \"},{\"examDate\":\"%s\",\"subject\":\"Java\"},{\"examDate\":\"%s\",\"subject\":\"Already written\"}]"
            .formatted(today().plusDays(5), today().plusDays(3), today().minusDays(1));
    HttpResponse<String> created =
        admin.post(
            "/api/v1/admin/notices",
            "{\"title\":\"Semester exams: time table\",\"body\":\"Synthetic text.\",\"category\":\"EXAM\",\"targetBranch\":\"PLN\",\"targetSemester\":3,\"dueDate\":\"%s\",\"examPapers\":%s}"
                .formatted(today().plusDays(3), papers));
    assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
    JsonNode notice = json(created);
    assertThat(notice.get("examPapers").size()).isEqualTo(3);
    assertThat(notice.get("examPapers").get(0).get("subject").asString())
        .as("in date order, trimmed")
        .isEqualTo("Already written");
    publish(admin, notice.get("id").asLong());

    JsonNode upcoming = json(me.get(EXAMS)).get("upcoming");
    assertThat(upcoming.valueStream().map(e -> e.get("name").asString()).toList())
        .as("one exam per paper still to come, not the notice's own date")
        .containsSubsequence("Java", "Advanced Data Structures")
        .doesNotContain("Semester exams: time table", "Already written");
    JsonNode onDashboard = json(me.get("/api/v1/students/me/dashboard")).get("upcomingExams");
    assertThat(onDashboard.valueStream().map(e -> e.get("name").asString()).toList())
        .contains("Java", "Advanced Data Structures");

    // Editing without the field keeps the papers; only exam notices may have them.
    long id = notice.get("id").asLong();
    HttpResponse<String> kept =
        admin.put(
            "/api/v1/admin/notices/" + id,
            "{\"title\":\"Semester exams: time table\",\"body\":\"Edited.\",\"category\":\"EXAM\",\"targetBranch\":\"PLN\",\"targetSemester\":3}");
    assertThat(json(kept).get("examPapers").size()).isEqualTo(3);
    assertProblem(
        admin.put(
            "/api/v1/admin/notices/" + id,
            "{\"title\":\"t\",\"body\":\"b\",\"category\":\"GENERAL\",\"examPapers\":%s}"
                .formatted(papers)),
        400,
        "VALIDATION_FAILED");
    assertProblem(
        admin.put(
            "/api/v1/admin/notices/" + id,
            "{\"title\":\"t\",\"body\":\"b\",\"category\":\"EXAM\",\"examPapers\":[{\"examDate\":null,\"subject\":\"x\"}]}"),
        400,
        "VALIDATION_FAILED");
  }

  private static long notice(
      ApiTestClient admin,
      String title,
      String category,
      String branch,
      Integer semester,
      LocalDate due)
      throws Exception {
    String body =
        "{\"title\":\"%s\",\"body\":\"Synthetic text.\",\"category\":\"%s\",\"targetBranch\":%s,\"targetSemester\":%s,\"dueDate\":%s}"
            .formatted(
                title,
                category,
                branch == null ? "null" : "\"" + branch + "\"",
                semester == null ? "null" : semester.toString(),
                due == null ? "null" : "\"" + due + "\"");
    HttpResponse<String> response = admin.post("/api/v1/admin/notices", body);
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    return json(response).get("id").asLong();
  }

  private static long publish(ApiTestClient admin, long id) throws Exception {
    assertThat(admin.post("/api/v1/admin/notices/" + id + "/publish", "").statusCode())
        .isEqualTo(204);
    return id;
  }
}
