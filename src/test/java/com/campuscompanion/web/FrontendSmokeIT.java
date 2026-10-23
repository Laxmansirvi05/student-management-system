package com.campuscompanion.web;

import static com.campuscompanion.support.ApiTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.support.ApiIntegrationTest;
import com.campuscompanion.support.ApiTestClient;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * A smoke test of the whole frontend through the running application (CC-09): as a visitor, a
 * student and an administrator, every page opens, and every script, style sheet, font and module it
 * loads, directly or through an import, is really served. A deleted or renamed file, a broken
 * import or a page that lost its route fails here. The robot's 3D files, the landing page's bundle
 * and saving a note are checked too. Rendering itself is checked by hand in a browser (docs/17).
 */
class FrontendSmokeIT extends ApiIntegrationTest {

  private static final Path STATIC = Path.of("src/main/resources/static");

  private static final Pattern PAGE_ASSET =
      Pattern.compile("<(?:script|link)\\b[^>]*?\\b(?:src|href)=\"(/[^\"#?]+)\"");
  private static final Pattern MODULE_IMPORT =
      Pattern.compile(
          "(?:\\bfrom\\s*|\\bimport\\s*\\(?\\s*)[\"']((?:\\./|\\.\\./|/)[^\"']+\\.js)[\"']");
  private static final Pattern CSS_URL = Pattern.compile("url\\([\"']?(/[^\"')#?]+)");

  /** The robot loads these by name at run time, not through an import the crawl can see. */
  private static final List<String> ROBOT_FILES =
      List.of(
          "/vendor/spline/runtime.js",
          "/robot/robot-scene.splinecode",
          "/vendor/spline/wasm/draco_decoder.wasm",
          "/vendor/spline/wasm/draco_wasm_wrapper.js");

  private static List<String> pagesIn(String folder) throws IOException {
    try (Stream<Path> files = Files.list(STATIC.resolve(folder))) {
      return files
          .map(path -> "/" + folder + "/" + path.getFileName())
          .filter(path -> path.endsWith(".html"))
          .sorted()
          .toList();
    }
  }

  /** Opens each page and follows everything it loads. Returns the problems found, if any. */
  private static List<String> openAll(ApiTestClient client, List<String> pages) throws Exception {
    List<String> problems = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    Deque<String> toLoad = new ArrayDeque<>();
    for (String page : pages) {
      HttpResponse<String> response = client.get(page);
      if (response.statusCode() != 200) {
        problems.add(page + " -> " + response.statusCode());
        continue;
      }
      Matcher asset = PAGE_ASSET.matcher(response.body());
      while (asset.find()) {
        toLoad.add(asset.group(1));
      }
    }
    while (!toLoad.isEmpty()) {
      String path = toLoad.poll();
      if (!seen.add(path)) {
        continue;
      }
      HttpResponse<String> response = client.get(path);
      if (response.statusCode() != 200) {
        problems.add(path + " -> " + response.statusCode());
        continue;
      }
      if (path.endsWith(".js")
          && !path.startsWith("/vendor/")
          && !path.startsWith("/js/landing/")) {
        Matcher imported = MODULE_IMPORT.matcher(response.body());
        while (imported.find()) {
          toLoad.add(URI.create(path).resolve(imported.group(1)).getPath());
        }
      }
      if (path.endsWith(".css")) {
        Matcher url = CSS_URL.matcher(response.body());
        while (url.find()) {
          toLoad.add(url.group(1));
        }
      }
    }
    return problems;
  }

  @Test
  void theLandingLoginAndLegalPagesOpenWithEverythingTheyLoad() throws Exception {
    List<String> pages = List.of("/", "/login.html", "/privacy.html", "/terms.html");
    assertThat(openAll(visitor(), pages)).isEmpty();
    String landing = visitor().get("/").body();
    assertThat(landing).containsPattern("/js/landing/main-[A-Za-z0-9_-]+\\.js");
    assertThat(landing).containsPattern("/css/landing/main-[A-Za-z0-9_-]+\\.css");
  }

  @Test
  void everyStudentPageOpensWithEveryScriptStyleAndModuleItLoads() throws Exception {
    accounts.student("SMKE0001", "CSE", 3, "A");
    ApiTestClient student = studentClient("SMKE0001");
    List<String> pages = pagesIn("student");
    assertThat(pages).hasSizeGreaterThanOrEqualTo(18);
    assertThat(openAll(student, pages)).isEmpty();
  }

  @Test
  void theRobotsSceneFilesAreServedToAStudent() throws Exception {
    accounts.student("SMKE0002", "CSE", 3, "A");
    ApiTestClient student = studentClient("SMKE0002");
    for (String file : ROBOT_FILES) {
      assertThat(student.get(file).statusCode()).as(file).isEqualTo(200);
    }
    // The robot is started from the shared page script, so every student page has it.
    assertThat(student.get("/js/shell.js").body()).contains("robot/robot.js");
  }

  @Test
  void everyAdminPageOpensWithEveryScriptStyleAndModuleItLoads() throws Exception {
    ApiTestClient admin = adminClient("smoke-admin@university.example");
    List<String> pages = pagesIn("admin");
    assertThat(pages).hasSizeGreaterThanOrEqualTo(14);
    assertThat(openAll(admin, pages)).isEmpty();
  }

  @Test
  void aStudentCanLogInOpenNotesAndSaveANote() throws Exception {
    accounts.student("SMKE0003", "CSE", 3, "A");
    ApiTestClient student = studentClient("SMKE0003");
    assertThat(student.get("/api/v1/auth/me").body()).contains("\"role\":\"STUDENT\"");
    assertThat(student.get("/student/notes.html").statusCode()).isEqualTo(200);

    HttpResponse<String> created =
        student.post(
            "/api/v1/notes",
            "{\"title\":\"Smoke\",\"content\":\"<p>one</p>\",\"source\":\"manual\"}");
    assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
    long id = json(created).get("id").asLong();
    HttpResponse<String> saved =
        student.patch("/api/v1/notes/" + id, "{\"content\":\"<p>two</p>\"}");
    assertThat(saved.statusCode()).as(saved.body()).isEqualTo(200);
    JsonNode note = json(student.get("/api/v1/notes/" + id));
    assertThat(note.get("content").asString()).isEqualTo("<p>two</p>");
  }

  @Test
  void aVisitorIsKeptOutOfStudentAndAdminPages() throws Exception {
    for (String page : List.of("/student/dashboard.html", "/admin/dashboard.html")) {
      assertThat(visitor().get(page).statusCode()).as(page).isNotEqualTo(200);
    }
  }
}
