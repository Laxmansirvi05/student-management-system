package com.campuscompanion.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.auth.Role;
import com.campuscompanion.auth.User;
import com.campuscompanion.auth.UserRepository;
import com.campuscompanion.support.ApiTestClient;
import com.campuscompanion.support.PostgresIntegrationTest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Task T1-09: which pages each kind of user can open, and where everyone else is sent
 * (docs/06-app-flow.md section 2, docs/10-authorization-matrix.md section 4.9).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PageRoutingIT extends PostgresIntegrationTest {

  private static final String STUDENT = "page23cse001@university.example";
  private static final String ADMIN = "page.admin@university.example";
  private static final String NEW_STUDENT = "page23cse002@university.example";
  private static final String PASSWORD = "a-chosen-password";

  @Value("${local.server.port}")
  private int port;

  @Autowired private UserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    create(STUDENT, Role.STUDENT);
    create(ADMIN, Role.ADMIN);
    create(NEW_STUDENT, Role.STUDENT);
    jdbcTemplate.update(
        "update app.users set must_change_password = false where login_email in (?, ?)",
        STUDENT,
        ADMIN);
  }

  @Test
  void aVisitorCanOpenOnlyTheLoginPageAndItsFiles() throws Exception {
    ApiTestClient visitor = new ApiTestClient(port);

    HttpResponse<String> login = visitor.get("/login.html");
    assertThat(login.statusCode()).isEqualTo(200);
    assertThat(login.body()).contains("Institutional email").contains("Welcome back!");
    assertThat(login.headers().firstValue("Content-Security-Policy").orElse(""))
        .contains("script-src 'self'");
    for (String file :
        new String[] {
          "/css/tokens.css",
          "/css/base.css",
          "/css/auth.css",
          "/js/api.js",
          "/js/login.js",
          "/js/mascot.js"
        }) {
      assertThat(visitor.get(file).statusCode()).as(file).isEqualTo(200);
    }

    for (String page :
        new String[] {
          "/student/dashboard.html", "/admin/dashboard.html", "/change-password.html"
        }) {
      HttpResponse<String> response = visitor.get(page);
      assertThat(response.statusCode()).as(page).isEqualTo(302);
      assertThat(response.headers().firstValue("Location").orElse(""))
          .as(page)
          .endsWith("/login.html");
    }
  }

  @Test
  void theLandingPageIsPublicAndOffersNoSignUp() throws Exception {
    ApiTestClient visitor = new ApiTestClient(port);

    for (String page : new String[] {"/", "/index.html"}) {
      HttpResponse<String> landing = visitor.get(page);
      assertThat(landing.statusCode()).as(page).isEqualTo(200);
      String html = landing.body();
      assertThat(html).contains("Your college life").contains("href=\"/login.html\"");
      assertThat(html.toLowerCase())
          .contains("there is no sign-up")
          .doesNotContain("sign up now", "register", "create an account", "remember me");
    }
    // The icons it uses can be fetched before logging in; they are the same file as everywhere.
    assertThat(visitor.get("/img/icons.svg").statusCode()).isEqualTo(200);
    assertThat(visitor.get("/css/landing.css").statusCode()).isEqualTo(200);
  }

  @Test
  void thePrivacyPolicyAndTheTermsCanBeReadWithoutLoggingIn() throws Exception {
    ApiTestClient visitor = new ApiTestClient(port);

    HttpResponse<String> privacy = visitor.get("/privacy.html");
    HttpResponse<String> terms = visitor.get("/terms.html");

    assertThat(privacy.statusCode()).isEqualTo(200);
    assertThat(privacy.body())
        .contains("<h1>Privacy policy</h1>")
        .contains("must be reviewed and adopted by the college");
    // It names every AI service the router may use (docs/12 section 2b).
    assertThat(privacy.body())
        .contains("Groq")
        .contains("Ollama")
        .contains("Gemini")
        .contains("OpenRouter")
        .contains("to another only if the first one fails");
    // It says what V15 really stores (CC-02) and promises no clean-up that does not exist (CC-15).
    assertThat(privacy.body())
        .contains("Your chats with the Quick Assistant")
        .contains("Only your newest six chats are kept")
        .contains("Only you can read them, not an administrator")
        .contains("once the college switches on the nightly clean-up")
        .doesNotContain(
            "The text you send to the AI assistant and the answer it gives are not stored")
        .doesNotContain("There is no automatic removal yet.");
    assertThat(terms.statusCode()).isEqualTo(200);
    assertThat(terms.body())
        .contains("<h1>Terms of use</h1>")
        .contains("can be wrong or incomplete");
    assertThat(visitor.get("/css/legal.css").statusCode()).isEqualTo(200);
    // They are reading pages only: no script at all, so nothing on them can act for a visitor.
    assertThat(privacy.body() + terms.body()).doesNotContain("<script");
    // The login page links to both.
    assertThat(visitor.get("/login.html").body()).contains("/privacy.html").contains("/terms.html");
    // Being public does not open anything else.
    assertThat(visitor.get("/student/settings.html").statusCode()).isEqualTo(302);
    assertThat(visitor.get("/admin/settings.html").statusCode()).isEqualTo(302);
  }

  @Test
  void theLoginPageOffersNothingTheProjectForbids() throws Exception {
    String html = new ApiTestClient(port).get("/login.html").body().toLowerCase();

    assertThat(html)
        .doesNotContain("sign up", "register", "remember me", "google", "apple", "twitter");
    assertThat(html).contains("ask your college administrator");
  }

  @Test
  void aStudentOpensStudentPagesAndIsSentAwayFromAdminPages() throws Exception {
    ApiTestClient student = new ApiTestClient(port);
    assertThat(student.login(STUDENT, PASSWORD).statusCode()).isEqualTo(200);

    assertThat(student.get("/").statusCode()).isEqualTo(200);
    HttpResponse<String> dashboard = student.get("/student/dashboard.html");
    assertThat(dashboard.statusCode()).isEqualTo(200);
    assertThat(dashboard.body()).contains("Campus Companion").contains("Log out");

    HttpResponse<String> adminPage = student.get("/admin/dashboard.html");
    assertThat(adminPage.statusCode()).isEqualTo(302);
    assertThat(adminPage.headers().firstValue("Location").orElse(""))
        .doesNotContain("admin")
        .endsWith("/");
  }

  @Test
  void anAdminOpensAdminPagesAndIsSentAwayFromStudentPages() throws Exception {
    ApiTestClient admin = new ApiTestClient(port);
    assertThat(admin.login(ADMIN, PASSWORD).statusCode()).isEqualTo(200);

    HttpResponse<String> dashboard = admin.get("/admin/dashboard.html");
    assertThat(dashboard.statusCode()).isEqualTo(200);
    assertThat(dashboard.body()).contains("<span class=\"badge\">Admin</span>");

    HttpResponse<String> studentPage = admin.get("/student/dashboard.html");
    assertThat(studentPage.statusCode()).isEqualTo(302);
    assertThat(studentPage.headers().firstValue("Location").orElse(""))
        .doesNotContain("student")
        .endsWith("/");
  }

  @Test
  void aStudentWithATemporaryPasswordIsSentToTheChangePasswordPage() throws Exception {
    ApiTestClient student = new ApiTestClient(port);
    assertThat(student.login(NEW_STUDENT, PASSWORD).statusCode()).isEqualTo(200);

    for (String page : new String[] {"/", "/student/dashboard.html"}) {
      HttpResponse<String> response = student.get(page);
      assertThat(response.statusCode()).as(page).isEqualTo(302);
      assertThat(response.headers().firstValue("Location").orElse(""))
          .as(page)
          .endsWith("/change-password.html");
    }
    HttpResponse<String> changePage = student.get("/change-password.html");
    assertThat(changePage.statusCode()).isEqualTo(200);
    assertThat(changePage.body()).contains("New password again");
  }

  private void create(String email, Role role) {
    if (!users.existsByLoginEmail(email)) {
      users.save(new User(email, passwordEncoder.encode(PASSWORD), role, "Page Test"));
    }
  }
}
