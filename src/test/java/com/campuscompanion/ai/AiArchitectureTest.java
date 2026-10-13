package com.campuscompanion.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The AI code cannot reach anyone's records (docs/14-AI-security-and-privacy.md section 5): no
 * class of the ai package uses the packages that hold students, accounts, attendance, marks or
 * results. The test reads the source files, so a new import breaks the build at once.
 */
class AiArchitectureTest {

  private static final Path AI_SOURCES = Path.of("src/main/java/com/campuscompanion/ai");
  private static final List<String> FORBIDDEN =
      List.of(
          "com.campuscompanion.student",
          "com.campuscompanion.auth",
          "com.campuscompanion.attendance",
          "com.campuscompanion.assessment",
          "com.campuscompanion.audit",
          "com.campuscompanion.notice",
          "com.campuscompanion.timetable",
          "com.campuscompanion.progress",
          "com.campuscompanion.resource");

  @Test
  void theAiPackageDoesNotUseThePackagesThatHoldRecords() throws IOException {
    try (Stream<Path> files = Files.walk(AI_SOURCES)) {
      List<Path> sources = files.filter(file -> file.toString().endsWith(".java")).toList();
      assertThat(sources).isNotEmpty();
      for (Path source : sources) {
        String code = Files.readString(source);
        for (String forbidden : FORBIDDEN) {
          assertThat(code)
              .as("%s must not use %s", source.getFileName(), forbidden)
              .doesNotContain(forbidden);
        }
      }
    }
  }

  @Test
  void theUsageLogHasNoFieldThatCouldHoldText() throws IOException {
    String entity = Files.readString(AI_SOURCES.resolve("AiRequestLog.java"));

    // Its only text is the prompt version, such as "explain-v1".
    assertThat(entity.split("private String ", -1)).hasSize(2);
    assertThat(entity).contains("private String promptVersion;");
  }
}
