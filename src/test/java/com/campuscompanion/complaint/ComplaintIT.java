package com.campuscompanion.complaint;

import static com.campuscompanion.support.ApiTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.support.ApiIntegrationTest;
import com.campuscompanion.support.ApiTestClient;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Complaints (ADR 0013): public, private and secure, support for public ones, and what the
 * administrator may and may not see. All data is synthetic. Each test uses its own students, so the
 * daily limit of one test does not touch another.
 */
class ComplaintIT extends ApiIntegrationTest {

  private static final String STUDENT = "/api/v1/complaints";
  private static final String ADMIN = "/api/v1/admin/complaints";
  private static final String TEXT =
      "The projector in the main seminar hall has not worked for weeks.";

  private ApiTestClient student(String rollNo) throws Exception {
    accounts.student(rollNo, "CMP", 3, "A");
    return studentClient(rollNo);
  }

  private ApiTestClient admin() throws Exception {
    return adminClient("complaints-admin@university.example");
  }

  private static String body(String type, String title, Boolean showIdentity) {
    return """
        {"type":"%s","category":"FACILITIES","title":"%s","description":"%s","showIdentity":%s}"""
        .formatted(type, title, TEXT, showIdentity);
  }

  private long raise(ApiTestClient who, String type, String title, Boolean showIdentity)
      throws Exception {
    HttpResponse<String> response = who.post(STUDENT, body(type, title, showIdentity));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    return json(response).get("id").asLong();
  }

  private static JsonNode find(JsonNode page, long id) {
    for (JsonNode item : page.get("content")) {
      if (item.get("id").asLong() == id) {
        return item;
      }
    }
    return null;
  }

  // --- public ----------------------------------------------------------------------------------

  @Test
  void aPublicComplaintShowsItsAuthorOnlyWhenTheAuthorChoseTo() throws Exception {
    ApiTestClient shown = student("CMPA0001");
    ApiTestClient hidden = student("CMPA0002");
    ApiTestClient reader = student("CMPA0003");
    long withName = raise(shown, "PUBLIC", "Seminar hall projector A", true);
    long anonymous = raise(hidden, "PUBLIC", "Seminar hall projector B", false);

    JsonNode board = json(reader.get(STUDENT + "?size=50"));
    assertThat(find(board, withName).get("authorRollNo").asString()).isEqualTo("CMPA0001");
    assertThat(find(board, anonymous).get("authorRollNo").isNull()).isTrue();
    assertThat(find(board, anonymous).get("mine").asBoolean()).isFalse();
    // Nothing else in the hidden complaint can name the author.
    HttpResponse<String> one = reader.get(STUDENT + "/" + anonymous);
    assertThat(one.body()).doesNotContain("CMPA0002").doesNotContain("Demo CMPA0002");
    // The author finds it as theirs.
    assertThat(find(json(hidden.get(STUDENT + "?size=50")), anonymous).get("mine").asBoolean())
        .isTrue();

    // The administrator sees both authors, and which one asked for anonymity.
    ApiTestClient admin = admin();
    JsonNode seen = find(json(admin.get(ADMIN + "?size=100")), anonymous);
    assertThat(seen.get("student").get("rollNo").asString()).isEqualTo("CMPA0002");
    assertThat(seen.get("hiddenFromStudents").asBoolean()).isTrue();
    assertThat(seen.get("identityProtected").asBoolean()).isFalse();
  }

  @Test
  void studentsSupportAPublicComplaintAndFiveMakeItAGroupComplaint() throws Exception {
    ApiTestClient author = student("CMPB0001");
    long id = raise(author, "PUBLIC", "Library closes too early", false);
    ApiTestClient last = null;
    for (int i = 2; i <= 6; i++) {
      ApiTestClient supporter = student("CMPB000" + i);
      HttpResponse<String> response = supporter.put(STUDENT + "/" + id + "/support", "");
      assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
      JsonNode result = json(response);
      assertThat(result.get("supporters").asInt()).isEqualTo(i - 1);
      assertThat(result.get("supported").asBoolean()).isTrue();
      assertThat(result.get("group").asBoolean()).isEqualTo(i - 1 >= 5);
      last = supporter;
    }
    // Supporting again changes nothing; taking it back lowers the count and the group mark.
    assertThat(json(last.put(STUDENT + "/" + id + "/support", "")).get("supporters").asInt())
        .isEqualTo(5);
    JsonNode back = json(last.delete(STUDENT + "/" + id + "/support"));
    assertThat(back.get("supporters").asInt()).isEqualTo(4);
    assertThat(back.get("group").asBoolean()).isFalse();
    assertThat(json(last.delete(STUDENT + "/" + id + "/support")).get("supporters").asInt())
        .isEqualTo(4);

    // The author cannot support their own complaint.
    assertProblem(author.put(STUDENT + "/" + id + "/support", ""), 409, "CONFLICT");

    // The administrator sees the supporters.
    JsonNode detail = json(admin().get(ADMIN + "/" + id));
    assertThat(detail.get("supporters").asInt()).isEqualTo(4);
    assertThat(detail.get("supportedBy")).hasSize(4);
  }

  @Test
  void theBoardCanBeSortedByTheMostSupported() throws Exception {
    ApiTestClient author = student("CMPC0001");
    ApiTestClient one = student("CMPC0002");
    ApiTestClient two = student("CMPC0003");
    long quiet = raise(author, "PUBLIC", "Quiet complaint cmpc", false);
    Thread.sleep(5);
    long popular = raise(author, "PUBLIC", "Popular complaint cmpc", false);
    one.put(STUDENT + "/" + popular + "/support", "");
    two.put(STUDENT + "/" + popular + "/support", "");

    List<Long> top = ids(json(one.get(STUDENT + "?sort=top&size=50")));
    assertThat(top.indexOf(popular)).isLessThan(top.indexOf(quiet));
    List<Long> newest = ids(json(one.get(STUDENT + "?sort=newest&size=50")));
    assertThat(newest.indexOf(popular)).isLessThan(newest.indexOf(quiet));
    assertProblem(one.get(STUDENT + "?sort=random"), 400, "VALIDATION_FAILED");
    assertProblem(one.get(STUDENT + "?category=NOPE"), 400, "VALIDATION_FAILED");
    assertProblem(one.get(STUDENT + "?size=500"), 400, "VALIDATION_FAILED");
  }

  private static List<Long> ids(JsonNode page) {
    return page.get("content").valueStream().map(n -> n.get("id").asLong()).toList();
  }

  // --- private and secure ----------------------------------------------------------------------

  @Test
  void aPrivateComplaintReachesOnlyTheAdministratorWhoSeesTheStudent() throws Exception {
    ApiTestClient author = student("CMPD0001");
    ApiTestClient other = student("CMPD0002");
    long id = raise(author, "PRIVATE", "Private matter with a teacher", true);

    assertThat(find(json(other.get(STUDENT + "?size=50")), id)).isNull();
    assertProblem(other.get(STUDENT + "/" + id), 404, "NOT_FOUND");
    assertProblem(other.put(STUDENT + "/" + id + "/support", ""), 404, "NOT_FOUND");
    assertProblem(other.delete(STUDENT + "/" + id), 404, "NOT_FOUND");
    // "Show my roll number" means nothing outside a public complaint.
    assertThat(json(author.get(STUDENT + "/" + id)).get("authorRollNo").isNull()).isTrue();

    JsonNode seen = json(admin().get(ADMIN + "/" + id));
    assertThat(seen.get("student").get("rollNo").asString()).isEqualTo("CMPD0001");
    assertThat(seen.get("identityProtected").asBoolean()).isFalse();
    assertThat(seen.get("hiddenFromStudents").asBoolean()).isFalse();
  }

  @Test
  void aSecureComplaintNeverRevealsTheStudentToTheAdministrator() throws Exception {
    ApiTestClient author = student("CMPE0001");
    ApiTestClient other = student("CMPE0002");
    long id = raise(author, "SECURE", "Serious concern kept secure", true);
    ApiTestClient admin = admin();

    assertThat(find(json(other.get(STUDENT + "?size=50")), id)).isNull();
    assertProblem(other.get(STUDENT + "/" + id), 404, "NOT_FOUND");

    // Every administrator response, in every form, is free of the student.
    HttpResponse<String> list = admin.get(ADMIN + "?type=SECURE&size=100");
    HttpResponse<String> detail = admin.get(ADMIN + "/" + id);
    for (HttpResponse<String> response : List.of(list, detail)) {
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).doesNotContain("CMPE0001").doesNotContain("Demo CMPE0001");
    }
    JsonNode seen = json(detail);
    assertThat(seen.get("student").isNull()).isTrue();
    assertThat(seen.get("identityProtected").asBoolean()).isTrue();
    assertThat(seen.get("supportedBy")).isEmpty();
    // The time is cut to the day, so it cannot be matched to a login.
    assertThat(seen.get("createdAt").asString()).contains("T00:00:00");

    // Handling it does not reveal the student either, in the response or in the audit log.
    HttpResponse<String> handled =
        admin.put(
            ADMIN + "/" + id,
            "{\"status\":\"IN_REVIEW\",\"response\":\"We are looking into it.\"}");
    assertThat(handled.statusCode()).as(handled.body()).isEqualTo(200);
    assertThat(handled.body()).doesNotContain("CMPE0001");
    List<String> audit =
        jdbc.queryForList(
            "select coalesce(old_value::text,'') || coalesce(new_value::text,'') from app.audit_logs"
                + " where action = 'COMPLAINT_UPDATED' and entity_id = ?",
            String.class,
            String.valueOf(id));
    assertThat(audit).hasSize(1);
    assertThat(audit.get(0))
        .contains("IN_REVIEW")
        .doesNotContain("CMPE0001")
        .doesNotContain("Demo");

    // The student still follows their own secure complaint and reads the response.
    JsonNode mine = find(json(author.get(STUDENT + "/mine?size=50")), id);
    assertThat(mine.get("status").asString()).isEqualTo("IN_REVIEW");
    assertThat(mine.get("adminResponse").asString()).isEqualTo("We are looking into it.");
    assertThat(mine.get("mine").asBoolean()).isTrue();
  }

  // --- handling --------------------------------------------------------------------------------

  @Test
  void aRejectedPublicComplaintLeavesTheBoardButStaysWithItsAuthor() throws Exception {
    ApiTestClient author = student("CMPF0001");
    ApiTestClient reader = student("CMPF0002");
    long id = raise(author, "PUBLIC", "Complaint that will be rejected", false);
    assertThat(find(json(reader.get(STUDENT + "?size=50")), id)).isNotNull();

    HttpResponse<String> rejected =
        admin()
            .put(
                ADMIN + "/" + id,
                "{\"status\":\"REJECTED\",\"response\":\"This is not a complaint.\"}");
    assertThat(rejected.statusCode()).as(rejected.body()).isEqualTo(200);

    assertThat(find(json(reader.get(STUDENT + "?size=50")), id)).isNull();
    assertProblem(reader.get(STUDENT + "/" + id), 404, "NOT_FOUND");
    assertProblem(reader.put(STUDENT + "/" + id + "/support", ""), 404, "NOT_FOUND");
    JsonNode own = json(author.get(STUDENT + "/" + id));
    assertThat(own.get("status").asString()).isEqualTo("REJECTED");
    assertThat(own.get("adminResponse").asString()).isEqualTo("This is not a complaint.");
  }

  @Test
  void theAdministratorFiltersHandlesAndValidates() throws Exception {
    ApiTestClient author = student("CMPG0001");
    ApiTestClient admin = admin();
    long publicId = raise(author, "PUBLIC", "Filter test public", true);
    long privateId = raise(author, "PRIVATE", "Filter test private", false);

    assertThat(find(json(admin.get(ADMIN + "?type=PUBLIC&size=100")), privateId)).isNull();
    assertThat(find(json(admin.get(ADMIN + "?type=PRIVATE&size=100")), privateId)).isNotNull();
    assertThat(admin.put(ADMIN + "/" + publicId, "{\"status\":\"RESOLVED\"}").statusCode())
        .isEqualTo(200);
    assertThat(find(json(admin.get(ADMIN + "?status=OPEN&size=100")), publicId)).isNull();
    assertThat(find(json(admin.get(ADMIN + "?status=RESOLVED&size=100")), publicId)).isNotNull();

    assertProblem(
        admin.put(ADMIN + "/" + privateId, "{\"response\":\"no status\"}"),
        400,
        "VALIDATION_FAILED");
    assertProblem(
        admin.put(ADMIN + "/" + privateId, "{\"status\":\"DONE\"}"), 400, "VALIDATION_FAILED");
    String tooLong = "x".repeat(2001);
    assertProblem(
        admin.put(
            ADMIN + "/" + privateId, "{\"status\":\"OPEN\",\"response\":\"" + tooLong + "\"}"),
        400,
        "VALIDATION_FAILED");
    assertProblem(admin.put(ADMIN + "/99999999", "{\"status\":\"OPEN\"}"), 404, "NOT_FOUND");
    assertProblem(admin.get(ADMIN + "/99999999"), 404, "NOT_FOUND");
    // Supporting a resolved complaint is refused.
    assertProblem(
        student("CMPG0002").put(STUDENT + "/" + publicId + "/support", ""), 409, "CONFLICT");
  }

  @Test
  void aStudentWithdrawsOnlyAnOpenComplaintOfTheirOwn() throws Exception {
    ApiTestClient author = student("CMPH0001");
    ApiTestClient supporter = student("CMPH0002");
    long open = raise(author, "PUBLIC", "Withdraw me while open", false);
    supporter.put(STUDENT + "/" + open + "/support", "");
    assertThat(author.delete(STUDENT + "/" + open).statusCode()).isEqualTo(204);
    assertProblem(author.get(STUDENT + "/" + open), 404, "NOT_FOUND");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from app.complaint_supports where complaint_id = ?",
                Long.class,
                open))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from app.complaint_owners where complaint_id = ?",
                Long.class,
                open))
        .isZero();

    long handled = raise(author, "PRIVATE", "Cannot withdraw once handled", false);
    admin().put(ADMIN + "/" + handled, "{\"status\":\"IN_REVIEW\"}");
    assertProblem(author.delete(STUDENT + "/" + handled), 409, "CONFLICT");
    assertProblem(author.delete(STUDENT + "/99999999"), 404, "NOT_FOUND");
  }

  // --- raising: validation and limits ----------------------------------------------------------

  @Test
  void aComplaintNeedsAKnownTypeATitleAndADescription() throws Exception {
    ApiTestClient s = student("CMPI0001");
    assertThat(
            invalidFields(
                s.post(
                    STUDENT,
                    "{\"category\":\"OTHER\",\"title\":\"Valid title\",\"description\":\""
                        + TEXT
                        + "\"}")))
        .containsExactly("type");
    assertThat(
            invalidFields(
                s.post(
                    STUDENT,
                    "{\"type\":\"PUBLIC\",\"title\":\"Valid title\",\"description\":\""
                        + TEXT
                        + "\"}")))
        .containsExactly("category");
    assertThat(
            invalidFields(
                s.post(
                    STUDENT,
                    "{\"type\":\"PUBLIC\",\"category\":\"OTHER\",\"title\":\"abc\",\"description\":\"short\"}")))
        .containsExactlyInAnyOrder("title", "description");
    assertThat(invalidFields(s.post(STUDENT, body("PUBLIC", "t".repeat(121), false))))
        .containsExactly("title");
    assertProblem(
        s.post(STUDENT, body("TOP_SECRET", "Valid title", false)), 400, "VALIDATION_FAILED");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from app.complaint_owners o join app.student_profiles p on p.id = o.student_id where p.roll_no = 'CMPI0001'",
                Long.class))
        .isZero();
  }

  @Test
  void aStudentCanRaiseFiveComplaintsADay() throws Exception {
    ApiTestClient s = student("CMPJ0001");
    for (int i = 1; i <= 5; i++) {
      raise(s, "PRIVATE", "Daily limit complaint " + i, false);
    }
    assertProblem(s.post(STUDENT, body("PRIVATE", "One too many", false)), 429, "RATE_LIMITED");
    // Another student is not affected.
    raise(student("CMPJ0002"), "PRIVATE", "Another student is fine", false);
  }

  @Test
  void textIsStoredAsPlainTextAndNeverInterpreted() throws Exception {
    ApiTestClient s = student("CMPK0001");
    String script = "<script>alert(1)</script> and a long enough description of the issue";
    HttpResponse<String> response =
        s.post(
            STUDENT,
            """
            {"type":"PUBLIC","category":"OTHER","title":"<b>bold</b> title","description":"%s","showIdentity":false}"""
                .formatted(script));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    assertThat(json(response).get("title").asString()).isEqualTo("<b>bold</b> title");
    assertThat(json(response).get("description").asString()).isEqualTo(script);
  }

  // --- access ----------------------------------------------------------------------------------

  @Test
  void onlyTheRightRoleReachesEachSide() throws Exception {
    ApiTestClient s = student("CMPL0001");
    ApiTestClient admin = admin();
    long id = raise(s, "PUBLIC", "Access test complaint", false);

    assertProblem(visitor().get(STUDENT), 401, "AUTH_REQUIRED");
    assertProblem(visitor().get(ADMIN), 401, "AUTH_REQUIRED");
    assertProblem(s.get(ADMIN), 403, "ACCESS_DENIED");
    assertProblem(s.get(ADMIN + "/" + id), 403, "ACCESS_DENIED");
    assertProblem(s.put(ADMIN + "/" + id, "{\"status\":\"RESOLVED\"}"), 403, "ACCESS_DENIED");
    assertProblem(admin.get(STUDENT), 403, "ACCESS_DENIED");
    assertProblem(
        admin.post(STUDENT, body("PUBLIC", "An admin cannot raise one", false)),
        403,
        "ACCESS_DENIED");
    assertProblem(
        s.postWithoutCsrf(STUDENT, body("PUBLIC", "Missing security token", false)),
        403,
        "CSRF_INVALID");
    // The status did not change.
    assertThat(json(s.get(STUDENT + "/" + id)).get("status").asString()).isEqualTo("OPEN");
  }
}
