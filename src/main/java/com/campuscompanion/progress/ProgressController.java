package com.campuscompanion.progress;

import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.progress.dto.ProgressResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A student's own progress. Contract: docs/openapi.yaml, /progress/me. */
@RestController
@RequestMapping("/api/v1/progress")
public class ProgressController {

  private final ProgressService progress;

  public ProgressController(ProgressService progress) {
    this.progress = progress;
  }

  @GetMapping("/me")
  public ProgressResponse mine(@AuthenticationPrincipal CurrentUser currentUser) {
    return progress.progressFor(currentUser);
  }
}
