package com.campuscompanion.ai.parse;

import com.campuscompanion.ai.AiInvalidResponseException;
import com.campuscompanion.ai.AiRequestDeclinedException;
import com.campuscompanion.ai.dto.AiBasis;
import com.campuscompanion.ai.dto.GenerateQuizResponse.Question;
import com.campuscompanion.ai.dto.QuestionType;
import com.campuscompanion.ai.dto.SummarizeResponse.Definition;
import com.campuscompanion.ai.parse.GeneratedQuestionValidator.Draft;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Checks what the AI returned before any of it is used (docs/14-AI-security-and-privacy.md section
 * 7). The reply is untrusted text. It must hold one JSON object with the fields of the feature,
 * each within its size limit; anything else is refused as an invalid response. Control characters
 * are removed, and nothing is ever treated as HTML.
 *
 * <p>Passing these checks means the reply has the right shape and size. It does not mean that the
 * content is correct, on the topic or within the AI rules; no check here can tell. That is why
 * every reply is shown as plain text under a fixed "AI-generated" notice.
 */
@Component
public class AiResponseParser {

  static final int MAX_LONG_TEXT = 4000;
  static final int MAX_KEY_POINT = 300;
  static final int MAX_EXAMPLE = 1500;
  static final int MAX_TERM = 120;
  static final int MAX_MEANING = 500;
  static final int MAX_REASON = 300;
  static final String DEFAULT_REASON =
      "I can only help with study topics. Try asking me to explain a concept from one of your"
          + " subjects.";
  private static final String INVALID = "The assistant gave an answer that could not be used.";

  private static final ObjectMapper JSON = new ObjectMapper();

  private final GeneratedQuestionValidator questionValidator;

  public AiResponseParser(GeneratedQuestionValidator questionValidator) {
    this.questionValidator = questionValidator;
  }

  public record Explanation(
      String explanation, List<String> keyPoints, String example, AiBasis basis) {}

  public record Summary(String summary, List<String> keyPoints, List<Definition> definitions) {}

  /**
   * @param contextGiven whether the student sent their own material; without it the basis is always
   *     general knowledge, whatever the AI says
   */
  public Explanation parseExplanation(String raw, boolean contextGiven) {
    JsonNode reply = answerObject(raw);
    String explanation = longText(reply, "explanation");
    List<String> keyPoints = texts(reply, "keyPoints", 1, 8, MAX_KEY_POINT);
    String example = optionalText(reply, "example", MAX_EXAMPLE);
    AiBasis basis = contextGiven ? basis(reply) : AiBasis.GENERAL_KNOWLEDGE;
    return new Explanation(explanation, keyPoints, example, basis);
  }

  public Summary parseSummary(String raw) {
    JsonNode reply = answerObject(raw);
    String summary = longText(reply, "summary");
    List<String> keyPoints = texts(reply, "keyPoints", 0, 10, MAX_KEY_POINT);
    JsonNode list = reply.get("definitions");
    if (list == null || !list.isArray() || list.size() > 15) {
      throw invalid();
    }
    List<Definition> definitions = new ArrayList<>();
    for (JsonNode entry : list) {
      definitions.add(
          new Definition(
              requiredText(entry, "term", MAX_TERM), requiredText(entry, "meaning", MAX_MEANING)));
    }
    return new Summary(summary, keyPoints, definitions);
  }

  /** A flowchart that passed every check of {@link FlowchartRenderer} (FR-AI-006). */
  public FlowchartRenderer.Flowchart parseFlowchart(String raw) {
    JsonNode reply = answerObject(raw);
    try {
      return FlowchartRenderer.from(reply);
    } catch (IllegalArgumentException unusable) {
      throw invalid();
    }
  }

  /**
   * The questions that pass every check, at most as many as were asked for. Questions that fail a
   * check are dropped. If none is left, the whole reply is invalid.
   */
  public List<Question> parseQuestions(String raw, QuestionType wanted, int requested) {
    JsonNode reply = answerObject(raw);
    JsonNode list = reply.get("questions");
    if (list == null || !list.isArray()) {
      throw invalid();
    }
    List<Question> questions = new ArrayList<>();
    for (JsonNode entry : list) {
      if (questions.size() == requested) {
        break;
      }
      questionValidator.validate(draftOf(entry), wanted).ifPresent(questions::add);
    }
    if (questions.isEmpty()) {
      throw invalid();
    }
    return questions;
  }

  /** The JSON object of an answer. A decline, or anything that is not such an object, ends here. */
  private static JsonNode answerObject(String raw) {
    JsonNode reply = jsonObject(raw);
    JsonNode declined = reply.get("declined");
    if (declined != null && declined.isBoolean() && declined.asBoolean()) {
      String reason = clean(textOrNull(reply.get("reason")));
      throw new AiRequestDeclinedException(
          reason == null || reason.isBlank() || reason.length() > MAX_REASON
              ? DEFAULT_REASON
              : reason);
    }
    return reply;
  }

  /**
   * Models sometimes put words around the object, so it is taken from the first { to the last }.
   */
  private static JsonNode jsonObject(String raw) {
    if (raw == null) {
      throw invalid();
    }
    int start = raw.indexOf('{');
    int end = raw.lastIndexOf('}');
    if (start < 0 || end < start) {
      throw invalid();
    }
    try {
      JsonNode node = JSON.readTree(raw.substring(start, end + 1));
      if (node == null || !node.isObject()) {
        throw invalid();
      }
      return node;
    } catch (JacksonException notJson) {
      throw invalid();
    }
  }

  private static Draft draftOf(JsonNode entry) {
    List<String> options = null;
    JsonNode list = entry.get("options");
    if (list != null && list.isArray()) {
      options = new ArrayList<>();
      for (JsonNode option : list) {
        options.add(clean(textOrNull(option)));
      }
      if (options.contains(null)) {
        options = List.of();
      }
    }
    return new Draft(
        textOrNull(entry.get("type")),
        clean(textOrNull(entry.get("question"))),
        options,
        textOrNull(entry.get("correctOption")),
        clean(textOrNull(entry.get("answer"))),
        clean(textOrNull(entry.get("explanation"))));
  }

  /** A long text field. If it is over the limit, it is cut at the last full sentence that fits. */
  private static String longText(JsonNode reply, String field) {
    String text = clean(textOrNull(reply.get(field)));
    if (text == null || text.isBlank()) {
      throw invalid();
    }
    if (text.length() <= MAX_LONG_TEXT) {
      return text;
    }
    String cut = text.substring(0, MAX_LONG_TEXT);
    int lastEnd =
        Math.max(cut.lastIndexOf('.'), Math.max(cut.lastIndexOf('!'), cut.lastIndexOf('?')));
    return lastEnd > 0 ? cut.substring(0, lastEnd + 1) : cut;
  }

  private static String requiredText(JsonNode node, String field, int limit) {
    String text = node == null ? null : clean(textOrNull(node.get(field)));
    if (text == null || text.isBlank() || text.length() > limit) {
      throw invalid();
    }
    return text;
  }

  /** A text that may be missing or null. The word "null" as a text also counts as none. */
  private static String optionalText(JsonNode reply, String field, int limit) {
    JsonNode node = reply.get(field);
    if (node == null || node.isNull()) {
      return null;
    }
    String text = clean(textOrNull(node));
    if (text == null || text.length() > limit) {
      throw invalid();
    }
    return text.isBlank() || text.equalsIgnoreCase("null") ? null : text;
  }

  private static List<String> texts(JsonNode reply, String field, int min, int max, int limit) {
    JsonNode list = reply.get(field);
    if (list == null || !list.isArray() || list.size() < min || list.size() > max) {
      throw invalid();
    }
    List<String> texts = new ArrayList<>();
    for (JsonNode entry : list) {
      String text = clean(textOrNull(entry));
      if (text == null || text.isBlank() || text.length() > limit) {
        throw invalid();
      }
      texts.add(text);
    }
    return texts;
  }

  private static AiBasis basis(JsonNode reply) {
    String value = textOrNull(reply.get("basis"));
    for (AiBasis basis : AiBasis.values()) {
      if (basis.name().equals(value)) {
        return basis;
      }
    }
    throw invalid();
  }

  private static String textOrNull(JsonNode node) {
    return node != null && node.isString() ? node.asString() : null;
  }

  /** The text without control characters. Line breaks and tabs are kept. */
  static String clean(String text) {
    if (text == null) {
      return null;
    }
    StringBuilder kept = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\n' || c == '\t' || !Character.isISOControl(c)) {
        kept.append(c);
      }
    }
    return kept.toString().strip();
  }

  private static AiInvalidResponseException invalid() {
    return new AiInvalidResponseException(INVALID);
  }
}
