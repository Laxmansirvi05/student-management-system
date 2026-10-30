package com.campuscompanion.coding;

import static com.campuscompanion.support.ApiTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.coding.platform.CodingPlatform;
import com.campuscompanion.coding.platform.PlatformDetails;
import com.campuscompanion.coding.platform.PlatformException;
import com.campuscompanion.coding.platform.PlatformProfile;
import com.campuscompanion.coding.platform.PlatformReader;
import com.campuscompanion.coding.platform.PlatformReaders;
import com.campuscompanion.coding.platform.PlatformStats;
import com.campuscompanion.support.ApiIntegrationTest;
import com.campuscompanion.support.ApiTestClient;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * The coding portfolio and leaderboards (FR-COD-001 to FR-COD-005) through the API, with every
 * coding site replaced by a fake that answers what each test sets, so no outside site is called.
 * All data is synthetic.
 */
@Import(CodingIT.FakeSites.class)
@TestPropertySource(properties = "app.coding.sync-pause=10ms")
class CodingIT extends ApiIntegrationTest {

  private static final String PORTFOLIO = "/api/v1/coding/portfolio";
  private static final String ACCOUNTS = "/api/v1/coding/accounts";
  private static final String VISIBILITY = "/api/v1/coding/leaderboard-visibility";
  private static final String LEADERBOARD = "/api/v1/coding/leaderboard";
  private static final String ADMIN_LEADERBOARD = "/api/v1/admin/coding/leaderboard";
  private static final String ADMIN = "coding-admin@university.example";

  /** What each fake site answers, by "PLATFORM:handle"; a handle not set is not found. */
  static final Map<String, Object> ANSWERS = new ConcurrentHashMap<>();

  @Autowired private CodingSyncJob job;

  @TestConfiguration
  static class FakeSites {
    @Bean
    @Primary
    PlatformReaders fakePlatformReaders() {
      return new PlatformReaders(
          Arrays.stream(CodingPlatform.values())
              .map(FakeReader::new)
              .map(r -> (PlatformReader) r)
              .toList());
    }
  }

  record FakeReader(CodingPlatform platform) implements PlatformReader {
    @Override
    public PlatformProfile read(String handle) throws PlatformException {
      Object answer = ANSWERS.get(platform + ":" + handle);
      if (answer instanceof PlatformProfile profile) {
        return profile;
      }
      throw new PlatformException(
          answer == null ? PlatformException.Kind.NOT_FOUND : (PlatformException.Kind) answer);
    }
  }

  private static void answer(CodingPlatform platform, String handle, int solved, String text) {
    ANSWERS.put(
        platform + ":" + handle,
        new PlatformProfile(
            new PlatformStats(
                solved, null, null, null, 1500, 1600, 4, "Knight", null, null, null, null),
            List.of(text)));
  }

  private ApiTestClient student(String rollNo) throws Exception {
    accounts.student(rollNo, "COD", 3, "A");
    return studentClient(rollNo);
  }

  private static String link(CodingPlatform platform, String handle) {
    return "{\"platform\":\"%s\",\"handle\":\"%s\"}".formatted(platform, handle);
  }

  private JsonNode connect(ApiTestClient who, CodingPlatform platform, String handle)
      throws Exception {
    HttpResponse<String> created = who.post(ACCOUNTS, link(platform, handle));
    assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
    return json(created);
  }

  /**
   * Links an account, puts its code in the fake profile and verifies it. Returns the account ID.
   */
  private long verified(ApiTestClient who, CodingPlatform platform, String handle, int solved)
      throws Exception {
    JsonNode account = connect(who, platform, handle);
    answer(platform, handle, solved, "About me " + account.get("verificationCode").asString());
    HttpResponse<String> verify =
        who.post(ACCOUNTS + "/" + account.get("id").asLong() + "/verify", "");
    assertThat(verify.statusCode()).as(verify.body()).isEqualTo(200);
    return account.get("id").asLong();
  }

  private void allowAnotherAttempt(long accountId) {
    jdbc.update(
        "update app.coding_accounts set last_attempt_at = now() - interval '1 hour' where id = ?",
        accountId);
  }

  // --- access ----------------------------------------------------------------------------------

  @Test
  void onlyStudentsUseThePortfolioAndOnlyAdminsTheAdminLeaderboard() throws Exception {
    ApiTestClient admin = adminClient(ADMIN);
    ApiTestClient me = student("23COD001");
    for (String path : List.of(PORTFOLIO, LEADERBOARD)) {
      assertProblem(visitor().get(path), 401, "AUTH_REQUIRED");
      assertProblem(admin.get(path), 403, "ACCESS_DENIED");
    }
    assertProblem(admin.post(ACCOUNTS, link(CodingPlatform.LEETCODE, "x")), 403, "ACCESS_DENIED");
    assertProblem(visitor().get(ADMIN_LEADERBOARD), 401, "AUTH_REQUIRED");
    assertProblem(me.get(ADMIN_LEADERBOARD), 403, "ACCESS_DENIED");
    assertProblem(me.get("/api/v1/admin/coding/students/1/portfolio"), 403, "ACCESS_DENIED");
  }

  @Test
  void anotherStudentsAccountIsNeverSeenOrChanged() throws Exception {
    ApiTestClient owner = student("23COD002");
    ApiTestClient other = student("23COD003");
    long id = connect(owner, CodingPlatform.CODEFORCES, "cod_owner_cf").get("id").asLong();

    assertThat(json(other.get(PORTFOLIO)).get("accounts")).isEmpty();
    assertProblem(other.post(ACCOUNTS + "/" + id + "/verify", ""), 404, "NOT_FOUND");
    assertProblem(other.post(ACCOUNTS + "/" + id + "/refresh", ""), 404, "NOT_FOUND");
    assertProblem(other.delete(ACCOUNTS + "/" + id), 404, "NOT_FOUND");
    assertThat(json(owner.get(PORTFOLIO)).get("accounts")).hasSize(1);
  }

  // --- linking and verifying -------------------------------------------------------------------

  @Test
  void anAccountIsLinkedVerifiedWithTheCodeAndCounted() throws Exception {
    ApiTestClient me = student("23COD004");
    JsonNode account =
        connect(me, CodingPlatform.LEETCODE, "https://leetcode.com/u/cod_verify_lc/");
    long id = account.get("id").asLong();
    String code = account.get("verificationCode").asString();
    assertThat(account.get("handle").asString()).isEqualTo("cod_verify_lc");
    assertThat(account.get("status").asString()).isEqualTo("PENDING");
    assertThat(code).matches("CC-[A-Z2-9]{8}");
    assertThat(account.get("profileUrl").asString())
        .isEqualTo("https://leetcode.com/u/cod_verify_lc/");

    // The profile exists but does not carry the code yet.
    answer(CodingPlatform.LEETCODE, "cod_verify_lc", 150, "Just a bio");
    assertProblem(me.post(ACCOUNTS + "/" + id + "/verify", ""), 422, "CODING_VERIFICATION_FAILED");
    // A second press straight away has to wait.
    assertProblem(me.post(ACCOUNTS + "/" + id + "/verify", ""), 429, "RATE_LIMITED");

    allowAnotherAttempt(id);
    answer(CodingPlatform.LEETCODE, "cod_verify_lc", 150, "my code: " + code.toLowerCase());
    JsonNode verified = json(me.post(ACCOUNTS + "/" + id + "/verify", ""));
    assertThat(verified.get("status").asString()).isEqualTo("VERIFIED");
    assertThat(verified.get("verificationCode").isNull()).isTrue();
    assertThat(verified.get("problemsSolved").asInt()).isEqualTo(150);
    assertThat(verified.get("rankLabel").asString()).isEqualTo("Knight");

    JsonNode portfolio = json(me.get(PORTFOLIO));
    assertThat(portfolio.get("totalSolved").asInt()).isEqualTo(150);
    assertThat(portfolio.get("verifiedAccounts").asInt()).isEqualTo(1);
    assertThat(portfolio.get("journey")).hasSize(1);
    assertThat(portfolio.get("journey").get(0).get("problemsSolved").asInt()).isEqualTo(150);
    assertThat(portfolio.get("student").get("rollNo").asString()).isEqualTo("23COD004");
    assertThat(portfolio.get("problemSolving").get("questionsSolved").asInt()).isEqualTo(150);
    assertThat(portfolio.get("development").isNull()).isTrue();
  }

  @Test
  void aProfileThatDoesNotExistOrASiteThatIsDownIsReported() throws Exception {
    ApiTestClient me = student("23COD005");
    long missing = connect(me, CodingPlatform.CODECHEF, "cod_nobody").get("id").asLong();
    assertProblem(
        me.post(ACCOUNTS + "/" + missing + "/verify", ""), 422, "CODING_PROFILE_NOT_FOUND");

    long down = connect(me, CodingPlatform.HACKERRANK, "cod_down").get("id").asLong();
    ANSWERS.put("HACKERRANK:cod_down", PlatformException.Kind.UNAVAILABLE);
    assertProblem(
        me.post(ACCOUNTS + "/" + down + "/verify", ""), 503, "CODING_PLATFORM_UNAVAILABLE");
  }

  @Test
  void badInputAndDuplicatesAreRefused() throws Exception {
    ApiTestClient me = student("23COD006");
    ApiTestClient other = student("23COD007");
    assertThat(invalidFields(me.post(ACCOUNTS, "{\"platform\":null,\"handle\":\"\"}")))
        .containsExactlyInAnyOrder("platform", "handle");
    assertThat(invalidFields(me.post(ACCOUNTS, link(CodingPlatform.GITHUB, "not a handle"))))
        .containsExactly("handle");

    verified(me, CodingPlatform.GEEKSFORGEEKS, "cod_dup_gfg", 40);
    // One account per site per student.
    assertProblem(
        me.post(ACCOUNTS, link(CodingPlatform.GEEKSFORGEEKS, "cod_dup_two")), 409, "CONFLICT");
    // A handle verified by one student cannot be linked by another, whatever the letter case.
    assertProblem(
        other.post(ACCOUNTS, link(CodingPlatform.GEEKSFORGEEKS, "COD_DUP_GFG")), 409, "CONFLICT");
  }

  @Test
  void refreshIsLimitedAndAFailedReadKeepsTheLastNumbers() throws Exception {
    ApiTestClient me = student("23COD008");
    long pending = connect(me, CodingPlatform.CODEFORCES, "cod_pending_cf").get("id").asLong();
    assertProblem(me.post(ACCOUNTS + "/" + pending + "/refresh", ""), 409, "CONFLICT");

    long id = verified(me, CodingPlatform.LEETCODE, "cod_refresh_lc", 80);
    assertProblem(me.post(ACCOUNTS + "/" + id + "/refresh", ""), 429, "RATE_LIMITED");

    allowAnotherAttempt(id);
    answer(CodingPlatform.LEETCODE, "cod_refresh_lc", 95, "");
    assertThat(json(me.post(ACCOUNTS + "/" + id + "/refresh", "")).get("problemsSolved").asInt())
        .isEqualTo(95);

    allowAnotherAttempt(id);
    ANSWERS.put("LEETCODE:cod_refresh_lc", PlatformException.Kind.UNAVAILABLE);
    assertProblem(
        me.post(ACCOUNTS + "/" + id + "/refresh", ""), 503, "CODING_PLATFORM_UNAVAILABLE");
    JsonNode kept = json(me.get(PORTFOLIO)).get("accounts").get(1);
    assertThat(kept.get("problemsSolved").asInt()).isEqualTo(95);
    assertThat(kept.get("lastSyncError").asString()).isEqualTo("UNAVAILABLE");
  }

  @Test
  void removingAnAccountRemovesItsHistory() throws Exception {
    ApiTestClient me = student("23COD009");
    long id = verified(me, CodingPlatform.HACKERRANK, "cod_remove_hr", 12);
    assertThat(me.delete(ACCOUNTS + "/" + id).statusCode()).isEqualTo(204);
    assertThat(json(me.get(PORTFOLIO)).get("accounts")).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from app.coding_snapshots where account_id = ?",
                Integer.class,
                id))
        .isZero();
  }

  @Test
  void gitHubIsShownButAddsNothingToTheTotal() throws Exception {
    ApiTestClient me = student("23COD010");
    JsonNode github = connect(me, CodingPlatform.GITHUB, "cod-github");
    ANSWERS.put(
        "GITHUB:cod-github",
        new PlatformProfile(
            new PlatformStats(null, null, null, null, null, null, null, null, null, 4, 2, 1),
            List.of(github.get("verificationCode").asString()),
            new PlatformDetails(
                Map.of(today().toString(), 5, today().minusDays(1).toString(), 2),
                Map.of(),
                List.of(),
                Map.of("Java", 3, "HTML", 1),
                List.of(
                    new PlatformDetails.Project(
                        "app", "https://github.com/cod-github/app", null, "Java", 2)),
                null,
                40,
                3,
                null)));
    assertThat(me.post(ACCOUNTS + "/" + github.get("id").asLong() + "/verify", "").statusCode())
        .isEqualTo(200);
    verified(me, CodingPlatform.CODECHEF, "cod_total_cc", 30);
    JsonNode portfolio = json(me.get(PORTFOLIO));
    assertThat(portfolio.get("verifiedAccounts").asInt()).isEqualTo(2);
    assertThat(portfolio.get("totalSolved").asInt()).isEqualTo(30);
    JsonNode development = portfolio.get("development");
    assertThat(development.get("contributions").get("total").asInt()).isEqualTo(7);
    assertThat(development.get("contributions").get("currentStreak").asInt()).isEqualTo(2);
    assertThat(development.get("languages").get("Java").asInt()).isEqualTo(75);
    assertThat(development.get("commits").asInt()).isEqualTo(40);
    assertThat(development.get("projects").get(0).get("name").asString()).isEqualTo("app");
  }

  // --- leaderboards ----------------------------------------------------------------------------

  @Test
  void theStudentsLeaderboardShowsOnlyThoseWhoChoseToShow() throws Exception {
    ApiTestClient top = student("23COD011");
    ApiTestClient hidden = student("23COD012");
    verified(top, CodingPlatform.CODEFORCES, "cod_lb_top", 900);
    verified(hidden, CodingPlatform.CODEFORCES, "cod_lb_hidden", 950);

    JsonNode before = json(top.get(LEADERBOARD));
    assertThat(before.get("me").get("showOnLeaderboard").asBoolean()).isFalse();
    assertThat(before.get("me").get("rank").isNull()).isTrue();
    assertThat(before.get("me").get("totalSolved").asInt()).isEqualTo(900);

    assertThat(json(top.put(VISIBILITY, "{\"show\":true}")).get("show").asBoolean()).isTrue();
    JsonNode after = json(top.get(LEADERBOARD + "?size=100"));
    assertThat(after.get("me").get("rank").asInt()).isPositive();
    List<String> names = texts(after.get("content"), "fullName");
    assertThat(names).contains("Demo 23COD011").doesNotContain("Demo 23COD012");
    JsonNode mine =
        after
            .get("content")
            .valueStream()
            .filter(e -> e.get("me").asBoolean())
            .findFirst()
            .orElseThrow();
    assertThat(mine.get("totalSolved").asInt()).isEqualTo(900);
    assertThat(mine.has("rollNo")).isFalse();

    assertThat(json(top.put(VISIBILITY, "{\"show\":false}")).get("show").asBoolean()).isFalse();
    assertThat(texts(json(top.get(LEADERBOARD + "?size=100")).get("content"), "fullName"))
        .doesNotContain("Demo 23COD011");
    assertThat(invalidFields(top.put(VISIBILITY, "{}"))).containsExactly("show");
  }

  @Test
  void adminsSeeEveryStudentWithFiltersAndAReadOnlyPortfolio() throws Exception {
    ApiTestClient admin = adminClient(ADMIN);
    Long studentId = accounts.student("23COD013", "CDA", 5, "B");
    ApiTestClient me = studentClient("23COD013");
    verified(me, CodingPlatform.LEETCODE, "cod_admin_lc", 61);
    connect(me, CodingPlatform.CODECHEF, "cod_admin_cc");

    JsonNode page = json(admin.get(ADMIN_LEADERBOARD + "?branch=CDA&semester=5&section=B"));
    assertThat(page.get("totalElements").asInt()).isEqualTo(1);
    JsonNode row = page.get("content").get(0);
    assertThat(row.get("rank").asInt()).isEqualTo(1);
    assertThat(row.get("rollNo").asString()).isEqualTo("23COD013");
    assertThat(row.get("totalSolved").asInt()).isEqualTo(61);
    assertThat(row.get("verifiedAccounts").asInt()).isEqualTo(1);
    assertThat(row.get("pendingAccounts").asInt()).isEqualTo(1);
    assertThat(row.get("showOnLeaderboard").asBoolean()).isFalse();
    assertThat(json(admin.get(ADMIN_LEADERBOARD + "?q=23cod013")).get("totalElements").asInt())
        .isEqualTo(1);

    JsonNode portfolio =
        json(admin.get("/api/v1/admin/coding/students/" + studentId + "/portfolio"));
    assertThat(portfolio.get("rollNo").asString()).isEqualTo("23COD013");
    assertThat(portfolio.get("portfolio").get("totalSolved").asInt()).isEqualTo(61);
    for (JsonNode account : portfolio.get("portfolio").get("accounts")) {
      assertThat(account.get("verificationCode").isNull()).isTrue();
    }
    assertProblem(admin.get("/api/v1/admin/coding/students/999999/portfolio"), 404, "NOT_FOUND");
  }

  @Test
  void theNightlyJobReadsVerifiedAccountsThatAreDue() throws Exception {
    ApiTestClient me = student("23COD014");
    long id = verified(me, CodingPlatform.GEEKSFORGEEKS, "cod_job_gfg", 20);
    jdbc.update(
        "update app.coding_accounts set last_synced_at = now() - interval '2 days' where id = ?",
        id);
    answer(CodingPlatform.GEEKSFORGEEKS, "cod_job_gfg", 27, "");

    job.run();

    assertThat(
            jdbc.queryForObject(
                "select problems_solved from app.coding_accounts where id = ?", Integer.class, id))
        .isEqualTo(27);
  }

  // --- badge pictures ----------------------------------------------------------------------------

  @Test
  void aKeptBadgePictureIsServedToAnyoneLoggedInAndCannotRunAnything() throws Exception {
    String key = "a".repeat(64);
    jdbc.update(
        "insert into app.coding_badge_images (id, source, content_type, data) values (?, ?, ?, ?)"
            + " on conflict do nothing",
        key,
        "https://cdn.codechef.com/images/badges/problem/gold.svg",
        "image/svg+xml",
        "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>"
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    String path = "/api/v1/coding-badges/" + key;

    HttpResponse<String> forStudent = student("23COD015").get(path);
    assertThat(forStudent.statusCode()).isEqualTo(200);
    assertThat(forStudent.headers().firstValue("Content-Type"))
        .hasValueSatisfying(type -> assertThat(type).startsWith("image/svg+xml"));
    assertThat(forStudent.headers().firstValue("Content-Security-Policy"))
        .hasValueSatisfying(
            policy -> assertThat(policy).contains("default-src 'none'").contains("sandbox"));
    assertThat(forStudent.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
    assertThat(adminClient(ADMIN).get(path).statusCode()).isEqualTo(200);
    assertProblem(visitor().get(path), 401, "AUTH_REQUIRED");
    assertProblem(
        student("23COD015").get("/api/v1/coding-badges/" + "b".repeat(64)), 404, "NOT_FOUND");
  }
}
