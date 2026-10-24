package com.campuscompanion.ai.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Loads the prompt templates from src/main/resources/prompts. Each file holds the instructions,
 * then a line "=== USER ===", then the layout of the student's content. The templates are read
 * once, when the application starts, so a missing or broken file stops the start.
 */
@Component
public class PromptLibrary {

  static final String SEPARATOR = "=== USER ===";

  private final PromptTemplate explain = load("explain-v1");
  private final PromptTemplate summarize = load("summarize-v1");
  private final PromptTemplate quiz = load("quiz-v1");
  private final PromptTemplate quickChat = load("assistant-quick-v1");
  private final PromptTemplate flowchart = load("flowchart-v1");

  public PromptTemplate explain() {
    return explain;
  }

  public PromptTemplate summarize() {
    return summarize;
  }

  public PromptTemplate quiz() {
    return quiz;
  }

  /** A topic or a process as the nodes and edges of a flowchart (FR-AI-006). */
  public PromptTemplate flowchart() {
    return flowchart;
  }

  /** The robot's short chat: plain text answers of a few sentences. */
  public PromptTemplate quickChat() {
    return quickChat;
  }

  static PromptTemplate load(String version) {
    String path = "prompts/" + version + ".txt";
    try (InputStream file = new ClassPathResource(path).getInputStream()) {
      String content = new String(file.readAllBytes(), StandardCharsets.UTF_8);
      int split = content.indexOf(SEPARATOR);
      if (split < 0) {
        throw new IllegalStateException("The prompt template " + path + " has no " + SEPARATOR);
      }
      return new PromptTemplate(
          version,
          content.substring(0, split).strip(),
          content.substring(split + SEPARATOR.length()).strip());
    } catch (IOException e) {
      throw new UncheckedIOException("The prompt template " + path + " could not be read", e);
    }
  }
}
