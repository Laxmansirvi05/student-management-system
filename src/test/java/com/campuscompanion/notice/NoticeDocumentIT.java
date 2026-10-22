package com.campuscompanion.notice;

import static com.campuscompanion.support.ApiTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.support.ApiIntegrationTest;
import com.campuscompanion.support.ApiTestClient;
import com.campuscompanion.support.InMemoryStorageClient;
import com.campuscompanion.support.TestPdfs;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * A notice can carry the college's official document (FR-NOT-002): an admin attaches a PDF or a
 * picture, students who may see the notice open it through a short-lived link, and nobody else can.
 * Storage is the in-memory stand-in; all data is synthetic.
 */
class NoticeDocumentIT extends ApiIntegrationTest {

  private static final String ADMIN = "/api/v1/admin/notices";
  private static int pdfs = 100;

  @Autowired private InMemoryStorageClient storage;
  private ApiTestClient admin;

  @BeforeEach
  void setUp() throws Exception {
    admin = adminClient("notice-docs-admin@university.example");
  }

  @AfterEach
  void storageWorksAgain() {
    storage.workAgain();
  }

  @Test
  void aStudentOpensTheOfficialDocumentOfANoticeMeantForThem() throws Exception {
    accounts.student("NDC23001", "NDC", 3, "A");
    ApiTestClient student = studentClient("NDC23001");
    long id = publish(notice("Exam circular with stamp", "NDC", 3));

    HttpResponse<String> attached =
        attach(id, "circular.pdf", "application/pdf", TestPdfs.valid(++pdfs));
    assertThat(attached.statusCode()).as(attached.body()).isEqualTo(200);
    assertThat(json(attached).get("documentType").asString()).isEqualTo("application/pdf");
    assertThat(storage.paths())
        .anyMatch(path -> path.startsWith("notices/") && path.endsWith(".pdf"));

    JsonNode listed = json(student.get("/api/v1/notices/" + id));
    assertThat(listed.get("documentType").asString()).isEqualTo("application/pdf");
    JsonNode link = json(student.get("/api/v1/notices/" + id + "/document"));
    assertThat(link.get("url").asString()).startsWith("https://storage.example/signed/notices/");
    assertThat(link.get("url").asString()).doesNotContain("download=");
    assertThat(link.get("contentType").asString()).isEqualTo("application/pdf");
    JsonNode download = json(student.get("/api/v1/notices/" + id + "/document?download=true"));
    assertThat(download.get("url").asString())
        .endsWith("&download=Notice - Exam circular with stamp.pdf");
  }

  @Test
  void aPictureIsDrawnAgainAsAJpegAndAReplacementRemovesTheOldFile() throws Exception {
    long id = publish(notice("Holiday circular", null, null));

    HttpResponse<String> png = attach(id, "photo.png", "image/png", picture("png", 900, 1200));
    assertThat(png.statusCode()).as(png.body()).isEqualTo(200);
    assertThat(json(png).get("documentType").asString()).isEqualTo("image/jpeg");
    String first = storedDocument();
    byte[] stored = storage.contentAt(first);
    assertThat(stored[0]).isEqualTo((byte) 0xFF);
    assertThat(stored[1]).isEqualTo((byte) 0xD8);

    HttpResponse<String> jpeg = attach(id, "scan.jpg", "image/jpeg", picture("jpeg", 3000, 4000));
    assertThat(jpeg.statusCode()).as(jpeg.body()).isEqualTo(200);
    String second = storedDocument();
    assertThat(second).isNotEqualTo(first);
    assertThat(storage.paths()).doesNotContain(first);
    // A very large picture is scaled down so it opens quickly on a phone.
    BufferedImage kept = ImageIO.read(new java.io.ByteArrayInputStream(storage.contentAt(second)));
    assertThat(Math.max(kept.getWidth(), kept.getHeight())).isEqualTo(2400);
  }

  @Test
  void wrongFilesAreRefusedAndNothingIsStored() throws Exception {
    long id = notice("Draft with a bad file", null, null);
    int before = storage.paths().size();

    assertProblem(
        attach(id, "notes.txt", "text/plain", "hello".getBytes()), 415, "UPLOAD_INVALID_TYPE");
    assertProblem(
        attach(id, "fake.pdf", "application/pdf", "not a pdf".getBytes()),
        415,
        "UPLOAD_INVALID_TYPE");
    assertProblem(
        attach(id, "fake.jpg", "image/jpeg", "not a picture".getBytes()),
        415,
        "UPLOAD_INVALID_TYPE");
    assertProblem(
        attach(id, "tiny.png", "image/png", picture("png", 50, 50)), 415, "UPLOAD_INVALID_TYPE");
    assertProblem(
        attach(id, "huge.pdf", "application/pdf", new byte[10 * 1024 * 1024 + 1]),
        413,
        "UPLOAD_TOO_LARGE");
    assertProblem(
        attach(999_999, "circular.pdf", "application/pdf", TestPdfs.valid(++pdfs)),
        404,
        "NOT_FOUND");
    assertThat(storage.paths()).hasSize(before);

    storage.failUploads(true);
    assertProblem(
        attach(id, "circular.pdf", "application/pdf", TestPdfs.valid(++pdfs)),
        503,
        "STORAGE_UNAVAILABLE");
    assertThat(
            json(admin.get(ADMIN + "?size=100"))
                .get("content")
                .valueStream()
                .filter(n -> n.get("id").asLong() == id)
                .findFirst()
                .orElseThrow()
                .get("documentType")
                .isNull())
        .isTrue();
  }

  @Test
  void onlyStudentsWhoMaySeeTheNoticeGetALink() throws Exception {
    accounts.student("NDC23002", "NDC", 3, "A");
    accounts.student("NDO23003", "NDO", 3, "A");
    ApiTestClient mine = studentClient("NDC23002");
    ApiTestClient other = studentClient("NDO23003");
    long forClass = publish(notice("For one class", "NDC", 3));
    long draft = notice("Still a draft", null, null);
    long textOnly = publish(notice("Text only", null, null));
    attach(forClass, "c.pdf", "application/pdf", TestPdfs.valid(++pdfs));
    attach(draft, "d.pdf", "application/pdf", TestPdfs.valid(++pdfs));

    assertThat(mine.get("/api/v1/notices/" + forClass + "/document").statusCode()).isEqualTo(200);
    assertProblem(other.get("/api/v1/notices/" + forClass + "/document"), 404, "NOT_FOUND");
    assertProblem(mine.get("/api/v1/notices/" + draft + "/document"), 404, "NOT_FOUND");
    assertProblem(mine.get("/api/v1/notices/" + textOnly + "/document"), 404, "NOT_FOUND");
    assertProblem(visitor().get("/api/v1/notices/" + forClass + "/document"), 401, "AUTH_REQUIRED");
    // The admin may look at a draft's document too.
    assertThat(admin.get(ADMIN + "/" + draft + "/document").statusCode()).isEqualTo(200);
    // Students cannot change documents.
    assertProblem(
        mine.postFile(
            ADMIN + "/" + forClass + "/document",
            "x.pdf",
            "application/pdf",
            TestPdfs.valid(++pdfs)),
        403,
        "ACCESS_DENIED");
    assertProblem(mine.delete(ADMIN + "/" + forClass + "/document"), 403, "ACCESS_DENIED");
  }

  @Test
  void removingTheDocumentOrTheNoticeRemovesTheFile() throws Exception {
    long kept = publish(notice("Keeps its text", null, null));
    attach(kept, "a.pdf", "application/pdf", TestPdfs.valid(++pdfs));
    String path = storedDocument();

    HttpResponse<String> removed = admin.delete(ADMIN + "/" + kept + "/document");
    assertThat(removed.statusCode()).as(removed.body()).isEqualTo(200);
    assertThat(json(removed).get("documentType").isNull()).isTrue();
    assertThat(storage.paths()).doesNotContain(path);
    assertProblem(admin.delete(ADMIN + "/" + kept + "/document"), 404, "NOT_FOUND");

    long gone = publish(notice("Goes away", null, null));
    attach(gone, "b.pdf", "application/pdf", TestPdfs.valid(++pdfs));
    String other = storedDocument();
    assertThat(admin.delete(ADMIN + "/" + gone).statusCode()).isEqualTo(204);
    assertThat(storage.paths()).doesNotContain(other);
  }

  // --- helpers ---------------------------------------------------------------------------------

  private HttpResponse<String> attach(long id, String name, String type, byte[] content)
      throws Exception {
    return admin.postFile(ADMIN + "/" + id + "/document", name, type, content);
  }

  /** The notice document stored by the last upload. */
  private String storedDocument() {
    String path = storage.lastWritten();
    assertThat(path).startsWith("notices/");
    return path;
  }

  private long notice(String title, String branch, Integer semester) throws Exception {
    String body =
        """
        {"title":"%s","body":"See the official notice.","category":"EXAM","targetBranch":%s,"targetSemester":%s,"dueDate":null}"""
            .formatted(
                title,
                branch == null ? "null" : "\"" + branch + "\"",
                semester == null ? "null" : semester);
    HttpResponse<String> response = admin.post(ADMIN, body);
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    return json(response).get("id").asLong();
  }

  private long publish(long id) throws Exception {
    assertThat(admin.post(ADMIN + "/" + id + "/publish", "").statusCode()).isEqualTo(204);
    return id;
  }

  private static byte[] picture(String format, int width, int height) throws Exception {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    graphics.setColor(Color.WHITE);
    graphics.fillRect(0, 0, width, height);
    graphics.setColor(Color.BLUE);
    graphics.drawString("Synthetic circular", 20, 40);
    graphics.dispose();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, format, out);
    return out.toByteArray();
  }
}
