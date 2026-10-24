package com.campuscompanion.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Every bundled course question bank is well formed, so none of its questions is skipped. */
class CourseQuestionBankTest {

  private static final Path BANKS = Path.of("src/main/resources/question-bank");

  @Test
  void eachBankHasFortyToFiftyWellFormedQuestions() throws IOException {
    List<Path> files;
    try (Stream<Path> list = Files.list(BANKS)) {
      files = list.filter(file -> file.toString().endsWith(".json")).sorted().toList();
    }
    assertThat(files)
        .extracting(file -> file.getFileName().toString())
        .contains("25CS21001.json", "25CS21002.json", "25CS21003.json");
    ObjectMapper json = new ObjectMapper();
    for (Path file : files) {
      JsonNode bank = json.readTree(file.toFile());
      assertThat(bank.get("subject").asString() + ".json").isEqualTo(file.getFileName().toString());
      JsonNode questions = bank.get("questions");
      assertThat(questions.size()).as(file.toString()).isBetween(40, 50);
      Set<String> texts = new HashSet<>();
      for (JsonNode question : questions) {
        String text = question.get("text").asString();
        assertThat(texts.add(text)).as("duplicate: " + text).isTrue();
        assertThat(text).hasSizeBetween(10, 1000);
        List<String> options =
            question.get("options").valueStream().map(JsonNode::asString).toList();
        assertThat(options).as(text).hasSize(4).doesNotHaveDuplicates();
        assertThat(options)
            .allSatisfy(option -> assertThat(option).isNotBlank().hasSizeLessThanOrEqualTo(300));
        assertThat(question.get("correct").asString()).isIn("A", "B", "C", "D");
        assertThat(question.get("difficulty").asString()).isIn("EASY", "MEDIUM", "HARD");
        assertThat(question.get("explanation").asString())
            .isNotBlank()
            .hasSizeLessThanOrEqualTo(2000);
      }
    }
  }
}
