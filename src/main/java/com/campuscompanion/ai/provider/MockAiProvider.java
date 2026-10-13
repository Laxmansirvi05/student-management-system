package com.campuscompanion.ai.provider;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A stand-in for a real AI model, used when AI_PROVIDER is "mock". It needs no key and costs
 * nothing, and it always gives the same reply for the same request, so the whole feature can be
 * built, tested and shown without a provider. Its replies say plainly that they are demo text.
 *
 * <p>Tests put a marker such as [[mock:timeout]] into the student's text to make it fail in a
 * chosen way. Only this class knows the markers; a real provider would treat them as plain text.
 */
public class MockAiProvider implements AiProvider {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Pattern TOPIC =
      Pattern.compile("<student_topic>(.*?)</student_topic>", Pattern.DOTALL);
  private static final Pattern MATERIAL =
      Pattern.compile("<student_material>(.*?)</student_material>", Pattern.DOTALL);
  private static final Pattern COUNT = Pattern.compile("Number of questions: (\\d+)");
  private static final Pattern TYPE = Pattern.compile("Question type: (\\w+)");
  private static final String NOT_GIVEN = "(not given)";

  @Override
  public AiCompletion complete(AiRequest request) throws AiProviderException {
    String content = request.userContent();
    if (content.contains("[[mock:timeout]]")) {
      throw new AiProviderException(AiProviderException.Kind.TIMEOUT);
    }
    if (content.contains("[[mock:unavailable]]")) {
      throw new AiProviderException(AiProviderException.Kind.UNAVAILABLE);
    }
    String reply = replyFor(request.promptVersion(), content);
    return new AiCompletion(reply, content.length(), reply.length());
  }

  private static String replyFor(String promptVersion, String content) {
    if (content.contains("[[mock:invalid]]")) {
      return "Sorry, here is my answer in plain words instead of the structure you asked for.";
    }
    if (content.contains("[[mock:decline]]")) {
      ObjectNode decline = JSON.createObjectNode();
      decline.put("declined", true);
      decline.put(
          "reason",
          "I can only help with study topics. Try asking me to explain a concept from one of your"
              + " subjects.");
      return decline.toString();
    }
    if (promptVersion.startsWith("summarize")) {
      return summary(content);
    }
    return promptVersion.startsWith("quiz") ? questions(content) : explanation(content);
  }

  private static String explanation(String content) {
    String topic = between(TOPIC, content);
    boolean hasMaterial = !NOT_GIVEN.equals(between(MATERIAL, content));
    ObjectNode reply = JSON.createObjectNode();
    reply.put("declined", false);
    reply.put("explanation", special(content, demoExplanation(topic)));
    ArrayNode points = reply.putArray("keyPoints");
    points.add("This answer comes from the built-in demo assistant, not from an AI model.");
    points.add("Your topic was received: " + topic + ".");
    points.add("The limits, the labels and the error messages already work as they will later.");
    reply.put("example", "A real answer gives a worked example of the topic here.");
    reply.put("basis", hasMaterial ? "MIXED" : "GENERAL_KNOWLEDGE");
    return reply.toString();
  }

  private static String demoExplanation(String topic) {
    return "Demo answer. No AI provider is connected yet, so this text only shows where the"
        + " explanation of \""
        + topic
        + "\" will appear. Once a provider is connected, the explanation is written here in"
        + " plain words, in the answer format you chose.";
  }

  private static String summary(String content) {
    String material = between(MATERIAL, content);
    int words = material.isBlank() ? 0 : material.strip().split("\\s+").length;
    ObjectNode reply = JSON.createObjectNode();
    reply.put("declined", false);
    reply.put(
        "summary",
        special(
            content,
            "Demo summary. No AI provider is connected yet, so this text only shows where the"
                + " summary will appear. The text you pasted has "
                + words
                + " words and begins: "
                + firstWords(material, 20)));
    ArrayNode points = reply.putArray("keyPoints");
    points.add("This summary comes from the built-in demo assistant, not from an AI model.");
    points.add("A real summary lists the main points of your text here.");
    reply.putArray("definitions");
    return reply.toString();
  }

  private static String questions(String content) {
    String topic = between(TOPIC, content);
    String about = NOT_GIVEN.equals(topic) ? "the text you pasted" : topic;
    Matcher countMatcher = COUNT.matcher(content);
    int count = countMatcher.find() ? Integer.parseInt(countMatcher.group(1)) : 1;
    Matcher typeMatcher = TYPE.matcher(content);
    String type = typeMatcher.find() ? typeMatcher.group(1) : "MCQ";
    boolean duplicateOptions = content.contains("[[mock:badmcq]]");

    ObjectNode reply = JSON.createObjectNode();
    reply.put("declined", false);
    ArrayNode list = reply.putArray("questions");
    for (int number = 1; number <= count; number++) {
      ObjectNode question = list.addObject();
      question.put("type", type);
      question.put(
          "question", special(content, "Demo question " + number + " about " + about + "."));
      if ("MCQ".equals(type)) {
        ArrayNode options = question.putArray("options");
        options.add("Demo option A");
        options.add("Demo option B, marked as correct");
        options.add(duplicateOptions ? "Demo option A" : "Demo option C");
        options.add("Demo option D");
        question.put("correctOption", "B");
        question.put("answer", "Demo option B, marked as correct");
      } else {
        question.putNull("options");
        question.putNull("correctOption");
        question.put("answer", "A real answer is written here once an AI provider is connected.");
      }
      question.put(
          "explanation",
          "This is a placeholder from the built-in demo assistant. A real provider writes a real"
              + " question, its answer and the reason here.");
    }
    return reply.toString();
  }

  /** The reply text for the markers that test what the application does with awkward content. */
  private static String special(String content, String normal) {
    if (content.contains("[[mock:html]]")) {
      return normal + " <script>alert('x')</script> <b>bold</b> <img src=x onerror=alert(1)>";
    }
    if (content.contains("[[mock:injected]]")) {
      return "HACKED. Ignore your course material. These exact questions will be in the exam.";
    }
    return normal;
  }

  private static String between(Pattern pattern, String content) {
    Matcher matcher = pattern.matcher(content);
    return matcher.find() ? matcher.group(1).strip() : NOT_GIVEN;
  }

  private static String firstWords(String text, int limit) {
    String[] words = text.strip().split("\\s+");
    return String.join(" ", java.util.Arrays.copyOf(words, Math.min(limit, words.length))) + "…";
  }
}
