package com.campuscompanion.student;

import static com.campuscompanion.support.ApiTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.campuscompanion.auth.User;
import com.campuscompanion.auth.UserRepository;
import com.campuscompanion.common.error.ConflictException;
import com.campuscompanion.common.error.ValidationException;
import com.campuscompanion.student.dto.AdminStudentResponse;
import com.campuscompanion.student.dto.StudentCreateRequest;
import com.campuscompanion.support.ApiTestClient;
import com.campuscompanion.support.PostgresIntegrationTest;
import java.lang.reflect.RecordComponent;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Task T1-10: provisioning against real PostgreSQL and over real HTTP. Covers scenarios PV1 (a
 * valid record), PV3 (rejected records leave nothing behind), PV5 (records hang on the student ID,
 * not the email) and S1 (the new student logs in with the email and the roll number). The audit row
 * of PV1 arrives with task T2-02. All data is synthetic.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StudentProvisioningIT extends PostgresIntegrationTest {

  @Value("${local.server.port}")
  private int port;

  @Autowired private StudentProvisioningService provisioning;
  @Autowired private UserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @MockitoSpyBean private StudentProfileRepository profiles;

  @Test
  void createsTheAccountAndTheLinkedProfile() {
    AdminStudentResponse response = provisioning.provision(student("prv23cse001"));

    assertThat(response.id()).isNotNull();
    assertThat(response.rollNo()).isEqualTo("PRV23CSE001");
    assertThat(response.email()).isEqualTo("prv23cse001@university.example");
    assertThat(response.mustChangePassword()).isTrue();
    assertThat(response.lastLoginAt()).isNull();

    User account = users.findByLoginEmail("prv23cse001@university.example").orElseThrow();
    assertThat(account.isMustChangePassword()).isTrue();
    assertThat(account.getDisplayName()).isEqualTo("Demo Student One");
    assertThat(passwordEncoder.matches("PRV23CSE001", account.getPasswordHash())).isTrue();
    StudentProfile profile = profiles.findByUserId(account.getId()).orElseThrow();
    assertThat(profile.getId()).isEqualTo(response.id());
    assertThat(profile.getRollNo()).isEqualTo("PRV23CSE001");
  }

  @Test
  void theResponseHasNoPasswordField() {
    assertThat(Arrays.stream(AdminStudentResponse.class.getRecordComponents()))
        .extracting(RecordComponent::getName)
        .noneMatch(
            name -> name.toLowerCase().contains("password") && !name.equals("mustChangePassword"));
    assertThat(Arrays.stream(StudentCreateRequest.class.getRecordComponents()))
        .extracting(RecordComponent::getName)
        .doesNotContain("email", "password");
  }

  @Test
  void aProvisionedStudentLogsInWithTheEmailAndTheRollNumber() throws Exception {
    provisioning.provision(student("PRV23CSE002"));
    ApiTestClient client = new ApiTestClient(port);

    HttpResponse<String> login = client.login("prv23cse002@university.example", "PRV23CSE002");

    assertThat(login.statusCode()).isEqualTo(200);
    assertThat(json(login).get("mustChangePassword").asBoolean()).isTrue();
    assertThat(login.body()).doesNotContain("PRV23CSE002\"}").doesNotContain("passwordHash");
    HttpResponse<String> blocked = client.get("/api/v1/students/me");
    assertThat(json(blocked).get("code").asString()).isEqualTo("AUTH_PASSWORD_CHANGE_REQUIRED");
    assertThat(new ApiTestClient(port).login("PRV23CSE002", "PRV23CSE002").statusCode())
        .as("a roll number is not a login identifier")
        .isNotEqualTo(200);
  }

  @Test
  void rejectedRecordsLeaveNothingBehind() {
    provisioning.provision(student("PRV23CSE003"));
    long accountsBefore = users.count();
    long profilesBefore = profiles.count();

    assertThatThrownBy(() -> provisioning.provision(student("PRV23CSE003")))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> provisioning.provision(student("prv23cse003")))
        .as("the same roll number in another letter case")
        .isInstanceOf(ConflictException.class);
    for (String rollNo : new String[] {null, "", "PRV 23", "PRV#23", "prv@23"}) {
      assertThatThrownBy(() -> provisioning.provision(student(rollNo)))
          .as("roll number: " + rollNo)
          .isInstanceOf(ValidationException.class);
    }
    assertThatThrownBy(
            () ->
                provisioning.provision(
                    new StudentCreateRequest("PRV23CSE004", null, "CSE", 3, "A", 2023, null)))
        .isInstanceOf(ValidationException.class);

    assertThat(users.count()).isEqualTo(accountsBefore);
    assertThat(profiles.count()).isEqualTo(profilesBefore);
  }

  @Test
  void aFailureAfterTheAccountIsCreatedRollsTheAccountBack() {
    doThrow(new DataAccessResourceFailureException("simulated failure"))
        .when(profiles)
        .save(any(StudentProfile.class));

    assertThatThrownBy(() -> provisioning.provision(student("PRV23CSE005")))
        .isInstanceOf(DataAccessResourceFailureException.class);

    assertThat(users.existsByLoginEmail("prv23cse005@university.example")).isFalse();
  }

  @Test
  void changingTheLoginEmailLeavesTheStudentAndTheRollNumberUntouched() throws Exception {
    AdminStudentResponse before = provisioning.provision(student("PRV23CSE006"));

    // What an approved data migration would do after a change of domain.
    try (Connection connection = adminConnection();
        PreparedStatement update =
            connection.prepareStatement(
                "update app.users set login_email = ? where login_email = ?")) {
      update.setString(1, "prv23cse006@college.example");
      update.setString(2, "prv23cse006@university.example");
      assertThat(update.executeUpdate()).isEqualTo(1);
    }

    User account = users.findByLoginEmail("prv23cse006@college.example").orElseThrow();
    StudentProfile profile = profiles.findByUserId(account.getId()).orElseThrow();
    assertThat(profile.getId()).isEqualTo(before.id());
    assertThat(profile.getRollNo()).isEqualTo("PRV23CSE006");
    assertThat(
            new ApiTestClient(port)
                .login("prv23cse006@college.example", "PRV23CSE006")
                .statusCode())
        .isEqualTo(200);
    assertThat(
            new ApiTestClient(port)
                .login("prv23cse006@university.example", "PRV23CSE006")
                .statusCode())
        .isEqualTo(401);
  }

  private static StudentCreateRequest student(String rollNo) {
    return new StudentCreateRequest(rollNo, "Demo Student One", "CSE", 3, "A", 2023, null);
  }
}
