package com.campuscompanion.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Task T2-02, scenario Z17: nothing that can be written into an audit entry has a place for a
 * password or a hash, and a password event can never carry values.
 */
class AuditSnapshotTest {

  @Test
  void noSnapshotTypeHasAFieldForAPasswordOrAHash() {
    Class<?>[] types = AuditSnapshot.class.getPermittedSubclasses();

    assertThat(types).isNotEmpty();
    for (Class<?> type : types) {
      assertThat(type.isRecord()).as(type.getSimpleName() + " is a record").isTrue();
      assertThat(Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName))
          .as("fields of " + type.getSimpleName())
          .noneMatch(
              name -> {
                String lower = name.toLowerCase(Locale.ROOT);
                return lower.contains("password")
                    || lower.contains("hash")
                    || lower.contains("secret");
              });
    }
  }

  @Test
  void aPasswordEventCanNeverCarryValues() {
    AuditService audit = new AuditService(mock(AuditLogRepository.class), new ObjectMapper());

    assertThatThrownBy(
            () ->
                audit.record(
                    AuditAction.PASSWORD_RESET,
                    "USER",
                    1L,
                    null,
                    new AuditSnapshot.AccountStatus(true)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> audit.recordPasswordEvent(AuditAction.MARKS_UPDATED, 1L))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
