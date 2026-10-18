package com.campuscompanion.pyq;

import static com.campuscompanion.support.ApiTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.support.ApiIntegrationTest;
import com.campuscompanion.support.ApiTestClient;
import com.campuscompanion.support.InMemoryStorageClient;
import com.campuscompanion.support.TestPdfs;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * Previous-year papers: an admin adds them (by details or by folder path), students browse, search,
 * open, download and save them. Storage is the in-memory stand-in. All data is synthetic.
 */
class PyqIT extends ApiIntegrationTest {

  private static final String ADMIN = "/api/v1/admin/pyqs";
  private static final String PYQS = "/api/v1/pyqs";
  private static int pages = 1;

  @Autowired private InMemoryStorageClient storage;

  @AfterEach
  void storageWorksAgain() {
    storage.workAgain();
  }

  private ApiTestClient admin() throws Exception {
    return adminClient("pyq-admin@university.example");
  }

  /** A different PDF each time, so no two papers share a checksum. */
  private static byte[] anotherPdf() {
    return TestPdfs.valid(++pages);
  }

  private long add(ApiTestClient admin, String path) throws Exception {
    HttpResponse<String> response =
        admin.postFileWithField(ADMIN, "path", path, "scan.pdf", "application/pdf", anotherPdf());
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    return json(response).get("id").asLong();
  }

  private ApiTestClient student(String rollNo) throws Exception {
    accounts.student(rollNo, "PYQ", 3, "A");
    return studentClient(rollNo);
  }

  private static List<String> labels(JsonNode page) {
    return page.get("content")
        .valueStream()
        .map(
            p -> p.get("subject").asString() + " " + p.get("year") + " " + p.get("kind").asString())
        .toList();
  }

  // --- Adding papers ------------------------------------------------------------------------

  @Test
  void anAdminAddsAPaperByItsFolderPath() throws Exception {
    ApiTestClient admin = admin();

    HttpResponse<String> response =
        admin.postFileWithField(
            ADMIN,
            "path",
            "CSE/Pyq Alpha Subject/2025(December)-supply AR20.pdf",
            "x.pdf",
            "application/pdf",
            anotherPdf());

    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    JsonNode paper = json(response);
    assertThat(paper.get("branch").asString()).isEqualTo("CSE");
    assertThat(paper.get("subject").asString()).isEqualTo("Pyq Alpha Subject");
    assertThat(paper.get("year").asInt()).isEqualTo(2025);
    assertThat(paper.get("monthName").asString()).isEqualTo("December");
    assertThat(paper.get("kind").asString()).isEqualTo("SUPPLY");
    assertThat(paper.get("regulation").asString()).isEqualTo("AR20");
    assertThat(paper.get("downloadName").asString())
        .isEqualTo("CSE - Pyq Alpha Subject - 2025 December Supplementary AR20.pdf");
    // The storage path never leaves the server.
    assertThat(response.body()).doesNotContain("storage").doesNotContain("pyq/CSE/");
    assertThat(storage.paths())
        .anyMatch(path -> path.startsWith("pyq/CSE/") && path.endsWith(".pdf"));
  }

  @Test
  void anAdminAddsAPaperByDetails() throws Exception {
    ApiTestClient admin = admin();
    String details =
        """
        {"branch":"mech","subject":"  Pyq   Beta  Subject ","year":2024,"month":1,"kind":"MID",
         "midNumber":2,"regulation":"AR22","courseCode":"20me31001","variant":1}
        """;

    HttpResponse<String> response =
        admin.postMultipart(ADMIN, details, "scan.pdf", "application/pdf", anotherPdf());

    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    JsonNode paper = json(response);
    assertThat(paper.get("branch").asString()).isEqualTo("MECH");
    assertThat(paper.get("subject").asString()).isEqualTo("Pyq Beta Subject");
    assertThat(paper.get("midNumber").asInt()).isEqualTo(2);
    assertThat(paper.get("courseCode").asString()).isEqualTo("20ME31001");
  }

  @Test
  void theSameFileOrTheSameSittingIsRefused() throws Exception {
    ApiTestClient admin = admin();
    byte[] pdf = anotherPdf();
    String path = "ECE/Pyq Gamma Subject/2024(May)-regular AR20.pdf";
    assertThat(
            admin
                .postFileWithField(ADMIN, "path", path, "a.pdf", "application/pdf", pdf)
                .statusCode())
        .isEqualTo(201);

    HttpResponse<String> sameFile =
        admin.postFileWithField(
            ADMIN,
            "path",
            "ECE/Pyq Gamma Subject/2023(May)-regular.pdf",
            "a.pdf",
            "application/pdf",
            pdf);
    HttpResponse<String> sameSitting =
        admin.postFileWithField(ADMIN, "path", path, "b.pdf", "application/pdf", anotherPdf());
    HttpResponse<String> asSecondSet =
        admin.postFileWithField(
            ADMIN,
            "path",
            "ECE/Pyq Gamma Subject/2024(May)-regular AR20 (2).pdf",
            "c.pdf",
            "application/pdf",
            anotherPdf());

    assertProblem(sameFile, 409, "CONFLICT");
    assertThat(sameFile.body()).contains("already in the library");
    assertProblem(sameSitting, 409, "CONFLICT");
    assertThat(sameSitting.body()).contains("give it a set number");
    assertThat(asSecondSet.statusCode()).isEqualTo(201);
  }

  @Test
  void badFilesAndBadPathsAreRefusedAndNothingIsStored() throws Exception {
    ApiTestClient admin = admin();
    int before = storage.paths().size();

    HttpResponse<String> notAPdf =
        admin.postFileWithField(
            ADMIN,
            "path",
            "CSE/Pyq Delta/2024(May)-regular.pdf",
            "a.pdf",
            "application/pdf",
            TestPdfs.plainText());
    HttpResponse<String> badPath =
        admin.postFileWithField(
            ADMIN, "path", "CSE/Pyq Delta/paper.pdf", "a.pdf", "application/pdf", anotherPdf());
    HttpResponse<String> badBranch =
        admin.postFileWithField(
            ADMIN,
            "path",
            "C/Pyq Delta/2024(May)-regular.pdf",
            "a.pdf",
            "application/pdf",
            anotherPdf());
    HttpResponse<String> midOnRegular =
        admin.postMultipart(
            ADMIN,
            "{\"branch\":\"CSE\",\"subject\":\"Pyq Delta\",\"year\":2024,\"month\":5,\"kind\":\"REGULAR\",\"midNumber\":1}",
            "a.pdf",
            "application/pdf",
            anotherPdf());

    assertProblem(notAPdf, 415, "UPLOAD_INVALID_TYPE");
    assertThat(invalidFields(badPath)).containsExactly("path");
    assertThat(invalidFields(badBranch)).contains("branch");
    assertThat(invalidFields(midOnRegular)).containsExactly("midNumber");
    assertThat(storage.paths()).hasSize(before);
  }

  @Test
  void aFailedSaveRemovesTheStoredFile() throws Exception {
    ApiTestClient admin = admin();
    int before = storage.paths().size();
    storage.failUploads(true);

    HttpResponse<String> response =
        admin.postFileWithField(
            ADMIN,
            "path",
            "CSE/Pyq Epsilon/2024(May)-regular.pdf",
            "a.pdf",
            "application/pdf",
            anotherPdf());

    assertProblem(response, 503, "STORAGE_UNAVAILABLE");
    assertThat(storage.paths()).hasSize(before);
  }

  // --- Browsing and searching -----------------------------------------------------------------

  @Test
  void studentsBrowseSubjectsAndFilterPapers() throws Exception {
    ApiTestClient admin = admin();
    add(admin, "FE/Pyq Zeta Physics/2023(August)-regular.pdf");
    add(admin, "FE/Pyq Zeta Physics/2025(July)-supply AR25.pdf");
    add(admin, "FE/Pyq Zeta Physics/2025(December)-regular AR25.pdf");
    add(admin, "FE/Pyq Zeta Chemistry/2024(January)-supply.pdf");
    ApiTestClient student = student("23PYQ001");

    JsonNode subjects = json(student.get(PYQS + "/subjects?branch=fe&q=zeta"));
    assertThat(subjects).hasSize(2);
    assertThat(subjects.get(0).get("subject").asString()).isEqualTo("Pyq Zeta Chemistry");
    JsonNode physics = subjects.get(1);
    assertThat(physics.get("papers").asInt()).isEqualTo(3);
    assertThat(physics.get("firstYear").asInt()).isEqualTo(2023);
    assertThat(physics.get("latestYear").asInt()).isEqualTo(2025);

    String physicsPapers = PYQS + "?branch=FE&subject=Pyq%20Zeta%20Physics";
    assertThat(labels(json(student.get(physicsPapers))))
        .containsExactly(
            "Pyq Zeta Physics 2025 REGULAR",
            "Pyq Zeta Physics 2025 SUPPLY",
            "Pyq Zeta Physics 2023 REGULAR");
    assertThat(labels(json(student.get(physicsPapers + "&sort=oldest"))).get(0)).contains("2023");
    assertThat(labels(json(student.get(physicsPapers + "&year=2025&kind=SUPPLY"))))
        .containsExactly("Pyq Zeta Physics 2025 SUPPLY");
    assertThat(json(student.get(physicsPapers + "&regulation=AR25")).get("totalElements").asInt())
        .isEqualTo(2);
  }

  @Test
  void searchMatchesSubjectAndYearIgnoresCaseAndTakesWildcardsLiterally() throws Exception {
    ApiTestClient admin = admin();
    add(admin, "CIVIL/Pyq Eta Surveying/2022(May)-regular.pdf");
    add(admin, "CIVIL/Pyq Eta Concrete/2021(May)-regular.pdf");
    ApiTestClient student = student("23PYQ002");

    assertThat(labels(json(student.get(PYQS + "?q=ETA%20SURVEY"))))
        .containsExactly("Pyq Eta Surveying 2022 REGULAR");
    assertThat(labels(json(student.get(PYQS + "?q=2021&branch=civil"))))
        .containsExactly("Pyq Eta Concrete 2021 REGULAR");
    // The words may come in any order and each one must match.
    assertThat(labels(json(student.get(PYQS + "?q=2021%20concrete"))))
        .containsExactly("Pyq Eta Concrete 2021 REGULAR");
    assertThat(json(student.get(PYQS + "?q=2021%20surveying")).get("totalElements").asInt())
        .isZero();
    // A percent sign or an underscore is just a character, so it matches nothing here.
    assertThat(json(student.get(PYQS + "?q=%25")).get("totalElements").asInt()).isZero();
    assertThat(json(student.get(PYQS + "?q=Pyq_Eta")).get("totalElements").asInt()).isZero();
  }

  @Test
  void theOverviewListsBranchesYearsAndRegulations() throws Exception {
    ApiTestClient admin = admin();
    add(admin, "IT/Pyq Theta Mining/2019(May)-regular AR18.pdf");
    ApiTestClient student = student("23PYQ003");

    JsonNode overview = json(student.get(PYQS + "/overview"));

    assertThat(overview.get("branches").valueStream().map(b -> b.get("branch").asString()))
        .contains("IT");
    assertThat(overview.get("years").valueStream().map(JsonNode::asInt)).contains(2019);
    assertThat(overview.get("regulations").valueStream().map(JsonNode::asString)).contains("AR18");
    assertThat(overview.get("totalPapers").asInt()).isPositive();
  }

  @Test
  void resultsArePagedAndBadParametersAreRefused() throws Exception {
    ApiTestClient admin = admin();
    for (int year = 2011; year <= 2013; year++) {
      add(admin, "EEE/Pyq Iota Machines/" + year + "(May)-regular.pdf");
    }
    ApiTestClient student = student("23PYQ004");

    JsonNode first = json(student.get(PYQS + "?subject=Pyq%20Iota%20Machines&size=2&page=0"));
    JsonNode second = json(student.get(PYQS + "?subject=Pyq%20Iota%20Machines&size=2&page=1"));

    assertThat(first.get("totalElements").asInt()).isEqualTo(3);
    assertThat(first.get("content")).hasSize(2);
    assertThat(second.get("content")).hasSize(1);
    assertThat(student.get(PYQS + "?size=0").statusCode()).isEqualTo(400);
    assertThat(student.get(PYQS + "?size=101").statusCode()).isEqualTo(400);
    assertThat(student.get(PYQS + "?year=1999").statusCode()).isEqualTo(400);
    assertThat(student.get(PYQS + "?kind=FINAL").statusCode()).isEqualTo(400);
  }

  // --- Opening, saving ---------------------------------------------------------------------------

  @Test
  void aLinkShowsOrSavesThePaperAndOnlyADownloadIsCounted() throws Exception {
    ApiTestClient admin = admin();
    long id = add(admin, "MECH/Pyq Kappa Thermodynamics/2024(December)-regular AR22.pdf");
    ApiTestClient student = student("23PYQ005");

    JsonNode view = json(student.get(PYQS + "/" + id + "/link"));
    JsonNode download = json(student.get(PYQS + "/" + id + "/link?download=true"));

    assertThat(view.get("url").asString()).startsWith("https://storage.example/signed/pyq/MECH/");
    assertThat(view.get("url").asString()).doesNotContain("download=");
    assertThat(download.get("url").asString()).contains("download=");
    assertThat(download.get("url").asString())
        .contains("download=MECH - Pyq Kappa Thermodynamics - 2024 December Regular AR22.pdf");
    assertThat(download.get("expiresAt").asString()).isNotBlank();
    JsonNode paper =
        json(student.get(PYQS + "?subject=Pyq%20Kappa%20Thermodynamics")).get("content").get(0);
    assertThat(paper.get("downloads").asInt()).isEqualTo(1);
  }

  @Test
  void savingIsPerStudentAndRepeatingItChangesNothing() throws Exception {
    ApiTestClient admin = admin();
    long id = add(admin, "CSE/Pyq Lambda Networks/2024(May)-regular.pdf");
    ApiTestClient first = student("23PYQ006");
    ApiTestClient second = student("23PYQ007");

    assertThat(first.put(PYQS + "/" + id + "/save", "").statusCode()).isEqualTo(204);
    assertThat(first.put(PYQS + "/" + id + "/save", "").statusCode()).isEqualTo(204);

    assertThat(json(first.get(PYQS + "/saved")).get("content")).hasSize(1);
    assertThat(
            json(first.get(PYQS + "?subject=Pyq%20Lambda%20Networks"))
                .get("content")
                .get(0)
                .get("bookmarked")
                .asBoolean())
        .isTrue();
    assertThat(json(second.get(PYQS + "/saved")).get("content")).isEmpty();
    assertThat(
            json(second.get(PYQS + "?subject=Pyq%20Lambda%20Networks"))
                .get("content")
                .get(0)
                .get("bookmarked")
                .asBoolean())
        .isFalse();

    assertThat(first.delete(PYQS + "/" + id + "/save").statusCode()).isEqualTo(204);
    assertThat(first.delete(PYQS + "/" + id + "/save").statusCode()).isEqualTo(204);
    assertThat(json(first.get(PYQS + "/saved")).get("content")).isEmpty();
  }

  // --- Hiding and removing
  // ---------------------------------------------------------------------------

  @Test
  void aHiddenPaperIsNotFoundByStudentsButAdminsStillSeeIt() throws Exception {
    ApiTestClient admin = admin();
    long id = add(admin, "CSE/Pyq Mu Compilers/2024(May)-regular.pdf");
    ApiTestClient student = student("23PYQ008");
    student.put(PYQS + "/" + id + "/save", "");

    JsonNode shown = json(admin.get(ADMIN + "?q=pyq%20mu")).get("content").get(0);
    String update =
        """
        {"metadata":{"branch":"CSE","subject":"Pyq Mu Compilers","year":2024,"month":5,"kind":"REGULAR"},"status":"HIDDEN"}
        """;
    assertThat(admin.put(ADMIN + "/" + id, update).statusCode()).isEqualTo(200);

    assertThat(shown.get("status").asString()).isEqualTo("PUBLISHED");
    assertThat(json(student.get(PYQS + "?q=pyq%20mu")).get("totalElements").asInt()).isZero();
    assertThat(json(student.get(PYQS + "/saved")).get("content")).isEmpty();
    assertProblem(student.get(PYQS + "/" + id + "/link"), 404, "NOT_FOUND");
    assertProblem(student.put(PYQS + "/" + id + "/save", ""), 404, "NOT_FOUND");
    assertThat(json(admin.get(ADMIN + "?status=HIDDEN&q=pyq%20mu")).get("content")).hasSize(1);
  }

  @Test
  void anAdminCanCorrectAPaperButNotIntoAnExistingOne() throws Exception {
    ApiTestClient admin = admin();
    long first = add(admin, "CSE/Pyq Nu Graphics/2024(May)-regular.pdf");
    add(admin, "CSE/Pyq Nu Graphics/2024(December)-regular.pdf");
    String sameAsTheOther =
        "{\"metadata\":{\"branch\":\"CSE\",\"subject\":\"Pyq Nu Graphics\",\"year\":2024,\"month\":12,\"kind\":\"REGULAR\"},\"status\":\"PUBLISHED\"}";
    String fixed =
        "{\"metadata\":{\"branch\":\"CSE\",\"subject\":\"Pyq Nu Graphics\",\"year\":2023,\"month\":12,\"kind\":\"SUPPLY\",\"regulation\":\"AR20\"},\"status\":\"PUBLISHED\"}";

    assertProblem(admin.put(ADMIN + "/" + first, sameAsTheOther), 409, "CONFLICT");
    HttpResponse<String> response = admin.put(ADMIN + "/" + first, fixed);

    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    assertThat(json(response).get("year").asInt()).isEqualTo(2023);
    assertThat(json(response).get("kind").asString()).isEqualTo("SUPPLY");
    assertProblem(admin.put(ADMIN + "/999999", fixed), 404, "NOT_FOUND");
  }

  @Test
  void anAdminCanOpenAHiddenPaperWithoutCountingADownload() throws Exception {
    ApiTestClient admin = admin();
    long id = add(admin, "CSE/Pyq Rho Vision/2024(May)-regular.pdf");
    String hide =
        "{\"metadata\":{\"branch\":\"CSE\",\"subject\":\"Pyq Rho Vision\",\"year\":2024,\"month\":5,\"kind\":\"REGULAR\"},\"status\":\"HIDDEN\"}";
    admin.put(ADMIN + "/" + id, hide);

    JsonNode link = json(admin.get(ADMIN + "/" + id + "/link"));

    assertThat(link.get("url").asString()).contains("pyq/CSE/");
    assertThat(
            json(admin.get(ADMIN + "?q=pyq%20rho")).get("content").get(0).get("downloads").asInt())
        .isZero();
    assertProblem(admin.get(ADMIN + "/999999/link"), 404, "NOT_FOUND");
  }

  @Test
  void deletingRemovesTheFileTheRowAndTheBookmarks() throws Exception {
    ApiTestClient admin = admin();
    long id = add(admin, "CSE/Pyq Xi Databases/2024(May)-regular.pdf");
    ApiTestClient student = student("23PYQ009");
    student.put(PYQS + "/" + id + "/save", "");
    int filesBefore = storage.paths().size();

    assertThat(admin.delete(ADMIN + "/" + id).statusCode()).isEqualTo(204);

    assertThat(storage.paths()).hasSize(filesBefore - 1);
    assertThat(json(student.get(PYQS + "/saved")).get("content")).isEmpty();
    assertThat(admin.delete(ADMIN + "/" + id).statusCode()).isEqualTo(404);
  }

  @Test
  void ifTheFileCannotBeRemovedTheRowStays() throws Exception {
    ApiTestClient admin = admin();
    long id = add(admin, "CSE/Pyq Omicron Security/2024(May)-regular.pdf");
    storage.failDeletes(true);

    HttpResponse<String> response = admin.delete(ADMIN + "/" + id);

    assertProblem(response, 503, "STORAGE_UNAVAILABLE");
    assertThat(json(admin.get(ADMIN + "?q=pyq%20omicron")).get("content")).hasSize(1);
  }

  // --- Who may do what ------------------------------------------------------------------------

  @Test
  void studentsCannotManageAndAdminsCannotUseTheStudentPages() throws Exception {
    ApiTestClient student = student("23PYQ010");

    assertProblem(student.get(ADMIN), 403, "ACCESS_DENIED");
    assertProblem(
        student.postFileWithField(
            ADMIN,
            "path",
            "CSE/Pyq Pi/2024(May)-regular.pdf",
            "a.pdf",
            "application/pdf",
            anotherPdf()),
        403,
        "ACCESS_DENIED");
    assertProblem(admin().get(PYQS + "/overview"), 403, "ACCESS_DENIED");
    assertProblem(visitor().get(PYQS), 401, "AUTH_REQUIRED");
    assertProblem(visitor().get(ADMIN), 401, "AUTH_REQUIRED");
  }
}
