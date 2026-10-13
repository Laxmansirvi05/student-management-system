package com.campuscompanion.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Task T1-02: every variable named in .env.example must be read by application.yml, so the two
 * files cannot drift apart (docs/04-TRD.md TR-JAVA-8).
 */
class ConfigurationBindingTest {

  /** Read by Spring Boot itself, not through a placeholder in application.yml. */
  private static final List<String> READ_BY_SPRING = List.of("SPRING_PROFILES_ACTIVE");

  @Test
  void everyVariableInEnvExampleIsBoundInApplicationYml() throws IOException {
    String envExample = Files.readString(Path.of(".env.example"));
    String applicationYml = Files.readString(Path.of("src/main/resources/application.yml"));

    Matcher names = Pattern.compile("(?m)^([A-Z][A-Z0-9_]*)=").matcher(envExample);
    int checked = 0;
    while (names.find()) {
      String name = names.group(1);
      if (READ_BY_SPRING.contains(name)) {
        continue;
      }
      assertThat(applicationYml).as("application.yml must read %s", name).contains("${" + name);
      checked++;
    }
    assertThat(checked).as("variables found in .env.example").isGreaterThan(10);
  }
}
