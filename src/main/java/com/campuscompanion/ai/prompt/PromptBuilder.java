package com.campuscompanion.ai.prompt;

import com.campuscompanion.ai.provider.AiRequest;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Turns a template and the student's input into one request for the model
 * (docs/14-AI-security-and-privacy.md section 4).
 *
 * <p>The instructions come only from the template file. The student's text goes only into the user
 * content, inside tags such as &lt;student_topic&gt;. Before it is put there, anything in it that
 * looks like one of those tags is removed, so the text cannot close its own wrapper and pretend
 * that what follows is something else.
 */
@Component
public class PromptBuilder {

  private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");

  /** An opening or closing tag of the wrapper, in any letter case, with any spacing. */
  private static final Pattern WRAPPER_TAG =
      Pattern.compile("<\\s*/?\\s*student_[a-z_]*[^>]*>", Pattern.CASE_INSENSITIVE);

  static final String NOT_GIVEN = "(not given)";

  /**
   * @param values the value of every placeholder of the template's user layout; a null or blank
   *     value is written as "(not given)"
   */
  public AiRequest build(
      PromptTemplate template, Map<String, String> values, int maxOutputTokens, Duration timeout) {
    // The layout is read once from start to end. A value is copied in as it is and is not read
    // again, so a "{placeholder}" typed by a student stays plain text.
    Matcher matcher = PLACEHOLDER.matcher(template.userLayout());
    StringBuilder content = new StringBuilder();
    while (matcher.find()) {
      String name = matcher.group(1);
      if (!values.containsKey(name)) {
        throw new IllegalArgumentException("No value for the placeholder " + name);
      }
      matcher.appendReplacement(content, Matcher.quoteReplacement(clean(values.get(name))));
    }
    matcher.appendTail(content);
    return new AiRequest(
        template.version(),
        template.systemInstructions(),
        content.toString(),
        maxOutputTokens,
        timeout);
  }

  /** The text without wrapper tags. Repeats until none is left, so a split tag cannot re-form. */
  static String clean(String text) {
    if (text == null || text.isBlank()) {
      return NOT_GIVEN;
    }
    String cleaned = text;
    String before;
    do {
      before = cleaned;
      cleaned = WRAPPER_TAG.matcher(cleaned).replaceAll("");
    } while (!cleaned.equals(before));
    return cleaned.isBlank() ? NOT_GIVEN : cleaned.strip();
  }
}
