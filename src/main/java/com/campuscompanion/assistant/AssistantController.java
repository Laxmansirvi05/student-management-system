package com.campuscompanion.assistant;

import com.campuscompanion.assistant.dto.ChatRequest;
import com.campuscompanion.assistant.dto.ChatResponse;
import com.campuscompanion.assistant.dto.ConversationListResponse;
import com.campuscompanion.assistant.dto.MessageListResponse;
import com.campuscompanion.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Students: the robot's Quick Assistant. Contract: docs/09-api-contract.md section 9.5. */
@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {

  private final AssistantService assistant;

  public AssistantController(AssistantService assistant) {
    this.assistant = assistant;
  }

  @PostMapping("/chat")
  public ChatResponse chat(
      @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody ChatRequest request) {
    return assistant.chat(user, request);
  }

  @GetMapping("/conversations")
  public ConversationListResponse conversations(
      @AuthenticationPrincipal CurrentUser user,
      @RequestParam(defaultValue = "quick") String source) {
    return assistant.conversations(user, source);
  }

  @GetMapping("/conversations/{id}/messages")
  public MessageListResponse messages(
      @AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
    return assistant.messages(user, id);
  }

  @DeleteMapping("/conversations/{id}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
    assistant.delete(user, id);
    return ResponseEntity.noContent().build();
  }
}
