package com.campuscompanion.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campuscompanion.support.PostgresIntegrationTest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Task T2-01: the academic tables of V2__academic.sql. The runtime role can write a valid row into
 * each of them, and the database itself refuses the bad data listed in docs/08-database-design.md
 * sections 4.5 to 4.10. Scenario Z15: the audit log cannot be changed or emptied by anyone. All
 * data is synthetic.
 */
class AcademicSchemaIT extends PostgresIntegrationTest {

  private static long adminId;
  private static long studentId;
  private static long subjectId;
  private static long assessmentId;

  @BeforeAll
  static void createAStudentASubjectAndAnAssessment() throws SQLException {
    try (Connection app = connectionAs(APP_USER, APP_PASSWORD);
        Statement statement = app.createStatement()) {
      adminId =
          insert(
              statement,
              "insert into app.users (login_email, password_hash, role, display_name)"
                  + " values ('v2.admin@university.example', 'x', 'ADMIN', 'Schema Admin')");
      long accountId =
          insert(
              statement,
              "insert into app.users (login_email, password_hash, role, display_name)"
                  + " values ('v2stu001@university.example', 'x', 'STUDENT', 'Demo Student')");
      studentId =
          insert(
              statement,
              "insert into app.student_profiles (user_id, roll_no, full_name, branch,"
                  + " current_semester, section, admission_year) values ("
                  + accountId
                  + ", 'V2STU001', 'Demo Student', 'CSE', 3, 'A', 2023)");
      subjectId =
          insert(
              statement,
              "insert into app.subjects (code, name, branch, semester, credits)"
                  + " values ('V2SUB1', 'Schema Subject', 'CSE', 3, 4)");
      assessmentId = insert(statement, assessment("Internal 1", "INTERNAL", "50"));
    }
  }

  @Test
  void runtimeRoleCanWriteAValidRowIntoEveryAcademicTable() throws SQLException {
    try (Connection app = connectionAs(APP_USER, APP_PASSWORD);
        Statement statement = app.createStatement()) {
      assertThat(insert(statement, attendance("2026-10-01", 1, "PRESENT"))).isPositive();
      assertThat(insert(statement, marks(assessmentId, "41.50", false))).isPositive();
      assertThat(insert(statement, result(1, "7.80", 22, 22, "PASS"))).isPositive();
      assertThat(insert(statement, timetable(1, "09:00", "10:00"))).isPositive();
      assertThat(insert(statement, notice("GENERAL", "null", "DRAFT"))).isPositive();
      assertThat(insert(statement, audit("ACCOUNT_CREATED"))).isPositive();

      assertThat(statement.executeUpdate("update app.attendance_records set status = 'ABSENT'"))
          .isPositive();
      assertThat(statement.executeUpdate("delete from app.timetable_entries")).isPositive();
      assertThat(statement.executeUpdate("delete from app.notices")).isPositive();
    }
  }

  @Test
  void officialRecordsCannotBeDeletedByTheRuntimeRole() throws SQLException {
    try (Connection app = connectionAs(APP_USER, APP_PASSWORD);
        Statement statement = app.createStatement()) {
      for (String table :
          new String[] {"attendance_records", "assessments", "marks", "semester_results"}) {
        assertThatThrownBy(() -> statement.executeUpdate("delete from app." + table))
            .as(table)
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("permission denied");
      }
    }
  }

  @Test
  void attendanceRejectsADuplicateClassABadStatusAndABadPeriod() throws SQLException {
    try (Connection app = connectionAs(APP_USER, APP_PASSWORD);
        Statement statement = app.createStatement()) {
      insert(statement, attendance("2026-10-02", 2, "PRESENT"));

      assertRejected(
          statement, attendance("2026-10-02", 2, "ABSENT"), "attendance_records_class_key");
      assertRejected(
          statement, attendance("2026-10-03", 1, "LATE"), "attendance_records_status_check");
      assertRejected(
          statement, attendance("2026-10-03", 0, "PRESENT"), "attendance_records_period_no_check");
      assertRejected(
          statement, attendance("2026-10-03", 13, "PRESENT"), "attendance_records_period_no_check");
    }
  }

  @Test
  void assessmentsRejectADuplicateNameABadTypeAndZeroMaximumMarks() throws SQLException {
    try (Connection app = connectionAs(APP_USER, APP_PASSWORD);
        Statement statement = app.createStatement()) {
      assertRejected(
          statement,
          assessment("Internal 1", "INTERNAL", "50"),
          "assessments_subject_id_name_academic_year_key");
      assertRejected(statement, assessment("Quiz", "SURPRISE", "50"), "assessments_type_check");
      assertRejected(statement, assessment("Lab 1", "LAB", "0"), "assessments_max_marks_check");
    }
  }

  @Test
  void marksRejectADuplicateNegativeMarksAndMissingMarksUnlessAbsent() throws SQLException {
    try (Connection app = connectionAs(APP_USER, APP_PASSWORD);
        Statement statement = app.createStatement()) {
      long first = insert(statement, assessment("Assignment 1", "ASSIGNMENT", "20"));
      long second = insert(statement, assessment("Assignment 2", "ASSIGNMENT", "20"));
      long third = insert(statement, assessment("Assignment 3", "ASSIGNMENT", "20"));
      insert(statement, marks(first, "18", false));

      assertRejected(statement, marks(first, "19", false), "marks_student_id_assessment_id_key");
      assertRejected(statement, marks(second, "-1", false), "marks_marks_obtained_check");
      assertRejected(statement, marks(second, "null", false), "marks_absent_or_marked_check");
      assertThat(insert(statement, marks(third, "null", true))).isPositive();
    }
  }

  @Test
  void semesterResultsRejectValuesOutsideTheirLimits() throws SQLException {
    try (Connection app = connectionAs(APP_USER, APP_PASSWORD);
        Statement statement = app.createStatement()) {
      insert(statement, result(2, "6.50", 24, 20, "FAIL"));

      assertRejected(
          statement, result(2, "7", 24, 24, "PASS"), "semester_results_student_id_semester_key");
      assertRejected(statement, result(9, "7", 24, 24, "PASS"), "semester_results_semester_check");
      assertRejected(statement, result(3, "10.01", 24, 24, "PASS"), "semester_results_sgpa_check");
      assertRejected(statement, result(3, "-0.01", 24, 24, "PASS"), "semester_results_sgpa_check");
      assertRejected(
          statement, result(3, "7", 0, 0, "PASS"), "semester_results_credits_registered_check");
      assertRejected(
          statement,
          result(3, "7", 20, 21, "PASS"),
          "semester_results_credits_earned_within_registered_check");
      assertRejected(statement, result(3, "7", 24, 24, "PENDING"), "semester_results_status_check");
    }
  }

  @Test
  void timetableRejectsABadDayAndAClassThatEndsBeforeItStarts() throws SQLException {
    try (Connection app = connectionAs(APP_USER, APP_PASSWORD);
        Statement statement = app.createStatement()) {
      assertRejected(
          statement, timetable(8, "09:00", "10:00"), "timetable_entries_day_of_week_check");
      assertRejected(
          statement, timetable(1, "10:00", "10:00"), "timetable_entries_end_after_start_check");
      assertRejected(
          statement, timetable(1, "10:00", "09:00"), "timetable_entries_end_after_start_check");
    }
  }

  @Test
  void noticesRejectABadCategoryStatusAndTargetSemester() throws SQLException {
    try (Connection app = connectionAs(APP_USER, APP_PASSWORD);
        Statement statement = app.createStatement()) {
      assertRejected(statement, notice("PARTY", "null", "DRAFT"), "notices_category_check");
      assertRejected(statement, notice("EXAM", "null", "ARCHIVED"), "notices_status_check");
      assertRejected(statement, notice("EXAM", "9", "DRAFT"), "notices_target_semester_check");
    }
  }

  @Test
  void everyTableRejectsARowThatPointsAtNothing() throws SQLException {
    try (Connection app = connectionAs(APP_USER, APP_PASSWORD);
        Statement statement = app.createStatement()) {
      assertRejected(
          statement,
          "insert into app.marks (student_id, assessment_id, marks_obtained, entered_by)"
              + " values (-1, "
              + assessmentId
              + ", 10, "
              + adminId
              + ")",
          "marks_student_id_fkey");
      assertRejected(
          statement,
          "insert into app.audit_logs (actor_user_id, action, entity_type, entity_id)"
              + " values (-1, 'ACCOUNT_CREATED', 'USER', '1')",
          "audit_logs_actor_user_id_fkey");
    }
  }

  @Test
  void theAuditLogCannotBeChangedOrEmptiedByTheRuntimeRole() throws SQLException {
    try (Connection app = connectionAs(APP_USER, APP_PASSWORD);
        Statement statement = app.createStatement()) {
      insert(statement, audit("PASSWORD_CHANGED"));
      long before = count(statement);

      for (String sql :
          new String[] {
            "update app.audit_logs set action = 'TAMPERED'",
            "delete from app.audit_logs",
            "truncate app.audit_logs"
          }) {
        assertThatThrownBy(() -> statement.executeUpdate(sql))
            .as(sql)
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("permission denied");
      }
      assertThat(count(statement)).isEqualTo(before);
    }
  }

  @Test
  void theAuditLogCannotBeChangedOrEmptiedEvenByItsOwner() throws SQLException {
    try (Connection app = connectionAs(APP_USER, APP_PASSWORD);
        Statement statement = app.createStatement()) {
      insert(statement, audit("PASSWORD_RESET"));
    }
    try (Connection owner = connectionAs(MIGRATOR_USER, MIGRATOR_PASSWORD);
        Statement statement = owner.createStatement()) {
      long before = count(statement);

      for (String sql :
          new String[] {
            "update app.audit_logs set action = 'TAMPERED'",
            "delete from app.audit_logs",
            "truncate app.audit_logs"
          }) {
        assertThatThrownBy(() -> statement.executeUpdate(sql))
            .as(sql)
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("audit_logs is append-only");
      }
      assertThat(count(statement)).isEqualTo(before);
      try (ResultSet tampered =
          statement.executeQuery("select count(*) from app.audit_logs where action = 'TAMPERED'")) {
        tampered.next();
        assertThat(tampered.getLong(1)).isZero();
      }
    }
  }

  private static String attendance(String date, int period, String status) {
    return "insert into app.attendance_records (student_id, subject_id, class_date, period_no,"
        + " status, marked_by) values ("
        + studentId
        + ", "
        + subjectId
        + ", '"
        + date
        + "', "
        + period
        + ", '"
        + status
        + "', "
        + adminId
        + ")";
  }

  private static String assessment(String name, String type, String maxMarks) {
    return "insert into app.assessments (subject_id, name, assessment_type, max_marks,"
        + " academic_year) values ("
        + subjectId
        + ", '"
        + name
        + "', '"
        + type
        + "', "
        + maxMarks
        + ", '2026-27')";
  }

  private static String marks(long assessment, String obtained, boolean absent) {
    return "insert into app.marks (student_id, assessment_id, marks_obtained, absent, entered_by)"
        + " values ("
        + studentId
        + ", "
        + assessment
        + ", "
        + obtained
        + ", "
        + absent
        + ", "
        + adminId
        + ")";
  }

  private static String result(
      int semester, String sgpa, int registered, int earned, String status) {
    return "insert into app.semester_results (student_id, semester, sgpa, credits_registered,"
        + " credits_earned, result_status) values ("
        + studentId
        + ", "
        + semester
        + ", "
        + sgpa
        + ", "
        + registered
        + ", "
        + earned
        + ", '"
        + status
        + "')";
  }

  private static String timetable(int day, String start, String end) {
    return "insert into app.timetable_entries (branch, semester, section, day_of_week,"
        + " start_time, end_time, subject_id) values ('CSE', 3, 'A', "
        + day
        + ", '"
        + start
        + "', '"
        + end
        + "', "
        + subjectId
        + ")";
  }

  private static String notice(String category, String targetSemester, String status) {
    return "insert into app.notices (title, body, category, target_semester, status, created_by)"
        + " values ('Schema notice', 'Synthetic text.', '"
        + category
        + "', "
        + targetSemester
        + ", '"
        + status
        + "', "
        + adminId
        + ")";
  }

  private static String audit(String action) {
    return "insert into app.audit_logs (actor_user_id, action, entity_type, entity_id)"
        + " values ("
        + adminId
        + ", '"
        + action
        + "', 'USER', '"
        + adminId
        + "')";
  }

  /** Runs an insert and returns the generated id, which also proves the identity column works. */
  private static long insert(Statement statement, String sql) throws SQLException {
    statement.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
    try (ResultSet keys = statement.getGeneratedKeys()) {
      keys.next();
      return keys.getLong("id");
    }
  }

  private static void assertRejected(Statement statement, String sql, String constraint) {
    assertThatThrownBy(() -> statement.executeUpdate(sql))
        .as(constraint)
        .isInstanceOf(SQLException.class)
        .hasMessageContaining(constraint);
  }

  private static long count(Statement statement) throws SQLException {
    try (ResultSet rows = statement.executeQuery("select count(*) from app.audit_logs")) {
      rows.next();
      return rows.getLong(1);
    }
  }
}
