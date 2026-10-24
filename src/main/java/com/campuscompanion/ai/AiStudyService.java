package com.campuscompanion.ai;

import com.campuscompanion.ai.dto.AiBasis;
import com.campuscompanion.ai.dto.ExplainRequest;
import com.campuscompanion.ai.dto.ExplainResponse;
import com.campuscompanion.ai.dto.FlowchartRequest;
import com.campuscompanion.ai.dto.FlowchartResponse;
import com.campuscompanion.ai.dto.GenerateQuizRequest;
import com.campuscompanion.ai.dto.GenerateQuizResponse;
import com.campuscompanion.ai.dto.SummarizeRequest;
import com.campuscompanion.ai.dto.SummarizeResponse;
import com.campuscompanion.ai.parse.AiResponseParser;
import com.campuscompanion.ai.parse.AiResponseParser.Explanation;
import com.campuscompanion.ai.parse.AiResponseParser.Summary;
import com.campuscompanion.ai.parse.FlowchartRenderer;
import com.campuscompanion.ai.prompt.PromptBuilder;
import com.campuscompanion.ai.prompt.PromptLibrary;
import com.campuscompanion.ai.provider.AiCompletion;
import com.campuscompanion.ai.provider.AiProvider;
import com.campuscompanion.ai.provider.AiProviderException;
import com.campuscompanion.ai.provider.AiRequest;
import com.campuscompanion.common.config.AiProperties;
import com.campuscompanion.common.error.ValidationException;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.subject.SubjectService;
import com.campuscompanion.subject.SubjectService.UnitOfSubject;
import com.campuscompanion.subject.dto.SubjectResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs one AI request from start to end (docs/15-AI-features-handoff.md section 4): check the
 * limits, look up the names of the subject and unit, build the prompt, call the provider, check the
 * reply, write the usage row, and turn every failure into its error code.
 *
 * <p>What is sent to the provider is the student's own text for this request, the names of the
 * chosen subject and unit, and the instructions. Nothing about any person is sent, and this class
 * cannot reach marks, attendance, results or profiles: it has no access to those services.
 */
@Service
public class AiStudyService {

  static final String NOTICE = "AI-generated. Check it against your course material.";
  static final String QUIZ_NOTICE =
      "AI-generated practice questions. They are not official exam questions.";
  private static final String UNAVAILABLE = "The assistant is unavailable right now.";
  private static final String INVALID = "The assistant gave an answer that could not be used.";

  private static final Logger log = LoggerFactory.getLogger(AiStudyService.class);

  private final AiUsageService usage;
  private final AiProvider provider;
  private final PromptLibrary templates;
  private final PromptBuilder prompts;
  private final AiResponseParser parser;
  private final SubjectService subjects;
  private final AiProperties properties;

  public AiStudyService(
      AiUsageService usage,
      AiProvider provider,
      PromptLibrary templates,
      PromptBuilder prompts,
      AiResponseParser parser,
      SubjectService subjects,
      AiProperties properties) {
    this.usage = usage;
    this.provider = provider;
    this.templates = templates;
    this.prompts = prompts;
    this.parser = parser;
    this.subjects = subjects;
    this.properties = properties;
  }

  public ExplainResponse explain(ExplainRequest request, CurrentUser user) {
    usage.begin(user.id());
    try {
      Map<String, String> values = names(user, request.subjectId(), request.unitId());
      boolean hasContext = request.context() != null && !request.context().isBlank();
      values.put("answerFormat", request.answerFormat().name());
      values.put("topic", request.topic());
      values.put("context", request.context());
      AiRequest prompt = prompt(templates.explain(), values);

      Explanation parsed =
          ask(user, AiFeature.EXPLAIN, prompt, reply -> parser.parseExplanation(reply, hasContext));
      return new ExplainResponse(
          request.topic().strip(),
          request.answerFormat(),
          parsed.explanation(),
          parsed.keyPoints(),
          parsed.example(),
          parsed.basis(),
          NOTICE,
          prompt.promptVersion());
    } finally {
      usage.end(user.id());
    }
  }

  public SummarizeResponse summarize(SummarizeRequest request, CurrentUser user) {
    usage.begin(user.id());
    try {
      Map<String, String> values = names(user, request.subjectId(), null);
      values.put("style", request.style().name());
      values.put("text", request.text());
      AiRequest prompt = prompt(templates.summarize(), values);

      Summary parsed = ask(user, AiFeature.SUMMARIZE, prompt, parser::parseSummary);
      // A summary is always of the student's own text, so Java sets the basis, not the model.
      return new SummarizeResponse(
          parsed.summary(),
          parsed.keyPoints(),
          parsed.definitions(),
          AiBasis.PROVIDED_TEXT,
          NOTICE,
          prompt.promptVersion());
    } finally {
      usage.end(user.id());
    }
  }

  /** FR-AI-006: a topic or a described process as a checked flowchart of at most 20 steps. */
  public FlowchartResponse flowchart(FlowchartRequest request, CurrentUser user) {
    usage.begin(user.id());
    try {
      boolean hasTopic = request.topic() != null && !request.topic().isBlank();
      boolean hasText = request.processText() != null && !request.processText().isBlank();
      if (hasTopic == hasText) {
        throw new ValidationException("topic", "Give a topic or describe a process, but not both.");
      }
      Map<String, String> values = names(user, request.subjectId(), null);
      values.put("topic", request.topic());
      values.put("processText", request.processText());
      AiRequest prompt = prompt(templates.flowchart(), values);

      FlowchartRenderer.Flowchart chart =
          ask(user, AiFeature.FLOWCHART, prompt, parser::parseFlowchart);
      // Java sets the basis: the student's own description, or general knowledge for a topic.
      return new FlowchartResponse(
          chart.title(),
          chart.nodes(),
          chart.edges(),
          chart.steps(),
          chart.mermaid(),
          hasText ? AiBasis.PROVIDED_TEXT : AiBasis.GENERAL_KNOWLEDGE,
          NOTICE,
          prompt.promptVersion());
    } finally {
      usage.end(user.id());
    }
  }

  public GenerateQuizResponse generateQuiz(GenerateQuizRequest request, CurrentUser user) {
    usage.begin(user.id());
    try {
      boolean hasTopic = request.topic() != null && !request.topic().isBlank();
      boolean hasText = request.sourceText() != null && !request.sourceText().isBlank();
      if (hasTopic == hasText) {
        throw new ValidationException("topic", "Give a topic or paste text, but not both.");
      }
      Map<String, String> values = names(user, request.subjectId(), request.unitId());
      values.put("questionType", request.questionType().name());
      values.put("count", String.valueOf(request.count()));
      values.put("topic", request.topic());
      values.put("sourceText", request.sourceText());
      AiRequest prompt = prompt(templates.quiz(), values);

      List<GenerateQuizResponse.Question> questions =
          ask(
              user,
              AiFeature.GENERATE_QUIZ,
              prompt,
              reply -> parser.parseQuestions(reply, request.questionType(), request.count()));
      return new GenerateQuizResponse(
          questions,
          request.count(),
          questions.size(),
          hasText ? AiBasis.PROVIDED_TEXT : AiBasis.GENERAL_KNOWLEDGE,
          QUIZ_NOTICE,
          prompt.promptVersion());
    } finally {
      usage.end(user.id());
    }
  }

  /**
   * Calls the provider, checks the reply and writes the usage row. Only a success or a decline
   * counts toward the limits; a failure or a timeout is logged but does not use up a request.
   */
  private <T> T ask(
      CurrentUser user, AiFeature feature, AiRequest prompt, Function<String, T> parse) {
    long started = System.nanoTime();
    int inputChars = prompt.userContent().length();
    Integer outputChars = null;
    AiRequestStatus status = AiRequestStatus.FAILED;
    try {
      // With several models configured, a reply that fails the parser sends the request on to the
      // next model; a decline is a usable reply, not a failure.
      AiCompletion completion = provider.complete(prompt, reply -> usable(parse, reply));
      outputChars = completion.text() == null ? 0 : completion.text().length();
      T parsed = parse.apply(completion.text());
      status = AiRequestStatus.SUCCESS;
      return parsed;
    } catch (AiRequestDeclinedException declined) {
      status = AiRequestStatus.DECLINED;
      throw declined;
    } catch (AiProviderException failure) {
      // Only the kind of failure is logged. The provider's own message can repeat the input.
      log.warn("The AI provider call failed: {}", failure.getKind());
      if (failure.getKind() == AiProviderException.Kind.TIMEOUT) {
        status = AiRequestStatus.TIMEOUT;
        throw new AiTimeoutException(UNAVAILABLE);
      }
      if (failure.getKind() == AiProviderException.Kind.INVALID_OUTPUT) {
        throw new AiInvalidResponseException(INVALID);
      }
      throw new AiUnavailableException(UNAVAILABLE);
    } finally {
      long durationMs = (System.nanoTime() - started) / 1_000_000;
      usage.record(
          user.id(), feature, prompt.promptVersion(), inputChars, outputChars, status, durationMs);
    }
  }

  /** Whether the parser accepts a reply. A reply the model used to decline counts as usable. */
  private static <T> boolean usable(Function<String, T> parse, String reply) {
    try {
      parse.apply(reply);
      return true;
    } catch (AiRequestDeclinedException declined) {
      return true;
    } catch (AiInvalidResponseException unusable) {
      return false;
    }
  }

  private AiRequest prompt(
      com.campuscompanion.ai.prompt.PromptTemplate template, Map<String, String> values) {
    return prompts.build(
        template, values, properties.maxOutputTokens(), properties.responseTimeout());
  }

  /**
   * The names of the chosen subject and unit, read from the database by ID. The request never
   * supplies a name. The subject must be one the student can see, and the unit must be its own.
   */
  private Map<String, String> names(CurrentUser user, Long subjectId, Long unitId) {
    Map<String, String> values = new HashMap<>();
    values.put("subjectName", null);
    values.put("unitTitle", null);
    if (subjectId == null) {
      if (unitId != null) {
        throw new ValidationException("unitId", "Choose the subject of this unit as well.");
      }
      return values;
    }
    SubjectResponse subject =
        subjects.visibleTo(user, null).stream()
            .filter(entry -> entry.id().equals(subjectId))
            .findFirst()
            .orElseThrow(
                () -> new ValidationException("subjectId", "Choose one of your subjects."));
    values.put("subjectName", subject.name());
    if (unitId != null) {
      UnitOfSubject unit =
          subjects
              .findUnit(unitId)
              .filter(entry -> entry.subjectId().equals(subjectId))
              .orElseThrow(
                  () -> new ValidationException("unitId", "Choose a unit of this subject."));
      values.put("unitTitle", unit.title());
    }
    return values;
  }
}
