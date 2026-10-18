package com.campuscompanion.student;

import static com.campuscompanion.support.ApiTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.support.ApiIntegrationTest;
import com.campuscompanion.support.ApiTestClient;
import com.campuscompanion.support.InMemoryStorageClient;
import com.campuscompanion.support.TestAccounts;
import com.campuscompanion.support.TestImages;
import java.awt.image.BufferedImage;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * A student edits their own personal details and photo (FR-STU-003). Storage is the in-memory
 * stand-in; no test reaches a real bucket.
 */
class OwnProfileIT extends ApiIntegrationTest {

  private static final String ME = "/api/v1/students/me";
  private static final String PHOTO = ME + "/photo";
  private static final String PHOTO_URL = ME + "/photo-url";

  @Autowired private InMemoryStorageClient storage;

  @AfterEach
  void storageWorksAgain() {
    storage.workAgain();
  }

  private ApiTestClient student(String rollNo) throws Exception {
    accounts.student(rollNo, "OWN", 3, "A");
    return studentClient(rollNo);
  }

  private String photoPath(String rollNo) {
    return jdbc.queryForObject(
        "select photo_path from app.student_profiles where roll_no = ?", String.class, rollNo);
  }

  // --- Links, personal email and skills -------------------------------------------------------

  @Test
  void aStudentCanAddTheirLinksAndPersonalEmail() throws Exception {
    ApiTestClient student = student("23OWN101");

    HttpResponse<String> response =
        student.put(
            ME,
            """
            {"phone":null,"bio":null,"personalEmail":" Demo.Student@Example.com ",
             "linkedinUrl":"linkedin.com/in/demo-student","githubUrl":"https://github.com/demo",
             "portfolioUrl":"https://demo.example","skills":"Java, SQL"}
            """);

    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    JsonNode saved = json(student.get(ME));
    assertThat(saved.get("personalEmail").asString()).isEqualTo("demo.student@example.com");
    assertThat(saved.get("linkedinUrl").asString())
        .isEqualTo("https://linkedin.com/in/demo-student");
    assertThat(saved.get("githubUrl").asString()).isEqualTo("https://github.com/demo");
    assertThat(saved.get("portfolioUrl").asString()).isEqualTo("https://demo.example");
    assertThat(saved.get("skills").asString()).isEqualTo("Java, SQL");
  }

  @Test
  void unsafeOrWrongLinksAreRefusedAndNothingIsSaved() throws Exception {
    ApiTestClient student = student("23OWN102");

    HttpResponse<String> response =
        student.put(
            ME,
            """
            {"phone":"+91 98765 43210","personalEmail":"nope","linkedinUrl":"https://github.com/x",
             "githubUrl":"javascript:alert(1)","portfolioUrl":"http://demo.example"}
            """);

    assertThat(invalidFields(response))
        .containsExactlyInAnyOrder("personalEmail", "linkedinUrl", "githubUrl", "portfolioUrl");
    JsonNode profile = json(student.get(ME));
    assertThat(profile.get("phone").isNull()).isTrue();
    assertThat(profile.get("githubUrl").isNull()).isTrue();
  }

  // --- Personal details -----------------------------------------------------------------------

  @Test
  void aStudentCanChangeTheirPhoneAndTheTextAboutThemselves() throws Exception {
    ApiTestClient student = student("23OWN001");
    JsonNode before = json(student.get(ME));
    assertThat(before.get("bio").isNull()).isTrue();
    assertThat(before.get("hasPhoto").asBoolean()).isFalse();

    HttpResponse<String> response =
        student.put(ME, "{\"phone\":\" +91 98765 43210 \",\"bio\":\"  I like graphs.  \"}");

    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    JsonNode saved = json(response);
    assertThat(saved.get("phone").asString()).isEqualTo("+91 98765 43210");
    assertThat(saved.get("bio").asString()).isEqualTo("I like graphs.");
    // What the college manages is exactly as it was.
    for (String field : new String[] {"rollNo", "email", "fullName", "branch", "section"}) {
      assertThat(saved.get(field)).as(field).isEqualTo(before.get(field));
    }
    assertThat(json(student.get(ME))).isEqualTo(saved);

    // Empty values clear the fields again.
    JsonNode cleared = json(student.put(ME, "{\"phone\":\"\",\"bio\":null}"));
    assertThat(cleared.get("phone").isNull()).isTrue();
    assertThat(cleared.get("bio").isNull()).isTrue();
  }

  @Test
  void aChangedPhoneIsAuditedAndAChangedTextIsNot() throws Exception {
    long studentId = accounts.student("23OWN002", "OWN", 3, "A");
    ApiTestClient student = studentClient("23OWN002");

    student.put(ME, "{\"phone\":null,\"bio\":\"Only the text changed.\"}");
    assertThat(auditRows("PROFILE_UPDATED", "STUDENT", studentId)).isZero();

    student.put(ME, "{\"phone\":\"9876543210\",\"bio\":\"Only the text changed.\"}");
    assertThat(auditRows("PROFILE_UPDATED", "STUDENT", studentId)).isEqualTo(1);
    assertThat(latestAudit("PROFILE_UPDATED"))
        .contains("\"phone\": null")
        .contains("\"phone\": \"9876543210\"");
    // The student did it, so the student is the actor.
    assertThat(
            jdbc.queryForObject(
                "select u.login_email from app.audit_logs a join app.users u on u.id = a.actor_user_id"
                    + " where a.action = 'PROFILE_UPDATED' and a.entity_id = ? order by a.id desc limit 1",
                String.class,
                String.valueOf(studentId)))
        .isEqualTo(TestAccounts.emailOf("23OWN002"));

    // Saving the same values again changes nothing and writes nothing.
    student.put(ME, "{\"phone\":\"9876543210\",\"bio\":\"Only the text changed.\"}");
    assertThat(auditRows("PROFILE_UPDATED", "STUDENT", studentId)).isEqualTo(1);
  }

  @Test
  void whatTheCollegeManagesCannotBeChangedByTheStudent() throws Exception {
    ApiTestClient student = student("23OWN003");
    JsonNode before = json(student.get(ME));

    for (String body :
        new String[] {
          "{\"phone\":null,\"bio\":null,\"fullName\":\"Someone Else\"}",
          "{\"phone\":null,\"bio\":null,\"rollNo\":\"23OWN999\"}",
          "{\"phone\":null,\"bio\":null,\"branch\":\"CSE\",\"currentSemester\":8}",
          "{\"phone\":null,\"bio\":null,\"email\":\"other@university.example\"}",
          "{\"phone\":null,\"bio\":null,\"role\":\"ADMIN\"}",
          "{\"phone\":null,\"bio\":null,\"photoPath\":\"resources/someone-elses.pdf\"}"
        }) {
      assertThat(student.put(ME, body).statusCode()).as(body).isEqualTo(400);
    }

    assertThat(json(student.get(ME))).isEqualTo(before);
  }

  @Test
  void thePhoneAndTheTextHaveLimits() throws Exception {
    ApiTestClient student = student("23OWN004");

    assertThat(invalidFields(student.put(ME, "{\"phone\":\"call me maybe\",\"bio\":null}")))
        .containsExactly("phone");
    assertThat(invalidFields(student.put(ME, "{\"phone\":\"123\",\"bio\":null}")))
        .containsExactly("phone");
    assertThat(
            invalidFields(student.put(ME, "{\"phone\":\"<script>1234567</script>\",\"bio\":null}")))
        .contains("phone");
    assertThat(
            invalidFields(
                student.put(ME, "{\"phone\":null,\"bio\":\"%s\"}".formatted("b".repeat(281)))))
        .containsExactly("bio");
    assertThat(
            student
                .put(ME, "{\"phone\":null,\"bio\":\"%s\"}".formatted("b".repeat(280)))
                .statusCode())
        .isEqualTo(200);
  }

  // --- Photo ----------------------------------------------------------------------------------

  @Test
  void aPhotoIsRedrawnStoredAndShownThroughAShortLivedLink() throws Exception {
    ApiTestClient student = student("23OWN005");
    assertProblem(student.get(PHOTO_URL), 404, "NOT_FOUND");
    byte[] upload = TestImages.withExif(TestImages.jpeg(900, 600), 1, "GPS-12.9716N");

    HttpResponse<String> response =
        student.putFile(PHOTO, "../../my face.JPG", "image/jpeg", upload);

    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    assertThat(json(response).get("hasPhoto").asBoolean()).isTrue();
    // The response says that there is a photo, never where it is kept.
    assertThat(response.body()).doesNotContain("profile-photos", "photoPath");
    String path = photoPath("23OWN005");
    assertThat(path).matches("profile-photos/[0-9a-f-]{36}\\.jpg");
    byte[] stored = storage.contentAt(path);
    BufferedImage picture = TestImages.read(stored);
    assertThat(picture.getWidth()).isEqualTo(512);
    assertThat(picture.getHeight()).isEqualTo(512);
    assertThat(new String(stored, java.nio.charset.StandardCharsets.ISO_8859_1))
        .doesNotContain("GPS-12.9716N");

    HttpResponse<String> link = student.get(PHOTO_URL);
    assertThat(link.statusCode()).isEqualTo(200);
    assertThat(json(link).get("url").asString())
        .startsWith("https://storage.example/signed/" + path);
    assertThat(
            Duration.between(Instant.now(), Instant.parse(json(link).get("expiresAt").asString())))
        .isLessThanOrEqualTo(Duration.ofMinutes(5))
        .isGreaterThan(Duration.ofMinutes(4));
  }

  @Test
  void aNewPhotoReplacesTheOldOneAndRemovingLeavesNothingBehind() throws Exception {
    ApiTestClient student = student("23OWN006");
    student.putFile(PHOTO, "first.png", "image/png", TestImages.pngWithTransparency(300, 300));
    String first = photoPath("23OWN006");

    student.putFile(PHOTO, "second.jpg", "image/jpeg", TestImages.jpeg(400, 400));
    String second = photoPath("23OWN006");

    assertThat(second).isNotEqualTo(first);
    assertThat(storage.paths()).contains(second).doesNotContain(first);

    assertThat(student.delete(PHOTO).statusCode()).isEqualTo(204);
    assertThat(storage.paths()).doesNotContain(second);
    assertThat(photoPath("23OWN006")).isNull();
    assertThat(json(student.get(ME)).get("hasPhoto").asBoolean()).isFalse();
    assertProblem(student.get(PHOTO_URL), 404, "NOT_FOUND");
    // Removing again is not an error.
    assertThat(student.delete(PHOTO).statusCode()).isEqualTo(204);
  }

  @Test
  void whatIsNotAPhotoIsRefusedAndNothingIsStored() throws Exception {
    ApiTestClient student = student("23OWN007");
    int filesBefore = storage.paths().size();
    byte[] tooLarge = new byte[2 * 1024 * 1024 + 1];
    System.arraycopy(TestImages.jpeg(100, 100), 0, tooLarge, 0, 200);

    assertProblem(
        student.putFile(PHOTO, "notes.jpg", "image/jpeg", "not a picture".getBytes()),
        415,
        "UPLOAD_INVALID_TYPE");
    assertProblem(
        student.putFile(PHOTO, "anim.gif", "image/gif", TestImages.gif(200, 200)),
        415,
        "UPLOAD_INVALID_TYPE");
    assertProblem(
        student.putFile(PHOTO, "tiny.jpg", "image/jpeg", TestImages.jpeg(32, 32)),
        415,
        "UPLOAD_INVALID_TYPE");
    assertProblem(
        student.putFile(
            PHOTO, "page.svg", "image/svg+xml", "<svg onload=alert(1)></svg>".getBytes()),
        415,
        "UPLOAD_INVALID_TYPE");
    assertProblem(
        student.putFile(PHOTO, "huge.jpg", "image/jpeg", tooLarge), 413, "UPLOAD_TOO_LARGE");

    assertThat(storage.paths()).hasSize(filesBefore);
    assertThat(photoPath("23OWN007")).isNull();
  }

  @Test
  void whenStorageIsDownTheProfileIsLeftAsItWas() throws Exception {
    ApiTestClient student = student("23OWN008");
    student.putFile(PHOTO, "first.jpg", "image/jpeg", TestImages.jpeg(300, 300));
    String kept = photoPath("23OWN008");

    storage.failUploads(true);
    assertProblem(
        student.putFile(PHOTO, "second.jpg", "image/jpeg", TestImages.jpeg(300, 300)),
        503,
        "STORAGE_UNAVAILABLE");
    assertThat(photoPath("23OWN008")).isEqualTo(kept);

    storage.workAgain();
    storage.failDeletes(true);
    assertProblem(student.delete(PHOTO), 503, "STORAGE_UNAVAILABLE");
    // The photo could not be removed from storage, so the profile still points at it.
    assertThat(photoPath("23OWN008")).isEqualTo(kept);
    assertThat(storage.paths()).contains(kept);

    storage.workAgain();
    storage.failSigning(true);
    assertProblem(student.get(PHOTO_URL), 503, "STORAGE_UNAVAILABLE");
    // The rest of the profile still loads.
    assertThat(student.get(ME).statusCode()).isEqualTo(200);
  }

  // --- Who may call what ----------------------------------------------------------------------

  @Test
  void everyStudentWorksOnTheirOwnProfileOnly() throws Exception {
    ApiTestClient first = student("23OWN009");
    ApiTestClient second = student("23OWN010");
    first.putFile(PHOTO, "mine.jpg", "image/jpeg", TestImages.jpeg(300, 300));
    first.put(ME, "{\"phone\":\"9876543210\",\"bio\":\"First student.\"}");
    String firstPath = photoPath("23OWN009");

    // There is no way to name another student: the second student's calls act on their own.
    assertProblem(second.get(PHOTO_URL + "?studentId=1"), 404, "NOT_FOUND");
    assertThat(second.delete(PHOTO).statusCode()).isEqualTo(204);
    second.put(ME, "{\"phone\":null,\"bio\":\"Second student.\"}");

    assertThat(photoPath("23OWN009")).isEqualTo(firstPath);
    assertThat(storage.paths()).contains(firstPath);
    assertThat(json(first.get(ME)).get("bio").asString()).isEqualTo("First student.");
    assertThat(json(first.get(ME)).get("phone").asString()).isEqualTo("9876543210");
  }

  @Test
  void visitorsAndAdminsHaveNoOwnProfile() throws Exception {
    ApiTestClient visitor = visitor();
    ApiTestClient admin = adminClient("own-profile-admin@university.example");
    byte[] photo = TestImages.jpeg(200, 200);

    assertProblem(visitor.put(ME, "{\"phone\":null,\"bio\":null}"), 401, "AUTH_REQUIRED");
    assertProblem(visitor.putFile(PHOTO, "a.jpg", "image/jpeg", photo), 401, "AUTH_REQUIRED");
    assertProblem(visitor.delete(PHOTO), 401, "AUTH_REQUIRED");
    assertProblem(visitor.get(PHOTO_URL), 401, "AUTH_REQUIRED");

    assertProblem(admin.put(ME, "{\"phone\":null,\"bio\":null}"), 403, "ACCESS_DENIED");
    assertProblem(admin.putFile(PHOTO, "a.jpg", "image/jpeg", photo), 403, "ACCESS_DENIED");
    assertProblem(admin.delete(PHOTO), 403, "ACCESS_DENIED");
    assertProblem(admin.get(PHOTO_URL), 403, "ACCESS_DENIED");
  }
}
