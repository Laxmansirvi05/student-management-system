package com.campuscompanion.academics;

import static com.campuscompanion.support.ApiTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.support.ApiTestClient;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Tasks T2-08 to T2-10 over real HTTP: assessments, marks sheets, semester results and the CGPA
 * (FR-ADM-003, FR-ADM-004, FR-MRK-001, FR-MRK-002). Covers scenarios Z6 and Z8. All data is
 * synthetic.
 */
class MarksAndResultsApiIT extends AcademicApiTest {

  private ApiTestClient admin;
  private long first;
  private long second;

  @BeforeEach
  void setUp() throws Exception {
    admin = adminClient("mrk.admin@university.example");
    first = accounts.student("MRK23001", "MRK", 3, "A");
    second = accounts.student("MRK23002", "MRK", 3, "B");
  }

  @Test
  void studentsSeeOnlyPublishedMarksAndAbsentIsNotZero() throws Exception {
    long subjectId = subject(admin, "MRK101", "MRK", 3);
    long internal = assessment(subjectId, "Internal 1", "INTERNAL", "50", true);
    long assignment = assessment(subjectId, "Assignment 1", "ASSIGNMENT", "20", true);
    long hidden = assessment(subjectId, "Internal 2", "INTERNAL", "50", false);
    saveMarks(internal, marks(first, "44", false), marks(second, "38.5", false));
    saveMarks(assignment, marks(first, "null", true), marks(second, "17.5", false));
    saveMarks(hidden, marks(first, "40", false), marks(second, "41", false));

    HttpResponse<String> response = studentClient("MRK23001").get("/api/v1/marks/me");

    assertThat(response.body())
        .as("an unpublished assessment is never returned")
        .doesNotContain("Internal 2");
    JsonNode subject = onlySubject(json(response), "MRK101");
    assertThat(texts(subject.get("assessments"), "name"))
        .containsExactly("Internal 1", "Assignment 1");
    JsonNode marked = subject.get("assessments").get(0);
    assertThat(marked.get("marksObtained").decimalValue()).isEqualByComparingTo("44");
    assertThat(marked.get("percent").decimalValue()).isEqualByComparingTo("88.00");
    assertThat(marked.get("absent").asBoolean()).isFalse();
    JsonNode missed = subject.get("assessments").get(1);
    assertThat(missed.get("absent").asBoolean()).isTrue();
    assertThat(missed.get("marksObtained").isNull()).as("absent is not zero").isTrue();
    assertThat(missed.get("percent").isNull()).isTrue();

    assertThat(json(studentClient("MRK23001").get("/api/v1/marks/me?subjectId=999999")).size())
        .isZero();
    assertThat(json(studentClient("MRK23001").get("/api/v1/marks/me?semester=5")).size()).isZero();
    JsonNode others =
        onlySubject(json(studentClient("MRK23002").get("/api/v1/marks/me")), "MRK101");
    assertThat(others.get("assessments").get(0).get("marksObtained").decimalValue())
        .as("each student sees their own marks")
        .isEqualByComparingTo("38.5");

    assertProblem(
        admin.post(
            "/api/v1/admin/assessments",
            assessmentJson(subjectId, "Internal 1", "INTERNAL", "50", true)),
        409,
        "CONFLICT");
    assertThat(
            invalidFields(
                admin.post(
                    "/api/v1/admin/assessments",
                    assessmentJson(subjectId, "Quiz", "INTERNAL", "0", true))))
        .containsExactly("maxMarks");
    assertThat(
            invalidFields(
                admin.post(
                    "/api/v1/admin/assessments",
                    assessmentJson(subjectId, "Quiz", "INTERNAL", "10", true)
                        .replace("2026-27", "2026"))))
        .containsExactly("academicYear");
    assertThat(
            invalidFields(
                admin.post(
                    "/api/v1/admin/assessments",
                    assessmentJson(999999, "Quiz", "INTERNAL", "10", true))))
        .containsExactly("subjectId");
    assertThat(texts(json(admin.get("/api/v1/admin/assessments?subjectId=" + subjectId)), "name"))
        .containsExactly("Internal 1", "Assignment 1", "Internal 2");
  }

  @Test
  void aMarksSheetIsSavedWholeOrNotAtAllAndChangesAreAudited() throws Exception {
    long subjectId = subject(admin, "MRK201", "MRK", 3);
    long assessmentId = assessment(subjectId, "Assignment 1", "ASSIGNMENT", "20", true);
    long outsider = accounts.student("MRK23099", "MRKX", 3, "A");

    assertThat(
            invalidFields(
                saveMarks(assessmentId, marks(first, "18.5", false), marks(second, "21", false))))
        .containsExactly("entries[1].marksObtained");
    assertThat(invalidFields(saveMarks(assessmentId, marks(first, "-1", false))))
        .containsExactly("entries[0].marksObtained");
    assertThat(invalidFields(saveMarks(assessmentId, marks(first, "null", false))))
        .containsExactly("entries[0].marksObtained");
    assertThat(
            invalidFields(
                saveMarks(assessmentId, marks(first, "10", false), marks(outsider, "10", false))))
        .containsExactly("entries[1].studentId");
    assertThat(marksRows(assessmentId))
        .as("nothing is saved from a sheet with an invalid row")
        .isZero();

    JsonNode saved =
        json(saveMarks(assessmentId, marks(first, "18.5", false), marks(second, "12", false)));
    assertThat(saved.get("saved").asInt()).isEqualTo(2);
    assertThat(saved.get("changed").asInt()).isEqualTo(2);
    assertThat(
            json(saveMarks(assessmentId, marks(first, "18.50", false), marks(second, "12", false)))
                .get("changed")
                .asInt())
        .as("the same marks again change nothing")
        .isZero();
    assertThat(
            json(saveMarks(assessmentId, marks(first, "19", false), marks(second, "12", false)))
                .get("changed")
                .asInt())
        .isEqualTo(1);
    assertThat(latestAudit("MARKS_UPDATED"))
        .isEqualTo(
            "{\"absent\": false, \"marksObtained\": 18.50} | {\"absent\": false, \"marksObtained\": 19}");
    assertThat(marksRows(assessmentId)).isEqualTo(2);

    JsonNode sheet = json(admin.get("/api/v1/admin/assessments/" + assessmentId + "/marks"));
    assertThat(texts(sheet.get("entries"), "rollNo")).containsExactly("MRK23001", "MRK23002");
    assertThat(sheet.get("entries").get(0).get("marksObtained").decimalValue())
        .isEqualByComparingTo("19");
    assertThat(
            invalidFields(
                admin.put(
                    "/api/v1/admin/assessments/" + assessmentId,
                    assessmentJson(subjectId, "Assignment 1", "ASSIGNMENT", "15", true))))
        .as("the maximum cannot drop below marks already saved")
        .containsExactly("maxMarks");
    assertProblem(admin.get("/api/v1/admin/assessments/999999/marks"), 404, "NOT_FOUND");
  }

  @Test
  void resultsShowPublishedSemestersAndTheCreditWeightedCgpa() throws Exception {
    long third = accounts.student("MRK23003", "MRK", 3, "A");
    saveResult(first, 1, "6.50", 24, 20, "FAIL", true);
    saveResult(first, 2, "7.80", 22, 22, "PASS", true);
    saveResult(first, 3, "9.90", 20, 20, "PASS", false);
    saveResult(second, 1, "8.00", 20, 20, "PASS", true);
    saveResult(second, 2, "2.00", 20, 0, "WITHHELD", true);

    JsonNode own = json(studentClient("MRK23001").get("/api/v1/results/me"));
    assertThat(own.get("cgpa").decimalValue())
        .as("(7.80 x 22 + 6.50 x 24) / 46")
        .isEqualByComparingTo("7.12");
    assertThat(own.get("semesters").size()).as("the unpublished semester is left out").isEqualTo(2);
    assertThat(own.get("semesters").get(0).get("resultStatus").asString()).isEqualTo("FAIL");
    assertThat(own.get("semesters").get(0).get("creditsEarned").asInt()).isEqualTo(20);

    JsonNode withheld = json(studentClient("MRK23002").get("/api/v1/results/me"));
    assertThat(withheld.get("cgpa").decimalValue())
        .as("a withheld semester is left out of the CGPA")
        .isEqualByComparingTo("8.00");
    assertThat(withheld.get("semesters").size()).isEqualTo(2);

    JsonNode none = json(studentClient("MRK23003").get("/api/v1/results/me"));
    assertThat(none.get("cgpa").isNull()).isTrue();
    assertThat(none.get("semesters").size()).isZero();

    assertThat(invalidFields(saveResult(first, 4, "7.00", 20, 21, "PASS", true)))
        .containsExactly("creditsEarned");
    assertThat(invalidFields(saveResult(first, 4, "10.5", 20, 20, "PASS", true)))
        .containsExactly("sgpa");
    assertThat(invalidFields(saveResult(999999, 4, "7.00", 20, 20, "PASS", true)))
        .containsExactly("studentId");

    int before = resultAudits(first);
    saveResult(first, 1, "6.50", 24, 20, "FAIL", true);
    assertThat(resultAudits(first))
        .as("saving the same values again writes no entry")
        .isEqualTo(before);
    saveResult(first, 1, "6.80", 24, 24, "PASS", true);
    assertThat(resultAudits(first)).isEqualTo(before + 1);
    assertThat(latestAudit("RESULT_UPDATED")).contains("\"sgpa\": 6.5").contains("\"sgpa\": 6.8");
    assertThat(json(admin.get("/api/v1/admin/semester-results?studentId=" + first)).size())
        .as("the admin also sees the unpublished one")
        .isEqualTo(3);
    assertThat(third).isPositive();
  }

  @Test
  void studentsCannotWriteMarksOrResultsAndAdminsHaveNoMarksOfTheirOwn() throws Exception {
    long subjectId = subject(admin, "MRK401", "MRK", 3);
    long assessmentId = assessment(subjectId, "Internal 1", "INTERNAL", "50", false);
    ApiTestClient student = studentClient("MRK23001");

    assertProblem(
        student.put(
            "/api/v1/admin/assessments/" + assessmentId + "/marks",
            "{\"entries\":[" + marks(first, "50", false) + "]}"),
        403,
        "ACCESS_DENIED");
    assertProblem(
        student.post(
            "/api/v1/admin/assessments", assessmentJson(subjectId, "Mine", "INTERNAL", "50", true)),
        403,
        "ACCESS_DENIED");
    assertProblem(
        student.put(
            "/api/v1/admin/assessments/" + assessmentId,
            assessmentJson(subjectId, "Internal 1", "INTERNAL", "50", true)),
        403,
        "ACCESS_DENIED");
    assertProblem(
        student.put(
            "/api/v1/admin/semester-results", resultJson(first, 8, "10.00", 20, 20, "PASS", true)),
        403,
        "ACCESS_DENIED");
    assertProblem(
        student.get("/api/v1/admin/assessments/" + assessmentId + "/marks"), 403, "ACCESS_DENIED");
    assertProblem(admin.get("/api/v1/marks/me"), 403, "ACCESS_DENIED");
    assertProblem(admin.get("/api/v1/results/me"), 403, "ACCESS_DENIED");
    assertProblem(visitor().get("/api/v1/marks/me"), 401, "AUTH_REQUIRED");
    assertProblem(visitor().get("/api/v1/results/me"), 401, "AUTH_REQUIRED");

    assertThat(marksRows(assessmentId)).as("the database is unchanged").isZero();
    assertThat(studentClient("MRK23001").get("/api/v1/marks/me").body())
        .as("the assessment is still unpublished")
        .doesNotContain("\"assessmentId\":" + assessmentId);
  }

  private long assessment(
      long subjectId, String name, String type, String maxMarks, boolean published)
      throws Exception {
    HttpResponse<String> response =
        admin.post(
            "/api/v1/admin/assessments",
            assessmentJson(subjectId, name, type, maxMarks, published));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    return json(response).get("id").asLong();
  }

  private static String assessmentJson(
      long subjectId, String name, String type, String maxMarks, boolean published) {
    return """
        {"subjectId":%d,"name":"%s","type":"%s","maxMarks":%s,"heldOn":null,"academicYear":"2026-27","published":%s}"""
        .formatted(subjectId, name, type, maxMarks, published);
  }

  private HttpResponse<String> saveMarks(long assessmentId, String... entries) throws Exception {
    return admin.put(
        "/api/v1/admin/assessments/" + assessmentId + "/marks",
        "{\"entries\":[" + String.join(",", entries) + "]}");
  }

  private static String marks(long studentId, String marksObtained, boolean absent) {
    return "{\"studentId\":%d,\"marksObtained\":%s,\"absent\":%s}"
        .formatted(studentId, marksObtained, absent);
  }

  private HttpResponse<String> saveResult(
      long studentId,
      int semester,
      String sgpa,
      int registered,
      int earned,
      String status,
      boolean published)
      throws Exception {
    return admin.put(
        "/api/v1/admin/semester-results",
        resultJson(studentId, semester, sgpa, registered, earned, status, published));
  }

  private static String resultJson(
      long studentId,
      int semester,
      String sgpa,
      int registered,
      int earned,
      String status,
      boolean published) {
    return """
        {"studentId":%d,"semester":%d,"sgpa":%s,"creditsRegistered":%d,"creditsEarned":%d,"resultStatus":"%s","published":%s}"""
        .formatted(studentId, semester, sgpa, registered, earned, status, published);
  }

  private static JsonNode onlySubject(JsonNode subjects, String code) {
    for (JsonNode subject : subjects) {
      if (subject.get("subjectCode").asString().equals(code)) {
        return subject;
      }
    }
    throw new AssertionError("No subject " + code + " in " + subjects);
  }

  private int marksRows(long assessmentId) {
    return jdbc.queryForObject(
        "select count(*) from app.marks where assessment_id = ?", Integer.class, assessmentId);
  }

  private int resultAudits(long studentId) {
    return jdbc.queryForObject(
        "select count(*) from app.audit_logs a join app.semester_results r on r.id::text = a.entity_id"
            + " where a.action = 'RESULT_UPDATED' and r.student_id = ?",
        Integer.class,
        studentId);
  }
}
