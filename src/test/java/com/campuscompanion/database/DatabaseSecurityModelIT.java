package com.campuscompanion.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campuscompanion.support.PostgresIntegrationTest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Tasks T1-03, T2-01, T3-02, T4-01 and T5-01: the database matches the security model in
 * docs/08-database-design.md section 11, and the database itself enforces email and roll-number
 * uniqueness (scenario PV4).
 */
class DatabaseSecurityModelIT extends PostgresIntegrationTest {

  /** The exact privileges of cc_app, copied from docs/08-database-design.md section 11.3. */
  private static final Map<String, Set<String>> EXPECTED_PRIVILEGES =
      Map.ofEntries(
          Map.entry("users", Set.of("SELECT", "INSERT", "UPDATE")),
          Map.entry("student_profiles", Set.of("SELECT", "INSERT", "UPDATE")),
          Map.entry("subjects", Set.of("SELECT", "INSERT", "UPDATE")),
          Map.entry("subject_units", Set.of("SELECT", "INSERT", "UPDATE", "DELETE")),
          Map.entry("attendance_records", Set.of("SELECT", "INSERT", "UPDATE")),
          Map.entry("attendance_reports", Set.of("SELECT", "INSERT", "UPDATE")),
          Map.entry("assessments", Set.of("SELECT", "INSERT", "UPDATE")),
          Map.entry("marks", Set.of("SELECT", "INSERT", "UPDATE")),
          Map.entry("semester_results", Set.of("SELECT", "INSERT", "UPDATE")),
          Map.entry("subject_results", Set.of("SELECT", "INSERT", "UPDATE")),
          Map.entry("timetable_entries", Set.of("SELECT", "INSERT", "UPDATE", "DELETE")),
          Map.entry("notices", Set.of("SELECT", "INSERT", "UPDATE", "DELETE")),
          Map.entry("learning_resources", Set.of("SELECT", "INSERT", "UPDATE", "DELETE")),
          Map.entry("question_papers", Set.of("SELECT", "INSERT", "UPDATE", "DELETE")),
          Map.entry("bookmarks", Set.of("SELECT", "INSERT", "DELETE")),
          Map.entry("pyq_papers", Set.of("SELECT", "INSERT", "UPDATE", "DELETE")),
          Map.entry("pyq_bookmarks", Set.of("SELECT", "INSERT", "DELETE")),
          Map.entry("quiz_questions", Set.of("SELECT", "INSERT", "UPDATE")),
          Map.entry("quiz_attempts", Set.of("SELECT", "INSERT", "UPDATE")),
          Map.entry("quiz_attempt_answers", Set.of("SELECT", "INSERT", "UPDATE")),
          Map.entry("audit_logs", Set.of("SELECT", "INSERT")),
          Map.entry("ai_request_logs", Set.of("SELECT", "INSERT")));

  @Test
  void flywayRanAsTheMigrationRole() throws SQLException {
    List<String> installedBy = query("select distinct installed_by from app.flyway_schema_history");

    assertThat(installedBy).containsExactly(MIGRATOR_USER);
  }

  @Test
  void runtimeRoleOwnsNothingAndCannotBypassRowLevelSecurity() throws SQLException {
    List<String> owned = query("select tablename from pg_tables where tableowner = 'cc_app'");
    List<String> flags =
        query(
            "select rolsuper::text || ',' || rolbypassrls::text from pg_roles where rolname = 'cc_app'");

    assertThat(owned).isEmpty();
    assertThat(flags).containsExactly("false,false");
  }

  @Test
  void everyApplicationTableIsOwnedByTheMigrationRoleAndHasRowLevelSecurity() throws SQLException {
    List<String> tables =
        query(
            "select tablename || ',' || tableowner || ',' || rowsecurity::text from pg_tables"
                + " where schemaname = 'app' and tablename <> 'flyway_schema_history'"
                + " order by tablename");

    assertThat(tables)
        .containsExactly(
            "ai_request_logs,cc_migrator,true",
            "assessments,cc_migrator,true",
            "attendance_records,cc_migrator,true",
            "attendance_reports,cc_migrator,true",
            "audit_logs,cc_migrator,true",
            "bookmarks,cc_migrator,true",
            "learning_resources,cc_migrator,true",
            "marks,cc_migrator,true",
            "notices,cc_migrator,true",
            "pyq_bookmarks,cc_migrator,true",
            "pyq_papers,cc_migrator,true",
            "question_papers,cc_migrator,true",
            "quiz_attempt_answers,cc_migrator,true",
            "quiz_attempts,cc_migrator,true",
            "quiz_questions,cc_migrator,true",
            "semester_results,cc_migrator,true",
            "student_profiles,cc_migrator,true",
            "subject_results,cc_migrator,true",
            "subject_units,cc_migrator,true",
            "subjects,cc_migrator,true",
            "timetable_entries,cc_migrator,true",
            "users,cc_migrator,true");
  }

  @Test
  void runtimeRoleHasExactlyTheDocumentedPrivileges() throws SQLException {
    Map<String, Set<String>> actual = new TreeMap<>();
    for (String row :
        query(
            "select table_name || ',' || privilege_type from information_schema.role_table_grants"
                + " where grantee = 'cc_app' and table_schema = 'app'")) {
      String[] parts = row.split(",");
      actual.computeIfAbsent(parts[0], key -> new TreeSet<>()).add(parts[1]);
    }

    assertThat(actual).isEqualTo(new TreeMap<>(EXPECTED_PRIVILEGES));
  }

  @Test
  void policiesExistForTheRuntimeRoleOnlyAndMatchItsPrivileges() throws SQLException {
    Map<String, Set<String>> policies = new TreeMap<>();
    for (String row :
        query(
            "select tablename || ',' || cmd || ',' || array_to_string(roles, '+') from pg_policies"
                + " where schemaname = 'app'")) {
      String[] parts = row.split(",");
      assertThat(parts[2]).as("policy role on %s", parts[0]).isEqualTo("cc_app");
      policies.computeIfAbsent(parts[0], key -> new TreeSet<>()).add(parts[1]);
    }

    assertThat(policies).isEqualTo(new TreeMap<>(EXPECTED_PRIVILEGES));
  }

  @Test
  void runtimeRoleCanInsertAndReadButCannotChangeTheSchemaOrDeleteAccounts() throws SQLException {
    try (Connection app = connectionAs(APP_USER, APP_PASSWORD);
        Statement statement = app.createStatement()) {
      statement.executeUpdate(
          "insert into app.users (login_email, password_hash, role, display_name)"
              + " values ('privilege.check@university.example', 'x', 'ADMIN', 'Privilege Check')");

      assertThatThrownBy(
              () -> statement.executeUpdate("create table app.should_not_exist (id int)"))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("permission denied");
      assertThatThrownBy(() -> statement.executeUpdate("delete from app.users"))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("permission denied");
      assertThatThrownBy(() -> statement.executeQuery("select * from app.flyway_schema_history"))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("permission denied");
    }
  }

  @Test
  void databaseRejectsDuplicateAndNonNormalisedEmails() throws SQLException {
    try (Connection admin = adminConnection();
        Statement statement = admin.createStatement()) {
      statement.executeUpdate(insertUser("pv4.first@university.example"));

      assertThatThrownBy(() -> statement.executeUpdate(insertUser("pv4.first@university.example")))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("users_login_email_key");
      assertThatThrownBy(() -> statement.executeUpdate(insertUser("PV4.FIRST@university.example")))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("users_login_email_normalised");
      assertThatThrownBy(
              () -> statement.executeUpdate(insertUser(" pv4.second@university.example ")))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("violates check constraint \"users_login_email_");
      assertThatThrownBy(() -> statement.executeUpdate(insertUser("not-an-email")))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("users_login_email_format");
    }
  }

  @Test
  void databaseRejectsDuplicateAndNonNormalisedRollNumbers() throws SQLException {
    try (Connection admin = adminConnection();
        Statement statement = admin.createStatement()) {
      statement.executeUpdate(insertUser("pv4.roll.a@university.example"));
      statement.executeUpdate(insertUser("pv4.roll.b@university.example"));
      statement.executeUpdate(insertProfile("pv4.roll.a@university.example", "PV4ROLL001"));

      assertThatThrownBy(
              () ->
                  statement.executeUpdate(
                      insertProfile("pv4.roll.b@university.example", "PV4ROLL001")))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("student_profiles_roll_no_key");
      assertThatThrownBy(
              () ->
                  statement.executeUpdate(
                      insertProfile("pv4.roll.b@university.example", "pv4roll002")))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("violates check constraint \"student_profiles_roll_no_");
      assertThatThrownBy(
              () ->
                  statement.executeUpdate(
                      insertProfile("pv4.roll.b@university.example", "PV4 ROLL")))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("student_profiles_roll_no_format");
    }
  }

  private static String insertUser(String email) {
    return "insert into app.users (login_email, password_hash, role, display_name) values ('"
        + email
        + "', 'x', 'STUDENT', 'Synthetic Student')";
  }

  private static String insertProfile(String email, String rollNo) {
    return "insert into app.student_profiles"
        + " (user_id, roll_no, full_name, branch, current_semester, section, admission_year)"
        + " select id, '"
        + rollNo
        + "', 'Synthetic Student', 'CSE', 3, 'A', 2023 from app.users where login_email = '"
        + email
        + "'";
  }

  /** Runs a one-column query as the container administrator and returns the values. */
  private static List<String> query(String sql) throws SQLException {
    List<String> values = new ArrayList<>();
    try (Connection admin = adminConnection();
        Statement statement = admin.createStatement();
        ResultSet rows = statement.executeQuery(sql)) {
      while (rows.next()) {
        values.add(rows.getString(1));
      }
    }
    return values;
  }
}
