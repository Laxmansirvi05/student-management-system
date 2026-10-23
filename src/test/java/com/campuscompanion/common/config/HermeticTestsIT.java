package com.campuscompanion.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.ai.provider.AiProvider;
import com.campuscompanion.ai.provider.MockAiProvider;
import com.campuscompanion.common.storage.StorageClient;
import com.campuscompanion.support.ApiIntegrationTest;
import com.campuscompanion.support.InMemoryStorageClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

/**
 * The integration tests are sealed off from the outside world, whatever a developer's .env says
 * (CC-11): the AI is the mock, file storage is in memory, and no key or administrator from .env
 * reaches the application under test.
 */
class HermeticTestsIT extends ApiIntegrationTest {

  @Autowired private AiProvider aiProvider;
  @Autowired private StorageClient storageClient;
  @Autowired private Environment environment;

  @Test
  void theAiIsTheMockAndStorageIsInMemory() {
    assertThat(aiProvider).isInstanceOf(MockAiProvider.class);
    assertThat(storageClient).isInstanceOf(InMemoryStorageClient.class);
  }

  @Test
  void noKeyAddressOrAdministratorFromDotEnvIsUsed() {
    for (String setting :
        new String[] {
          "app.ai.provider",
          "app.ai.api-key",
          "app.storage.supabase-url",
          "app.storage.service-key",
          "app.bootstrap.admin-email",
          "app.bootstrap.admin-password"
        }) {
      String value = environment.getProperty(setting);
      if (setting.equals("app.ai.provider")) {
        assertThat(value).as(setting).isEqualTo("mock");
      } else {
        assertThat(value).as(setting).isEmpty();
      }
    }
    assertThat(environment.getProperty("app.seed.demo-enabled")).isEqualTo("false");
  }
}
