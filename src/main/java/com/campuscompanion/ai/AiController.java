package com.campuscompanion.ai;

import com.campuscompanion.ai.dto.AiUsageResponse;
import com.campuscompanion.ai.dto.ExplainRequest;
import com.campuscompanion.ai.dto.ExplainResponse;
import com.campuscompanion.ai.dto.GenerateQuizRequest;
import com.campuscompanion.ai.dto.GenerateQuizResponse;
import com.campuscompanion.ai.dto.SummarizeRequest;
import com.campuscompanion.ai.dto.SummarizeResponse;
import com.campuscompanion.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The AI study assistant: three single-request tools and the usage count. It is not a chat, and
 * nothing is remembered between requests. Contract: docs/09-api-contract.md section 9.
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

  private final AiStudyService study;
  private final AiUsageService usage;

  public AiController(AiStudyService study, AiUsageService usage) {
    this.study = study;
    this.usage = usage;
  }

  @PostMapping("/explain")
  public ExplainResponse explain(
      @Valid @RequestBody ExplainRequest request, @AuthenticationPrincipal CurrentUser user) {
    return study.explain(request, user);
  }

  @PostMapping("/summarize")
  public SummarizeResponse summarize(
      @Valid @RequestBody SummarizeRequest request, @AuthenticationPrincipal CurrentUser user) {
    return study.summarize(request, user);
  }

  @PostMapping("/generate-quiz")
  public GenerateQuizResponse generateQuiz(
      @Valid @RequestBody GenerateQuizRequest request, @AuthenticationPrincipal CurrentUser user) {
    return study.generateQuiz(request, user);
  }

  @GetMapping("/usage/me")
  public AiUsageResponse usage(@AuthenticationPrincipal CurrentUser user) {
    return usage.usageFor(user.id());
  }
}
