package com.campuscompanion.student;

import static com.campuscompanion.support.ApiTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.auth.Role;
import com.campuscompanion.auth.User;
import com.campuscompanion.auth.UserRepository;
import com.campuscompanion.common.csv.CsvReader;
import com.campuscompanion.support.ApiIntegrationTest;
import com.campuscompanion.support.ApiTestClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Admin CSV import of students (FR-ADM-010): every row is checked first, and either all rows are
 * created or none. All data is synthetic.
 */
class StudentImportIT extends ApiIntegrationTest {

  private static final String IMPORT = "/api/v1/admin/imports/students";
  private static final String HEADER =
      "rollNo,fullName,branch,currentSemester,section,admissionYear,phone\n";

  @Autowired private UserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;

  private ApiTestClient admin() throws Exception {
    return adminClient("import-admin@university.example");
  }

  private static HttpResponse<String> send(ApiTestClient client, String csv) throws Exception {
    return client.postFile(
        IMPORT, "students.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
  }

  private int studentsWithRollNoLike(String pattern) {
    return jdbc.queryForObject(
        "select count(*) from app.student_profiles where roll_no like ?", Integer.class, pattern);
  }

  private static List<String> messages(HttpResponse<String> response) {
    return json(response)
        .get("errors")
        .valueStream()
        .map(e -> e.get("message").asString())
        .toList();
  }

  @Test
  void createsEveryStudentInTheFile() throws Exception {
    // A spreadsheet export: byte order mark, Windows line endings, columns in another order and
    // case, empty columns after the last one, a quoted name with a comma, and a short last line.
    String csv =
        CsvReader.BYTE_ORDER_MARK
            + "FullName,ROLLNO,branch,section,currentSemester,admissionYear,phone,,\r\n"
            + "\"Demo, Asha\",23imp001,IMP,A,3,2023,+91 90000 00001,,\r\n"
            + "Demo Ravi,23IMP002,IMP,A,3,2023,,,\r\n"
            + "Demo Meena,23IMP003,IMP,B,3,2023\r\n";

    HttpResponse<String> response = send(admin(), csv);

    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    assertThat(json(response).get("created").asInt()).isEqualTo(3);
    assertThat(studentsWithRollNoLike("23IMP00%")).isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                "select full_name from app.student_profiles where roll_no = '23IMP001'",
                String.class))
        .isEqualTo("Demo, Asha");
    // The same rules as a student added by hand: email from the roll number, first password the
    // roll number, password change required, one audit row each.
    User account = users.findByLoginEmail("23imp002@university.example").orElseThrow();
    assertThat(account.isMustChangePassword()).isTrue();
    assertThat(passwordEncoder.matches("23IMP002", account.getPasswordHash())).isTrue();
    long audited =
        jdbc.queryForObject(
            "select count(*) from app.audit_logs a join app.student_profiles s"
                + " on s.user_id::text = a.entity_id and a.entity_type = 'USER'"
                + " where a.action = 'ACCOUNT_CREATED' and s.roll_no like '23IMP00%'",
            Long.class);
    assertThat(audited).isEqualTo(3);
  }

  @Test
  void listsEveryProblemByLineAndCreatesNothing() throws Exception {
    accounts.student("23IMP100", "IMP", 3, "A");
    String csv =
        HEADER
            + "23IMP101,Demo Fine,IMP,3,A,2023,\n" // line 2: valid, but must not be created
            + "23IMP102,,IMP,3,A,2023,\n" // line 3: no name
            + "23IMP103,Demo Three,IMP,three,A,2023,\n" // line 4: not a number
            + "23IMP104,Demo Four,IMP,9,A,1990,\n" // line 5: semester and year out of range
            + "23 IMP105,Demo Five,IMP,3,A,2023,\n" // line 6: space in the roll number
            + "23imp101,Demo Again,IMP,3,A,2023,\n" // line 7: same roll number as line 2
            + "23IMP100,Demo Exists,IMP,3,A,2023,\n" // line 8: already a student
            + "23IMP108,Demo Eight,IMP,3,A,2023,,extra\n"; // line 9: one value too many

    HttpResponse<String> response = send(admin(), csv);

    assertThat(invalidFields(response))
        .containsExactly(
            "rows[3].fullName",
            "rows[4].currentSemester",
            "rows[5].currentSemester",
            "rows[5].admissionYear",
            "rows[6].rollNo",
            "rows[7].rollNo",
            "rows[8].rollNo",
            "rows[9].line");
    assertThat(messages(response))
        .contains(
            "\"three\" is not a whole number.",
            "Roll number 23IMP101 is also on line 2.",
            "A student with roll number 23IMP100 already exists.");
    assertThat(studentsWithRollNoLike("23IMP10%")).isEqualTo(1);
  }

  @Test
  void aFailureWhileCreatingUndoesTheRowsBeforeIt() throws Exception {
    // The roll number is free, but its email belongs to another account. Only creating finds it.
    users.save(
        new User(
            "23imp202@university.example",
            passwordEncoder.encode("not-used-here"),
            Role.ADMIN,
            "Demo Clash"));
    String csv = HEADER + "23IMP201,Demo One,IMP,3,A,2023,\n23IMP202,Demo Two,IMP,3,A,2023,\n";

    HttpResponse<String> response = send(admin(), csv);

    assertProblem(response, 409, "CONFLICT");
    assertThat(studentsWithRollNoLike("23IMP20%")).isZero();
    assertThat(users.existsByLoginEmail("23imp201@university.example")).isFalse();
  }

  @Test
  void checksTheHeaderLine() throws Exception {
    HttpResponse<String> response =
        send(admin(), "rollNo,name,branch,currentSemester,section,section\n23IMP301,a,b,3,A,A\n");

    assertThat(invalidFields(response)).containsOnly("header");
    assertThat(messages(response))
        .containsExactlyInAnyOrder(
            "Unknown column \"name\". The columns are rollNo, fullName, branch,"
                + " currentSemester, section, admissionYear and, if you like, phone.",
            "The column section appears twice.",
            "The column fullName is missing.",
            "The column admissionYear is missing.");
  }

  @Test
  void refusesFilesItCannotRead() throws Exception {
    ApiTestClient admin = admin();

    assertThat(invalidFields(send(admin, ""))).containsExactly("file");
    assertThat(invalidFields(send(admin, HEADER))).containsExactly("file");
    assertThat(invalidFields(send(admin, HEADER + "23IMP401,\"Demo,IMP,3,A,2023,\n")))
        .containsExactly("file");
    byte[] latin1 =
        (HEADER + "23IMP402,Demo José,IMP,3,A,2023,\n").getBytes(StandardCharsets.ISO_8859_1);
    assertThat(invalidFields(admin.postFile(IMPORT, "students.csv", "text/csv", latin1)))
        .containsExactly("file");
    assertProblem(
        admin.postFile(
            IMPORT, "students.xlsx", "text/csv", HEADER.getBytes(StandardCharsets.UTF_8)),
        415,
        "UPLOAD_INVALID_TYPE");
    assertProblem(
        admin.postFile(IMPORT, "students.csv", "text/csv", new byte[1024 * 1024 + 1]),
        413,
        "UPLOAD_TOO_LARGE");
    assertThat(studentsWithRollNoLike("23IMP40%")).isZero();
  }

  @Test
  void refusesMoreRowsThanOneRequestCanCreateInTime() throws Exception {
    StringBuilder csv = new StringBuilder(HEADER);
    for (int i = 0; i <= StudentImportService.MAX_ROWS; i++) {
      csv.append("24IMP%03d,Demo,IMP,1,A,2024,\n".formatted(i));
    }

    HttpResponse<String> response = send(admin(), csv.toString());

    assertThat(invalidFields(response)).containsExactly("file");
    assertThat(studentsWithRollNoLike("24IMP%")).isZero();
  }

  @Test
  void onlyAnAdminMayImport() throws Exception {
    accounts.student("23IMP500", "IMP", 3, "A");
    String csv = HEADER + "23IMP501,Demo,IMP,3,A,2023,\n";

    assertProblem(send(studentClient("23IMP500"), csv), 403, "ACCESS_DENIED");
    assertProblem(send(visitor(), csv), 401, "AUTH_REQUIRED");
    assertThat(studentsWithRollNoLike("23IMP501")).isZero();
  }
}
