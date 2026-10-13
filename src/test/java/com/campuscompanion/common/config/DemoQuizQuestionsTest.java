package com.campuscompanion.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.common.config.DemoQuizSeeder.DemoQuestion;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

/**
 * The demo questions file is complete: every question can be saved and every unit can be tested.
 */
class DemoQuizQuestionsTest {

  private static List<DemoQuestion> questions() throws Exception {
    try (InputStream file = new ClassPathResource("demo/quiz-questions.json").getInputStream()) {
      return List.of(new ObjectMapper().readValue(file, DemoQuestion[].class));
    }
  }

  @Test
  void everyQuestionHasFourDifferentOptionsAnAnswerAndAnExplanation() throws Exception {
    assertThat(questions())
        .isNotEmpty()
        .allSatisfy(
            question -> {
              assertThat(question.text()).isNotBlank().hasSizeLessThanOrEqualTo(1000);
              assertThat(question.options())
                  .hasSize(4)
                  .doesNotHaveDuplicates()
                  .allSatisfy(
                      option -> assertThat(option).isNotBlank().hasSizeLessThanOrEqualTo(300));
              assertThat(question.correct()).isNotNull();
              assertThat(question.difficulty()).isNotNull();
              assertThat(question.explanation()).isNotBlank().hasSizeLessThanOrEqualTo(2000);
            });
  }

  @Test
  void everyUnitWithQuestionsHasEnoughForTheShortestTest() throws Exception {
    Map<String, Long> perUnit =
        questions().stream()
            .collect(
                Collectors.groupingBy(
                    question -> question.subject() + " unit " + question.unit(),
                    Collectors.counting()));

    assertThat(perUnit.values()).allSatisfy(count -> assertThat(count).isGreaterThanOrEqualTo(5));
  }

  @Test
  void noQuestionIsAskedTwice() throws Exception {
    assertThat(questions()).extracting(DemoQuestion::text).doesNotHaveDuplicates();
  }
}
