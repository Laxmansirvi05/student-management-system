package com.campuscompanion.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campuscompanion.ai.dto.AnswerFormat;
import com.campuscompanion.ai.dto.ExplainRequest;
import com.campuscompanion.ai.dto.ExplainResponse;
import com.campuscompanion.ai.parse.AiResponseParser;
import com.campuscompanion.ai.parse.GeneratedQuestionValidator;
import com.campuscompanion.ai.prompt.PromptBuilder;
import com.campuscompanion.ai.prompt.PromptLibrary;
import com.campuscompanion.ai.provider.AiCompletion;
import com.campuscompanion.ai.provider.AiProvider;
import com.campuscompanion.ai.provider.AiProviderException;
import com.campuscompanion.ai.provider.AiRequest;
import com.campuscompanion.auth.Role;
import com.campuscompanion.common.config.AiProperties;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.subject.SubjectService;
import com.campuscompanion.subject.SubjectService.UnitOfSubject;
import com.campuscompanion.subject.dto.SubjectResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * One AI request from start to end, with a mock in place of the provider: what is sent out, and
 * what is recorded when the provider fails.
 */
class AiStudyServiceTest {

  /** An ID that would be easy to spot if it were ever sent to the provider. */
  private static final CurrentUser STUDENT = new CurrentUser(987654321L, Role.STUDENT);

  private static final String GOOD_REPLY =
      "{\"declined\":false,\"explanation\":\"A tree has nodes.\",\"keyPoints\":[\"Root\"],"
          + "\"example\":null,\"basis\":\"GENERAL_KNOWLEDGE\"}";

  private final AiUsageService usage = mock(AiUsageService.class);
  private final AiProvider provider = mock(AiProvider.class);
  private final SubjectService subjects = mock(SubjectService.class);
  private final PromptLibrary templates = new PromptLibrary();

  private AiStudyService service;

  @BeforeEach
  void setUp() {
    AiProperties properties =
        new AiProperties(
            true,
            "mock",
            "",
            "",
            30,
            500,
            1200,
            5,
            2,
            Duration.ofSeconds(5),
            Duration.ofSeconds(20),
            Duration.ofSeconds(25));
    service =
        new AiStudyService(
            usage,
            provider,
            templates,
            new PromptBuilder(),
            new AiResponseParser(new GeneratedQuestionValidator()),
            subjects,
            properties);
    when(subjects.visibleTo(STUDENT, null))
        .thenReturn(List.of(new SubjectResponse(12L, "CS301", "Data Structures", "CSE", 3, 4)));
    when(subjects.findUnit(31L)).thenReturn(Optional.of(new UnitOfSubject(31L, 3, "Trees", 12L)));
  }

  private static ExplainRequest request(String topic) {
    return new ExplainRequest(topic, 12L, 31L, AnswerFormat.SIMPLE, null);
  }

  @Test
  void onlyTheTopicTheNamesAndTheInstructionsAreSentOut() throws Exception {
    when(provider.complete(any())).thenReturn(new AiCompletion(GOOD_REPLY, 10, 20));

    ExplainResponse response = service.explain(request("binary search trees"), STUDENT);

    ArgumentCaptor<AiRequest> sent = ArgumentCaptor.forClass(AiRequest.class);
    verify(provider).complete(sent.capture());
    AiRequest request = sent.getValue();
    assertThat(request.systemInstructions()).isEqualTo(templates.explain().systemInstructions());
    assertThat(request.userContent())
        .contains("Subject: Data Structures")
        .contains("Unit: Trees")
        .contains("<student_topic>binary search trees</student_topic>");
    // Nothing says who is asking: no user ID, no role, no subject or unit ID.
    assertThat(request.systemInstructions() + request.userContent())
        .doesNotContain("987654321")
        .doesNotContain("STUDENT")
        .doesNotContain("CS301");
    assertThat(request.maxOutputTokens()).isEqualTo(1200);
    assertThat(request.timeout()).isEqualTo(Duration.ofSeconds(20));

    assertThat(response.notice()).isEqualTo(AiStudyService.NOTICE);
    assertThat(response.promptVersion()).isEqualTo("explain-v1");
    verify(usage)
        .record(
            eq(987654321L),
            eq(AiFeature.EXPLAIN),
            eq("explain-v1"),
            anyInt(),
            eq(GOOD_REPLY.length()),
            eq(AiRequestStatus.SUCCESS),
            anyLong());
    verify(usage).end(987654321L);
  }

  @Test
  void whenALimitRefusesTheRequestTheProviderIsNeverCalled() throws Exception {
    doThrow(new AiQuotaExceededException("limit")).when(usage).begin(987654321L);

    assertThatThrownBy(() -> service.explain(request("stacks"), STUDENT))
        .isInstanceOf(AiQuotaExceededException.class);

    verify(provider, never()).complete(any());
    verify(usage, never()).record(anyLong(), any(), anyString(), anyInt(), any(), any(), anyLong());
  }

  @Test
  void aProviderFailureBecomesUnavailableAndIsRecordedAsFailed() throws Exception {
    when(provider.complete(any()))
        .thenThrow(new AiProviderException(AiProviderException.Kind.RATE_LIMITED));

    assertThatThrownBy(() -> service.explain(request("stacks"), STUDENT))
        .isInstanceOf(AiUnavailableException.class);

    verify(usage)
        .record(
            eq(987654321L),
            eq(AiFeature.EXPLAIN),
            eq("explain-v1"),
            anyInt(),
            eq(null),
            eq(AiRequestStatus.FAILED),
            anyLong());
    verify(usage).end(987654321L);
  }

  @Test
  void aProviderTimeoutBecomesATimeout() throws Exception {
    when(provider.complete(any()))
        .thenThrow(new AiProviderException(AiProviderException.Kind.TIMEOUT));

    assertThatThrownBy(() -> service.explain(request("stacks"), STUDENT))
        .isInstanceOf(AiTimeoutException.class);

    verify(usage)
        .record(
            anyLong(),
            any(),
            anyString(),
            anyInt(),
            eq(null),
            eq(AiRequestStatus.TIMEOUT),
            anyLong());
  }

  @Test
  void aReplyThatCannotBeUsedIsRecordedAsFailed() throws Exception {
    when(provider.complete(any())).thenReturn(new AiCompletion("not json", 10, 8));

    assertThatThrownBy(() -> service.explain(request("stacks"), STUDENT))
        .isInstanceOf(AiInvalidResponseException.class);

    verify(usage)
        .record(
            anyLong(), any(), anyString(), anyInt(), eq(8), eq(AiRequestStatus.FAILED), anyLong());
    verify(usage).end(987654321L);
  }
}
