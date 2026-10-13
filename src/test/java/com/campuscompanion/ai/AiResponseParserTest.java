package com.campuscompanion.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campuscompanion.ai.dto.AiBasis;
import com.campuscompanion.ai.dto.GenerateQuizResponse.Question;
import com.campuscompanion.ai.dto.QuestionType;
import com.campuscompanion.ai.parse.AiResponseParser;
import com.campuscompanion.ai.parse.AiResponseParser.Explanation;
import com.campuscompanion.ai.parse.AiResponseParser.Summary;
import com.campuscompanion.ai.parse.GeneratedQuestionValidator;
import com.campuscompanion.quiz.OptionKey;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What the application accepts from the AI (docs/14-AI-security-and-privacy.md section 7). These
 * tests are about shape and size. None of them can show that a reply is true.
 */
class AiResponseParserTest {

  private final AiResponseParser parser = new AiResponseParser(new GeneratedQuestionValidator());

  private static String explanation(String explanation, String keyPoints, String rest) {
    return "{\"declined\":false,\"explanation\":\"%s\",\"keyPoints\":%s%s}"
        .formatted(explanation, keyPoints, rest);
  }

  @Test
  void aCompleteExplanationIsAccepted() {
    Explanation parsed =
        parser.parseExplanation(
            explanation(
                "A stack is last in, first out.",
                "[\"Push adds\",\"Pop removes\"]",
                ",\"example\":\"A pile of plates.\",\"basis\":\"MIXED\""),
            true);

    assertThat(parsed.explanation()).isEqualTo("A stack is last in, first out.");
    assertThat(parsed.keyPoints()).containsExactly("Push adds", "Pop removes");
    assertThat(parsed.example()).isEqualTo("A pile of plates.");
    assertThat(parsed.basis()).isEqualTo(AiBasis.MIXED);
  }

  @Test
  void wordsAroundTheObjectAndUnknownFieldsAreIgnored() {
    String raw =
        "Here is the answer:\n```json\n"
            + explanation(
                "Text.",
                "[\"One\"]",
                ",\"example\":null,\"basis\":\"GENERAL_KNOWLEDGE\",\"mood\":\"happy\"")
            + "\n```\nHope it helps.";

    Explanation parsed = parser.parseExplanation(raw, false);

    assertThat(parsed.explanation()).isEqualTo("Text.");
    assertThat(parsed.example()).isNull();
  }

  @Test
  void withoutTheStudentsMaterialTheBasisIsAlwaysGeneralKnowledge() {
    Explanation parsed =
        parser.parseExplanation(
            explanation("Text.", "[\"One\"]", ",\"basis\":\"PROVIDED_TEXT\""), false);

    assertThat(parsed.basis()).isEqualTo(AiBasis.GENERAL_KNOWLEDGE);
  }

  @Test
  void withMaterialTheBasisMustBeOneOfTheThree() {
    assertInvalid(() -> parser.parseExplanation(explanation("Text.", "[\"One\"]", ""), true));
    assertInvalid(
        () ->
            parser.parseExplanation(
                explanation("Text.", "[\"One\"]", ",\"basis\":\"MY_OWN_IDEA\""), true));
  }

  @Test
  void whatIsNotJsonIsRefused() {
    assertInvalid(() -> parser.parseExplanation("I would rather answer in plain words.", false));
    assertInvalid(() -> parser.parseExplanation("", false));
    assertInvalid(() -> parser.parseExplanation(null, false));
    assertInvalid(() -> parser.parseExplanation("[1, 2, 3]", false));
    // Cut off in the middle, as happens when the output limit is reached.
    assertInvalid(
        () -> parser.parseExplanation("{\"declined\":false,\"explanation\":\"A stack is", false));
  }

  @Test
  void aMissingOrEmptyRequiredFieldIsRefused() {
    assertInvalid(
        () -> parser.parseExplanation("{\"declined\":false,\"keyPoints\":[\"One\"]}", false));
    assertInvalid(() -> parser.parseExplanation(explanation("  ", "[\"One\"]", ""), false));
    assertInvalid(() -> parser.parseExplanation(explanation("Text.", "[]", ""), false));
    assertInvalid(() -> parser.parseExplanation(explanation("Text.", "\"One\"", ""), false));
    assertInvalid(
        () ->
            parser.parseExplanation(
                "{\"declined\":false,\"explanation\":42,\"keyPoints\":[\"One\"]}", false));
  }

  @Test
  void fieldsOverTheirLimitAreRefused() {
    String longPoint = "x".repeat(301);
    String nine = "[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\",\"7\",\"8\",\"9\"]";

    assertInvalid(
        () -> parser.parseExplanation(explanation("Text.", "[\"" + longPoint + "\"]", ""), false));
    assertInvalid(() -> parser.parseExplanation(explanation("Text.", nine, ""), false));
    assertInvalid(
        () ->
            parser.parseExplanation(
                explanation("Text.", "[\"One\"]", ",\"example\":\"" + "x".repeat(1501) + "\""),
                false));
  }

  @Test
  void anOverlongExplanationIsCutAtTheLastFullSentence() {
    String sentence = "This sentence has exactly fifty characters in it, yes. ";
    String longText = sentence.repeat(90);

    Explanation parsed = parser.parseExplanation(explanation(longText, "[\"One\"]", ""), false);

    assertThat(parsed.explanation()).hasSizeLessThanOrEqualTo(4000).endsWith("yes.");
  }

  @Test
  void htmlStaysLiteralTextAndControlCharactersAreRemoved() {
    Explanation parsed =
        parser.parseExplanation(
            explanation(
                "Use <script>alert(1)</script> and <b>bold</b>.\\u0000\\u0007 Next\\nline.",
                "[\"<img src=x onerror=alert(1)>\"]",
                ""),
            false);

    assertThat(parsed.explanation())
        .isEqualTo("Use <script>alert(1)</script> and <b>bold</b>. Next\nline.");
    assertThat(parsed.keyPoints()).containsExactly("<img src=x onerror=alert(1)>");
  }

  @Test
  void aDeclineCarriesTheReasonForTheStudent() {
    assertThatThrownBy(
            () ->
                parser.parseExplanation(
                    "{\"declined\":true,\"reason\":\"I can only help with study topics.\"}", false))
        .isInstanceOf(AiRequestDeclinedException.class)
        .hasMessage("I can only help with study topics.");
    // Without a usable reason, a fixed polite one is used.
    assertThatThrownBy(() -> parser.parseSummary("{\"declined\":true}"))
        .isInstanceOf(AiRequestDeclinedException.class)
        .hasMessageStartingWith("I can only help with study topics.");
    assertThatThrownBy(
            () -> parser.parseSummary("{\"declined\":true,\"reason\":\"" + "x".repeat(301) + "\"}"))
        .isInstanceOf(AiRequestDeclinedException.class)
        .hasMessageStartingWith("I can only help with study topics.");
  }

  @Test
  void aSummaryMayHaveNoDefinitions() {
    Summary parsed =
        parser.parseSummary(
            "{\"declined\":false,\"summary\":\"Short.\",\"keyPoints\":[\"One\"],\"definitions\":[]}");
    Summary withDefinition =
        parser.parseSummary(
            "{\"declined\":false,\"summary\":\"Short.\",\"keyPoints\":[],"
                + "\"definitions\":[{\"term\":\"Deadlock\",\"meaning\":\"Each waits for another.\"}]}");

    assertThat(parsed.definitions()).isEmpty();
    assertThat(withDefinition.definitions().get(0).term()).isEqualTo("Deadlock");
    assertThat(withDefinition.keyPoints()).isEmpty();
  }

  @Test
  void aSummaryWithABrokenListIsRefused() {
    assertInvalid(
        () -> parser.parseSummary("{\"declined\":false,\"summary\":\"Short.\",\"keyPoints\":[]}"));
    assertInvalid(
        () ->
            parser.parseSummary(
                "{\"declined\":false,\"summary\":\"Short.\",\"keyPoints\":[],"
                    + "\"definitions\":[{\"term\":\"Deadlock\"}]}"));
    String eleven = "[" + "\"p\",".repeat(10) + "\"p\"]";
    assertInvalid(
        () ->
            parser.parseSummary(
                "{\"declined\":false,\"summary\":\"Short.\",\"keyPoints\":"
                    + eleven
                    + ",\"definitions\":[]}"));
  }

  private static String mcq(String options, String correct, String answer) {
    return "{\"type\":\"MCQ\",\"question\":\"Which one?\",\"options\":%s,\"correctOption\":\"%s\","
            .formatted(options, correct)
        + "\"answer\":\"%s\",\"explanation\":\"Because.\"}".formatted(answer);
  }

  @Test
  void goodQuestionsAreKeptAndBadOnesAreDropped() {
    String good = mcq("[\"One\",\"Two\",\"Three\",\"Four\"]", "B", "Two");
    String duplicateOptions = mcq("[\"One\",\"Two\",\"one \",\"Four\"]", "B", "Two");
    String threeOptions = mcq("[\"One\",\"Two\",\"Three\"]", "B", "Two");
    String fifthLetter = mcq("[\"One\",\"Two\",\"Three\",\"Four\"]", "E", "Two");
    String answerOfAnotherOption = mcq("[\"One\",\"Two\",\"Three\",\"Four\"]", "B", "Three");
    String raw =
        "{\"declined\":false,\"questions\":["
            + String.join(
                ",", duplicateOptions, good, threeOptions, fifthLetter, answerOfAnotherOption)
            + "]}";

    List<Question> questions = parser.parseQuestions(raw, QuestionType.MCQ, 5);

    assertThat(questions).hasSize(1);
    assertThat(questions.get(0).correctOption()).isEqualTo(OptionKey.B);
    assertThat(questions.get(0).answer()).isEqualTo("Two");
    assertThat(questions.get(0).options()).containsExactly("One", "Two", "Three", "Four");
  }

  @Test
  void noMoreQuestionsAreKeptThanWereAskedFor() {
    String good = mcq("[\"One\",\"Two\",\"Three\",\"Four\"]", "A", "One");
    String raw = "{\"declined\":false,\"questions\":[" + String.join(",", good, good, good) + "]}";

    assertThat(parser.parseQuestions(raw, QuestionType.MCQ, 2)).hasSize(2);
  }

  @Test
  void whenNoQuestionSurvivesTheReplyIsInvalid() {
    String bad = mcq("[\"One\",\"One\",\"Three\",\"Four\"]", "A", "One");

    assertInvalid(
        () ->
            parser.parseQuestions(
                "{\"declined\":false,\"questions\":[" + bad + "]}", QuestionType.MCQ, 3));
    assertInvalid(
        () -> parser.parseQuestions("{\"declined\":false,\"questions\":[]}", QuestionType.MCQ, 3));
    assertInvalid(() -> parser.parseQuestions("{\"declined\":false}", QuestionType.MCQ, 3));
  }

  @Test
  void aWrittenQuestionHasNoOptionsAndMustBeOfTheTypeAskedFor() {
    String written =
        "{\"type\":\"SHORT\",\"question\":\"What is a stack?\",\"options\":null,"
            + "\"correctOption\":null,\"answer\":\"A last in, first out list.\",\"explanation\":\"By definition.\"}";
    String raw = "{\"declined\":false,\"questions\":[" + written + "]}";

    List<Question> questions = parser.parseQuestions(raw, QuestionType.SHORT, 1);

    assertThat(questions.get(0).options()).isNull();
    assertThat(questions.get(0).correctOption()).isNull();
    assertInvalid(() -> parser.parseQuestions(raw, QuestionType.LONG, 1));
    assertInvalid(() -> parser.parseQuestions(raw, QuestionType.MCQ, 1));
  }

  private static void assertInvalid(Runnable parse) {
    assertThatThrownBy(parse::run).isInstanceOf(AiInvalidResponseException.class);
  }
}
