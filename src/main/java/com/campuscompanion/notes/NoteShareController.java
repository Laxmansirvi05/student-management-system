package com.campuscompanion.notes;

import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.notes.dto.AddRecipientRequest;
import com.campuscompanion.notes.dto.LinkAccessRequest;
import com.campuscompanion.notes.dto.NoteResponse;
import com.campuscompanion.notes.dto.ShareResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Students: sharing a note with classmates. Contract: docs/09-api-contract.md section 8.20. */
@RestController
@RequestMapping("/api/v1/notes")
public class NoteShareController {

  private final NoteShareService shares;

  public NoteShareController(NoteShareService shares) {
    this.shares = shares;
  }

  @GetMapping("/{id}/share")
  public ShareResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
    return shares.get(user, id);
  }

  @PutMapping("/{id}/share")
  public ShareResponse setLinkAccess(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable Long id,
      @Valid @RequestBody LinkAccessRequest request) {
    return shares.setLinkAccess(user, id, request.linkAccess());
  }

  @PostMapping("/{id}/share/recipients")
  public ShareResponse addRecipient(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable Long id,
      @Valid @RequestBody AddRecipientRequest request) {
    return shares.addRecipient(user, id, request.identifier(), request.permission());
  }

  @DeleteMapping("/{id}/share/recipients/{recipientId}")
  public ShareResponse removeRecipient(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable Long id,
      @PathVariable Long recipientId) {
    return shares.removeRecipient(user, id, recipientId);
  }

  /** Stops all sharing of the note. */
  @DeleteMapping("/{id}/share")
  public ShareResponse stop(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
    return shares.stop(user, id);
  }

  /** Opens a note through its share link. */
  @GetMapping("/shared/{slug}")
  public NoteResponse openByLink(
      @AuthenticationPrincipal CurrentUser user, @PathVariable String slug) {
    return shares.openByLink(user, slug);
  }
}
