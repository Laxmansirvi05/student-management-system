package com.campuscompanion.notes;

import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.notes.dto.AddLinkRequest;
import com.campuscompanion.notes.dto.BulkNoteRequest;
import com.campuscompanion.notes.dto.BulkResult;
import com.campuscompanion.notes.dto.CreateNoteRequest;
import com.campuscompanion.notes.dto.LinkTargetsResponse;
import com.campuscompanion.notes.dto.NoteLinksResponse;
import com.campuscompanion.notes.dto.NoteListResponse;
import com.campuscompanion.notes.dto.NoteResponse;
import com.campuscompanion.notes.dto.NoteSaved;
import com.campuscompanion.notes.dto.UpdateNoteRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Students: notes, and the links between notes. Contract: docs/09-api-contract.md section 8.20. */
@RestController
@Validated
@RequestMapping("/api/v1/notes")
public class NoteController {

  private final NoteService notes;
  private final NoteLinkService links;

  public NoteController(NoteService notes, NoteLinkService links) {
    this.notes = notes;
    this.links = links;
  }

  @GetMapping
  public NoteListResponse list(
      @AuthenticationPrincipal CurrentUser user,
      @RequestParam(defaultValue = "all") String view,
      @RequestParam(defaultValue = "updated") String sort,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String folder,
      @RequestParam(required = false) String tag,
      @RequestParam(defaultValue = "false") boolean attachments) {
    return notes.list(user, view, sort, q, folder, tag, attachments);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public NoteResponse create(
      @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody CreateNoteRequest request) {
    return notes.create(user, request);
  }

  @PostMapping("/bulk")
  public BulkResult bulk(
      @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody BulkNoteRequest request) {
    return notes.bulk(user, request);
  }

  @GetMapping("/link-targets")
  public LinkTargetsResponse linkTargets(
      @AuthenticationPrincipal CurrentUser user,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(required = false) Long exclude) {
    return links.targets(user, q, exclude);
  }

  @GetMapping("/{id}")
  public NoteResponse one(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
    return notes.one(user, id);
  }

  @PatchMapping("/{id}")
  public NoteSaved update(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable Long id,
      @Valid @RequestBody UpdateNoteRequest request) {
    return notes.update(user, id, request);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable Long id,
      @RequestParam(defaultValue = "false") boolean permanent) {
    notes.delete(user, id, permanent);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/duplicate")
  @ResponseStatus(HttpStatus.CREATED)
  public NoteResponse duplicate(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
    return notes.duplicate(user, id);
  }

  @GetMapping("/{id}/links")
  public NoteLinksResponse links(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
    return links.list(user, id);
  }

  @PostMapping("/{id}/links")
  public NoteLinksResponse addLink(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable Long id,
      @Valid @RequestBody AddLinkRequest request) {
    return links.add(user, id, request);
  }

  @DeleteMapping("/{id}/links/{linkId}")
  public NoteLinksResponse removeLink(
      @AuthenticationPrincipal CurrentUser user, @PathVariable Long id, @PathVariable Long linkId) {
    return links.remove(user, id, linkId);
  }
}
