package com.campuscompanion.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.support.ApiIntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * T7-09: the AI usage log loses its rows older than the retention period, in bounded batches, and
 * each run that removed rows leaves one audit entry with the count and the cutoff only.
 */
@TestPropertySource(
    properties = {"app.ai.log-retention.batch-size=2", "app.ai.log-retention.max-batches=2"})
class AiUsageLogRetentionIT extends ApiIntegrationTest {

  @Autowired private AiUsageLogRetention retention;

  private void row(long userId, int daysAgo) {
    jdbc.update(
        "insert into app.ai_request_logs (user_id, feature, prompt_version, input_chars, status,"
            + " created_at) values (?, 'EXPLAIN', 'explain-v1', 10, 'SUCCESS', ?)",
        userId,
        Timestamp.from(Instant.now().minus(daysAgo, ChronoUnit.DAYS)));
  }

  private int rowsOf(long userId) {
    return jdbc.queryForObject(
        "select count(*) from app.ai_request_logs where user_id = ?", Integer.class, userId);
  }

  private List<String> purgeAudits() {
    return jdbc.queryForList(
        "select new_value::text from app.audit_logs where action = 'AI_USAGE_LOGS_PURGED'"
            + " order by id",
        String.class);
  }

  @Test
  void rowsPastTheRetentionPeriodGoInBoundedRunsAndEachRunIsAudited() {
    long userId = accounts.student("23RET001", "RET", 3, "A");
    long account =
        jdbc.queryForObject(
            "select user_id from app.student_profiles where id = ?", Long.class, userId);
    for (int i = 0; i < 5; i++) {
      row(account, 91 + i);
    }
    row(account, 89);
    row(account, 1);
    int auditsBefore = purgeAudits().size();

    // Batches of 2, at most 2 per run: 4 of the 5 old rows go in the first run.
    assertThat(retention.purge()).isEqualTo(4);
    assertThat(rowsOf(account)).isEqualTo(3);
    assertThat(retention.purge()).isEqualTo(1);
    assertThat(rowsOf(account)).isEqualTo(2);
    // Nothing older is left: the recent rows stay and no audit entry is written.
    assertThat(retention.purge()).isZero();
    assertThat(rowsOf(account)).isEqualTo(2);

    List<String> audits = purgeAudits();
    assertThat(audits).hasSize(auditsBefore + 2);
    assertThat(audits.get(audits.size() - 2)).contains("\"rowsRemoved\": 4").contains("olderThan");
    assertThat(audits.get(audits.size() - 1)).contains("\"rowsRemoved\": 1");
  }
}
