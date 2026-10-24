package com.campuscompanion.ai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.campuscompanion.audit.AuditService;
import com.campuscompanion.common.config.AiLogRetentionProperties;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

/** T7-09: switched off, the nightly job never touches the usage log. */
class AiUsageLogRetentionTest {

  @Test
  void whenSwitchedOffTheNightlyRunDeletesNothing() {
    AiRequestLogRepository logs = mock(AiRequestLogRepository.class);
    AiUsageLogRetention retention =
        new AiUsageLogRetention(
            logs,
            new AiLogRetentionProperties(false, 90, 1000, 50),
            mock(AuditService.class),
            mock(TransactionTemplate.class),
            Clock.systemUTC());

    retention.nightly();

    verify(logs, never()).deleteOlderThan(any(), anyInt());
  }
}
