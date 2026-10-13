package com.campuscompanion.academics;

import static com.campuscompanion.support.ApiTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.campuscompanion.audit.AuditLog;
import com.campuscompanion.audit.AuditLogRepository;
import com.campuscompanion.support.ApiIntegrationTest;
import com.campuscompanion.support.ApiTestClient;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Task T2-02, scenario Z19: a change and its audit entry are saved together or not at all. When the
 * audit entry cannot be written, the marks change is rolled back as well.
 */
class AuditRollbackIT extends ApiIntegrationTest {

  @MockitoSpyBean private AuditLogRepository auditLogs;

  @Test
  void aMarksChangeIsRolledBackWhenItsAuditEntryCannotBeWritten() throws Exception {
    ApiTestClient admin = adminClient("rollback.admin@university.example");
    long studentId = accounts.student("RBK23001", "RBK", 3, "A");
    long subjectId = subject(admin, "RBK101", "RBK", 3);
    HttpResponse<String> created =
        admin.post(
            "/api/v1/admin/assessments",
            "{\"subjectId\":%d,\"name\":\"Internal 1\",\"type\":\"INTERNAL\",\"maxMarks\":50,\"heldOn\":null,\"academicYear\":\"2026-27\",\"published\":true}"
                .formatted(subjectId));
    long assessmentId = json(created).get("id").asLong();
    String path = "/api/v1/admin/assessments/" + assessmentId + "/marks";
    assertThat(admin.put(path, marks(studentId, 30)).statusCode()).isEqualTo(200);

    doThrow(new DataAccessResourceFailureException("simulated failure"))
        .when(auditLogs)
        .save(any(AuditLog.class));
    HttpResponse<String> failed = admin.put(path, marks(studentId, 45));

    assertProblem(failed, 500, "INTERNAL_ERROR");
    assertThat(
            jdbc.queryForObject(
                "select marks_obtained from app.marks where assessment_id = ?",
                BigDecimal.class,
                assessmentId))
        .as("the marks are what they were before the failed save")
        .isEqualByComparingTo("30");
  }

  private static String marks(long studentId, int marksObtained) {
    return "{\"entries\":[{\"studentId\":%d,\"marksObtained\":%d,\"absent\":false}]}"
        .formatted(studentId, marksObtained);
  }
}
