package com.campuscompanion.resource;

import static com.campuscompanion.support.ApiTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.support.ApiIntegrationTest;
import com.campuscompanion.support.ApiTestClient;
import com.campuscompanion.support.InMemoryStorageClient;
import com.campuscompanion.support.TestPdfs;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * The resource hub over real HTTP: upload checks (UP1 to UP11), signed links, search, bookmarks and
 * the access rules Z9 and Z10 of docs/17-testing-strategy.md. Storage is the in-memory stand-in; no
 * test reaches a real bucket.
 */
class ResourceApiIT extends ApiIntegrationTest {

  private static final String ADMIN = "resources-admin@university.example";
  private static final String PDF = "application/pdf";

  @Autowired private InMemoryStorageClient storage;

  private ApiTestClient admin;

  @BeforeEach
  void logInAsAdmin() throws Exception {
    admin = adminClient(ADMIN);
  }

  @AfterEach
  void storageWorksAgain() {
    storage.workAgain();
  }

  // --- Upload ---------------------------------------------------------------------------------

  @Test
  void aValidPdfIsStoredAndDescribed() throws Exception {
    long subjectId = subject(admin, "RSA101", "RSA", 3);
    byte[] pdf = TestPdfs.valid(2);

    HttpResponse<String> response =
        admin.postMultipart(
            "/api/v1/admin/resources",
            metadata("Unit 1 notes", subjectId, "NOTES", "\"description\":\"Worked examples.\""),
            "unit-1.pdf",
            PDF,
            pdf);

    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    JsonNode body = json(response);
    assertThat(body.get("title").asString()).isEqualTo("Unit 1 notes");
    assertThat(body.get("subjectCode").asString()).isEqualTo("RSA101");
    assertThat(body.get("type").asString()).isEqualTo("NOTES");
    assertThat(body.get("status").asString()).isEqualTo("PUBLISHED");
    assertThat(body.get("fileSizeBytes").asLong()).isEqualTo(pdf.length);
    assertThat(body.get("questionPaper").isNull()).isTrue();
    // The response never says where the file is kept.
    assertThat(response.body()).doesNotContain("storagePath", "checksum", "resources/RSA101");

    String path = storagePathOf(body.get("id").asLong());
    assertThat(path).matches("resources/RSA101/[0-9a-f-]{36}\\.pdf");
    assertThat(storage.contentAt(path)).isEqualTo(pdf);
    assertThat(
            jdbc.queryForObject(
                "select original_file_name || '|' || mime_type || '|' || length(checksum_sha256)"
                    + " from app.learning_resources where id = ?",
                String.class,
                body.get("id").asLong()))
        .isEqualTo("unit-1.pdf|application/pdf|64");
  }

  @Test
  void aTextFileRenamedToPdfIsRefused() throws Exception {
    long subjectId = subject(admin, "RSB101", "RSB", 3);
    int filesBefore = storage.paths().size();

    HttpResponse<String> response =
        admin.postMultipart(
            "/api/v1/admin/resources",
            metadata("Renamed text", subjectId, "NOTES", null),
            "notes.pdf",
            PDF,
            TestPdfs.plainText());

    assertProblem(response, 415, "UPLOAD_INVALID_TYPE");
    assertThat(storage.paths()).hasSize(filesBefore);
    assertThat(resourcesOf(subjectId)).isZero();
  }

  @Test
  void aFileWithOnlyThePdfSignatureIsRefused() throws Exception {
    long subjectId = subject(admin, "RSC101", "RSC", 3);

    HttpResponse<String> response =
        admin.postMultipart(
            "/api/v1/admin/resources",
            metadata("Broken", subjectId, "NOTES", null),
            "broken.pdf",
            PDF,
            TestPdfs.signatureOnly());

    assertProblem(response, 415, "UPLOAD_INVALID_TYPE");
    assertThat(resourcesOf(subjectId)).isZero();
  }

  @Test
  void aFileOverTenMegabytesIsRefused() throws Exception {
    long subjectId = subject(admin, "RSD101", "RSD", 3);

    HttpResponse<String> response =
        admin.postMultipart(
            "/api/v1/admin/resources",
            metadata("Huge", subjectId, "NOTES", null),
            "huge.pdf",
            PDF,
            TestPdfs.tooLarge());

    assertProblem(response, 413, "UPLOAD_TOO_LARGE");
    assertThat(resourcesOf(subjectId)).isZero();
  }

  @Test
  void aQuestionPaperNeedsItsExamDetails() throws Exception {
    long subjectId = subject(admin, "RSE101", "RSE", 3);
    byte[] pdf = TestPdfs.valid(1);

    HttpResponse<String> withoutDetails =
        admin.postMultipart(
            "/api/v1/admin/resources",
            metadata("Paper", subjectId, "QUESTION_PAPER", null),
            "paper.pdf",
            PDF,
            pdf);
    HttpResponse<String> withoutYear =
        admin.postMultipart(
            "/api/v1/admin/resources",
            metadata(
                "Paper",
                subjectId,
                "QUESTION_PAPER",
                "\"questionPaper\":{\"examType\":\"SEMESTER\"}"),
            "paper.pdf",
            PDF,
            pdf);
    HttpResponse<String> notesWithDetails =
        admin.postMultipart(
            "/api/v1/admin/resources",
            metadata(
                "Notes",
                subjectId,
                "NOTES",
                "\"questionPaper\":{\"examYear\":2025,\"examType\":\"SEMESTER\"}"),
            "notes.pdf",
            PDF,
            pdf);

    assertThat(invalidFields(withoutDetails)).containsExactly("questionPaper");
    assertThat(invalidFields(withoutYear)).containsExactly("questionPaper.examYear");
    assertThat(invalidFields(notesWithDetails)).containsExactly("questionPaper");
    assertThat(resourcesOf(subjectId)).isZero();
  }

  @Test
  void aQuestionPaperKeepsItsExamDetails() throws Exception {
    long subjectId = subject(admin, "RSF101", "RSF", 3);

    HttpResponse<String> response =
        admin.postMultipart(
            "/api/v1/admin/resources",
            metadata(
                "Semester exam 2025",
                subjectId,
                "QUESTION_PAPER",
                "\"questionPaper\":{\"examYear\":2025,\"examType\":\"SEMESTER\",\"maxMarks\":70}"),
            "paper.pdf",
            PDF,
            TestPdfs.valid(1));

    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    JsonNode paper = json(response).get("questionPaper");
    assertThat(paper.get("examYear").asInt()).isEqualTo(2025);
    assertThat(paper.get("examType").asString()).isEqualTo("SEMESTER");
    assertThat(paper.get("maxMarks").asInt()).isEqualTo(70);
  }

  @Test
  void anOddFileNameDoesNotReachTheStoragePath() throws Exception {
    long subjectId = subject(admin, "RSG101", "RSG", 3);

    HttpResponse<String> response =
        admin.postMultipart(
            "/api/v1/admin/resources",
            metadata("Odd name", subjectId, "NOTES", null),
            "../../etc/pass wd;rm -rf.pdf",
            PDF,
            TestPdfs.valid(1));

    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    long id = json(response).get("id").asLong();
    assertThat(storagePathOf(id)).matches("resources/RSG101/[0-9a-f-]{36}\\.pdf");
    assertThat(
            jdbc.queryForObject(
                "select original_file_name from app.learning_resources where id = ?",
                String.class,
                id))
        .isEqualTo("pass wd;rm -rf.pdf");
  }

  @Test
  void whenStorageIsDownNothingIsSaved() throws Exception {
    long subjectId = subject(admin, "RSH101", "RSH", 3);
    storage.failUploads(true);

    HttpResponse<String> response =
        admin.postMultipart(
            "/api/v1/admin/resources",
            metadata("Lost", subjectId, "NOTES", null),
            "lost.pdf",
            PDF,
            TestPdfs.valid(1));

    assertProblem(response, 503, "STORAGE_UNAVAILABLE");
    assertThat(resourcesOf(subjectId)).isZero();
  }

  @Test
  void theUnitMustBelongToTheSubject() throws Exception {
    long subjectId = subject(admin, "RSI101", "RSI", 3);
    long otherSubjectId = subject(admin, "RSI102", "RSI", 3);
    long otherUnitId = unit(otherSubjectId, 1, "Another subject's unit");

    HttpResponse<String> response =
        admin.postMultipart(
            "/api/v1/admin/resources",
            metadata("Wrong unit", subjectId, "NOTES", "\"unitId\":" + otherUnitId),
            "notes.pdf",
            PDF,
            TestPdfs.valid(1));
    HttpResponse<String> unknownSubject =
        admin.postMultipart(
            "/api/v1/admin/resources",
            metadata("No subject", 999_999_999L, "NOTES", null),
            "notes.pdf",
            PDF,
            TestPdfs.valid(1));

    assertThat(invalidFields(response)).containsExactly("unitId");
    assertThat(invalidFields(unknownSubject)).containsExactly("subjectId");
  }

  // --- Signed links ---------------------------------------------------------------------------

  @Test
  void aSignedLinkLastsAtMostFiveMinutes() throws Exception {
    long subjectId = subject(admin, "RSJ101", "RSJ", 3);
    long id = upload(subjectId, "Linked notes", "NOTES", null);
    accounts.student("23RSJ001", "RSJ", 3, "A");
    ApiTestClient student = studentClient("23RSJ001");

    HttpResponse<String> response = student.get("/api/v1/resources/" + id + "/download-url");

    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    assertThat(json(response).get("url").asString())
        .startsWith("https://storage.example/signed/")
        .doesNotContain("download=");
    // A download link asks storage to save the file under the name it was uploaded with.
    HttpResponse<String> download =
        student.get("/api/v1/resources/" + id + "/download-url?download=true");
    assertThat(json(download).get("url").asString()).endsWith("&download=file.pdf");
    Instant expiresAt = Instant.parse(json(response).get("expiresAt").asString());
    assertThat(Duration.between(Instant.now(), expiresAt))
        .isLessThanOrEqualTo(Duration.ofMinutes(5))
        .isGreaterThan(Duration.ofMinutes(4));
  }

  @Test
  void whenSigningFailsTheListStillWorks() throws Exception {
    long subjectId = subject(admin, "RSK101", "RSK", 3);
    long id = upload(subjectId, "Unreachable notes", "NOTES", null);
    accounts.student("23RSK001", "RSK", 3, "A");
    ApiTestClient student = studentClient("23RSK001");
    storage.failSigning(true);

    assertProblem(
        student.get("/api/v1/resources/" + id + "/download-url"), 503, "STORAGE_UNAVAILABLE");

    HttpResponse<String> list = student.get("/api/v1/resources?subjectId=" + subjectId);
    assertThat(list.statusCode()).isEqualTo(200);
    assertThat(texts(json(list).get("content"), "title")).containsExactly("Unreachable notes");
    assertThat(student.get("/api/v1/resources/" + id).statusCode()).isEqualTo(200);
  }

  @Test
  void aHiddenResourceLooksMissingToAStudent() throws Exception {
    long subjectId = subject(admin, "RSL101", "RSL", 3);
    long id = upload(subjectId, "Hidden notes", "NOTES", null);
    accounts.student("23RSL001", "RSL", 3, "A");
    ApiTestClient student = studentClient("23RSL001");
    assertThat(student.put("/api/v1/bookmarks/" + id, "").statusCode()).isEqualTo(204);

    HttpResponse<String> hidden =
        admin.put(
            "/api/v1/admin/resources/" + id, update("Hidden notes", subjectId, "NOTES", "HIDDEN"));
    assertThat(hidden.statusCode()).as(hidden.body()).isEqualTo(200);
    assertThat(json(hidden).get("status").asString()).isEqualTo("HIDDEN");

    assertProblem(student.get("/api/v1/resources/" + id + "/download-url"), 404, "NOT_FOUND");
    assertProblem(student.get("/api/v1/resources/" + id), 404, "NOT_FOUND");
    assertProblem(student.put("/api/v1/bookmarks/" + id, ""), 404, "NOT_FOUND");
    assertThat(json(student.get("/api/v1/resources?subjectId=" + subjectId)).get("content"))
        .isEmpty();
    assertThat(json(student.get("/api/v1/bookmarks/me")).get("content")).isEmpty();
    // A missing resource answers exactly the same way.
    assertProblem(student.get("/api/v1/resources/999999999/download-url"), 404, "NOT_FOUND");

    // The admin still sees it, and can open it.
    assertThat(admin.get("/api/v1/resources/" + id + "/download-url").statusCode()).isEqualTo(200);
    JsonNode adminList = json(admin.get("/api/v1/admin/resources?subjectId=" + subjectId));
    assertThat(texts(adminList.get("content"), "status")).containsExactly("HIDDEN");
    JsonNode onlyPublished =
        json(admin.get("/api/v1/admin/resources?status=PUBLISHED&subjectId=" + subjectId));
    assertThat(onlyPublished.get("content")).isEmpty();
  }

  // --- Update and delete ----------------------------------------------------------------------

  @Test
  void detailsCanBeEditedAndExamDetailsFollowTheType() throws Exception {
    long subjectId = subject(admin, "RSM101", "RSM", 3);
    long unitId = unit(subjectId, 2, "Linked lists");
    long id = upload(subjectId, "First title", "NOTES", null);

    HttpResponse<String> asPaper =
        admin.put(
            "/api/v1/admin/resources/" + id,
            """
            {"title":"Model paper","description":"With answers.","subjectId":%d,"unitId":%d,
             "type":"MODEL_PAPER","status":"PUBLISHED",
             "questionPaper":{"examYear":2026,"examType":"MODEL","maxMarks":null}}
            """
                .formatted(subjectId, unitId));

    assertThat(asPaper.statusCode()).as(asPaper.body()).isEqualTo(200);
    JsonNode body = json(asPaper);
    assertThat(body.get("title").asString()).isEqualTo("Model paper");
    assertThat(body.get("unitNo").asInt()).isEqualTo(2);
    assertThat(body.get("unitTitle").asString()).isEqualTo("Linked lists");
    assertThat(body.get("questionPaper").get("examType").asString()).isEqualTo("MODEL");

    HttpResponse<String> backToNotes =
        admin.put(
            "/api/v1/admin/resources/" + id,
            update("Notes again", subjectId, "NOTES", "PUBLISHED"));
    assertThat(backToNotes.statusCode()).as(backToNotes.body()).isEqualTo(200);
    assertThat(json(backToNotes).get("questionPaper").isNull()).isTrue();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from app.question_papers where resource_id = ?",
                Integer.class,
                id))
        .isZero();

    assertThat(
            invalidFields(
                admin.put(
                    "/api/v1/admin/resources/" + id, update(" ", subjectId, "NOTES", "PUBLISHED"))))
        .containsExactly("title");
    assertProblem(
        admin.put(
            "/api/v1/admin/resources/999999999", update("None", subjectId, "NOTES", "PUBLISHED")),
        404,
        "NOT_FOUND");
  }

  @Test
  void deletingRemovesTheRowTheFileAndTheBookmarks() throws Exception {
    long subjectId = subject(admin, "RSN101", "RSN", 3);
    long id = upload(subjectId, "Short-lived notes", "NOTES", null);
    String path = storagePathOf(id);
    accounts.student("23RSN001", "RSN", 3, "A");
    ApiTestClient student = studentClient("23RSN001");
    assertThat(student.put("/api/v1/bookmarks/" + id, "").statusCode()).isEqualTo(204);

    assertThat(admin.delete("/api/v1/admin/resources/" + id).statusCode()).isEqualTo(204);

    assertThat(storage.paths()).doesNotContain(path);
    assertThat(resourcesOf(subjectId)).isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from app.bookmarks where resource_id = ?", Integer.class, id))
        .isZero();
    assertProblem(admin.delete("/api/v1/admin/resources/" + id), 404, "NOT_FOUND");
  }

  @Test
  void whenTheFileCannotBeRemovedTheRowStays() throws Exception {
    long subjectId = subject(admin, "RSO101", "RSO", 3);
    long id = upload(subjectId, "Stays for now", "NOTES", null);
    storage.failDeletes(true);

    assertProblem(admin.delete("/api/v1/admin/resources/" + id), 503, "STORAGE_UNAVAILABLE");

    assertThat(resourcesOf(subjectId)).isEqualTo(1);
    assertThat(storage.paths()).contains(storagePathOf(id));
  }

  // --- Search ---------------------------------------------------------------------------------

  @Test
  void searchCombinesItsFilters() throws Exception {
    long dataStructures = subject(admin, "RSP301", "RSP", 3);
    long networks = subject(admin, "RSP501", "RSP", 5);
    long unitId = unit(dataStructures, 1, "Arrays");
    upload(dataStructures, "Arrays notes", "NOTES", "\"unitId\":" + unitId);
    upload(dataStructures, "Lab manual", "LAB_MANUAL", "\"description\":\"About arrays too.\"");
    upload(
        dataStructures,
        "Semester exam 2025",
        "QUESTION_PAPER",
        "\"questionPaper\":{\"examYear\":2025,\"examType\":\"SEMESTER\",\"maxMarks\":70}");
    upload(
        networks,
        "Model paper 2026",
        "MODEL_PAPER",
        "\"questionPaper\":{\"examYear\":2026,\"examType\":\"MODEL\"}");
    accounts.student("23RSP001", "RSP", 3, "A");
    ApiTestClient student = studentClient("23RSP001");

    assertThat(titles(student, "/api/v1/resources?subjectId=" + dataStructures))
        .containsExactly("Semester exam 2025", "Lab manual", "Arrays notes");
    assertThat(titles(student, "/api/v1/resources?subjectId=" + dataStructures + "&sort=title,asc"))
        .containsExactly("Arrays notes", "Lab manual", "Semester exam 2025");
    assertThat(titles(student, "/api/v1/resources?unitId=" + unitId))
        .containsExactly("Arrays notes");
    assertThat(
            titles(student, "/api/v1/resources?subjectId=" + dataStructures + "&type=LAB_MANUAL"))
        .containsExactly("Lab manual");
    // The text is matched in the title and the description, in any letter case.
    assertThat(titles(student, "/api/v1/resources?subjectId=" + dataStructures + "&q=ARRAYS"))
        .containsExactly("Lab manual", "Arrays notes");
    // A student may read the resources of any semester and branch.
    assertThat(titles(student, "/api/v1/resources?subjectId=" + networks))
        .containsExactly("Model paper 2026");
    assertThat(titles(student, "/api/v1/resources?semester=5&q=model paper 2026"))
        .containsExactly("Model paper 2026");

    JsonNode firstPage =
        json(student.get("/api/v1/resources?subjectId=" + dataStructures + "&size=2&page=0"));
    assertThat(firstPage.get("content")).hasSize(2);
    assertThat(firstPage.get("totalElements").asInt()).isEqualTo(3);
    assertThat(firstPage.get("totalPages").asInt()).isEqualTo(2);
    // A student is not told about hiding at all.
    assertThat(firstPage.get("content").get(0).get("status").isNull()).isTrue();

    assertThat(titles(student, "/api/v1/question-papers?subjectId=" + dataStructures))
        .containsExactly("Semester exam 2025");
    assertThat(titles(student, "/api/v1/question-papers?examYear=2026&subjectId=" + networks))
        .containsExactly("Model paper 2026");
    assertThat(titles(student, "/api/v1/question-papers?examType=MODEL&subjectId=" + networks))
        .containsExactly("Model paper 2026");
    assertThat(titles(student, "/api/v1/question-papers?examType=INTERNAL&subjectId=" + networks))
        .isEmpty();
  }

  @Test
  void searchRejectsFiltersOutsideTheContract() throws Exception {
    accounts.student("23RSQ001", "RSQ", 3, "A");
    ApiTestClient student = studentClient("23RSQ001");

    assertThat(invalidFields(student.get("/api/v1/resources?q=a"))).containsExactly("q");
    assertThat(invalidFields(student.get("/api/v1/resources?size=101"))).containsExactly("size");
    assertThat(invalidFields(student.get("/api/v1/resources?semester=9")))
        .containsExactly("semester");
    assertThat(invalidFields(student.get("/api/v1/resources?sort=storagePath,asc")))
        .containsExactly("sort");
    assertThat(student.get("/api/v1/resources?type=VIDEO").statusCode()).isEqualTo(400);
  }

  // --- Bookmarks ------------------------------------------------------------------------------

  @Test
  void bookmarkingTwiceAndRemovingTwiceHaveNoExtraEffect() throws Exception {
    long subjectId = subject(admin, "RSR101", "RSR", 3);
    long id = upload(subjectId, "Saved notes", "NOTES", null);
    long studentId = accounts.student("23RSR001", "RSR", 3, "A");
    ApiTestClient student = studentClient("23RSR001");

    assertThat(student.put("/api/v1/bookmarks/" + id, "").statusCode()).isEqualTo(204);
    assertThat(student.put("/api/v1/bookmarks/" + id, "").statusCode()).isEqualTo(204);

    assertThat(bookmarksOf(studentId)).isEqualTo(1);
    assertThat(titles(student, "/api/v1/bookmarks/me")).containsExactly("Saved notes");
    JsonNode listed = json(student.get("/api/v1/resources?subjectId=" + subjectId)).get("content");
    assertThat(listed.get(0).get("bookmarked").asBoolean()).isTrue();

    assertThat(student.delete("/api/v1/bookmarks/" + id).statusCode()).isEqualTo(204);
    assertThat(student.delete("/api/v1/bookmarks/" + id).statusCode()).isEqualTo(204);

    assertThat(bookmarksOf(studentId)).isZero();
    assertThat(json(student.get("/api/v1/resources/" + id)).get("bookmarked").asBoolean())
        .isFalse();
    assertProblem(student.put("/api/v1/bookmarks/999999999", ""), 404, "NOT_FOUND");
  }

  @Test
  void aStudentCannotTouchAnotherStudentsBookmarks() throws Exception {
    long subjectId = subject(admin, "RSS101", "RSS", 3);
    long id = upload(subjectId, "Shared notes", "NOTES", null);
    long firstId = accounts.student("23RSS001", "RSS", 3, "A");
    long secondId = accounts.student("23RSS002", "RSS", 3, "A");
    ApiTestClient first = studentClient("23RSS001");
    ApiTestClient second = studentClient("23RSS002");
    assertThat(first.put("/api/v1/bookmarks/" + id, "").statusCode()).isEqualTo(204);

    // The request names only the resource. Whose bookmark it is comes from the session.
    assertThat(second.delete("/api/v1/bookmarks/" + id).statusCode()).isEqualTo(204);

    assertThat(bookmarksOf(firstId)).isEqualTo(1);
    assertThat(bookmarksOf(secondId)).isZero();
    assertThat(json(second.get("/api/v1/bookmarks/me")).get("content")).isEmpty();
    assertThat(json(second.get("/api/v1/resources/" + id)).get("bookmarked").asBoolean()).isFalse();
    assertThat(titles(first, "/api/v1/bookmarks/me")).containsExactly("Shared notes");
  }

  // --- Who may call what ----------------------------------------------------------------------

  @Test
  void visitorsAreTurnedAwayAndStudentsCannotManageResources() throws Exception {
    long subjectId = subject(admin, "RST101", "RST", 3);
    long id = upload(subjectId, "Guarded notes", "NOTES", null);
    accounts.student("23RST001", "RST", 3, "A");
    ApiTestClient student = studentClient("23RST001");
    ApiTestClient visitor = visitor();

    for (String path :
        List.of(
            "/api/v1/resources",
            "/api/v1/resources/" + id,
            "/api/v1/resources/" + id + "/download-url",
            "/api/v1/question-papers",
            "/api/v1/bookmarks/me",
            "/api/v1/admin/resources")) {
      assertProblem(visitor.get(path), 401, "AUTH_REQUIRED");
    }
    assertProblem(visitor.put("/api/v1/bookmarks/" + id, ""), 401, "AUTH_REQUIRED");
    assertProblem(visitor.delete("/api/v1/admin/resources/" + id), 401, "AUTH_REQUIRED");

    assertProblem(student.get("/api/v1/admin/resources"), 403, "ACCESS_DENIED");
    assertProblem(
        student.postMultipart(
            "/api/v1/admin/resources",
            metadata("Not allowed", subjectId, "NOTES", null),
            "notes.pdf",
            PDF,
            TestPdfs.valid(1)),
        403,
        "ACCESS_DENIED");
    assertProblem(
        student.put(
            "/api/v1/admin/resources/" + id, update("Changed", subjectId, "NOTES", "HIDDEN")),
        403,
        "ACCESS_DENIED");
    assertProblem(student.delete("/api/v1/admin/resources/" + id), 403, "ACCESS_DENIED");
    assertThat(resourcesOf(subjectId)).isEqualTo(1);

    // Bookmarks belong to students. An admin has none.
    assertProblem(admin.get("/api/v1/bookmarks/me"), 403, "ACCESS_DENIED");
    assertProblem(admin.put("/api/v1/bookmarks/" + id, ""), 403, "ACCESS_DENIED");
  }

  // --- Helpers --------------------------------------------------------------------------------

  /** The metadata part of an upload. "extra" is one more JSON member, or null. */
  private static String metadata(String title, long subjectId, String type, String extra) {
    return "{\"title\":\"%s\",\"subjectId\":%d,\"type\":\"%s\"%s}"
        .formatted(title, subjectId, type, extra == null ? "" : "," + extra);
  }

  private static String update(String title, long subjectId, String type, String status) {
    return "{\"title\":\"%s\",\"description\":null,\"subjectId\":%d,\"unitId\":null,"
            .formatted(title, subjectId)
        + "\"type\":\"%s\",\"questionPaper\":null,\"status\":\"%s\"}".formatted(type, status);
  }

  /** Uploads a one-page PDF and returns the ID of the new resource. */
  private long upload(long subjectId, String title, String type, String extra) throws Exception {
    HttpResponse<String> response =
        admin.postMultipart(
            "/api/v1/admin/resources",
            metadata(title, subjectId, type, extra),
            "file.pdf",
            PDF,
            TestPdfs.valid(1));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    return json(response).get("id").asLong();
  }

  private long unit(long subjectId, int unitNo, String title) throws Exception {
    HttpResponse<String> response =
        admin.post(
            "/api/v1/admin/subjects/" + subjectId + "/units",
            "{\"unitNo\":%d,\"title\":\"%s\"}".formatted(unitNo, title));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    return json(response).get("id").asLong();
  }

  private static List<String> titles(ApiTestClient client, String path) throws Exception {
    HttpResponse<String> response = client.get(path.replace(" ", "%20"));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    return texts(json(response).get("content"), "title");
  }

  private String storagePathOf(long resourceId) {
    return jdbc.queryForObject(
        "select storage_path from app.learning_resources where id = ?", String.class, resourceId);
  }

  private int resourcesOf(long subjectId) {
    return jdbc.queryForObject(
        "select count(*) from app.learning_resources where subject_id = ?",
        Integer.class,
        subjectId);
  }

  private int bookmarksOf(long studentId) {
    return jdbc.queryForObject(
        "select count(*) from app.bookmarks where student_id = ?", Integer.class, studentId);
  }
}
