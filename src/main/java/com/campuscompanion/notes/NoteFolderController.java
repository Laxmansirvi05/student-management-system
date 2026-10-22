package com.campuscompanion.notes;

import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.notes.dto.FolderListResponse;
import com.campuscompanion.notes.dto.FolderRequest;
import com.campuscompanion.notes.dto.FolderResponse;
import com.campuscompanion.notes.dto.ReorderFoldersRequest;
import com.campuscompanion.notes.dto.UpdateFolderRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Students: the folders of their notes. Contract: docs/09-api-contract.md section 8.20. */
@RestController
@RequestMapping("/api/v1/notes/folders")
public class NoteFolderController {

  private final NoteFolderService folders;

  public NoteFolderController(NoteFolderService folders) {
    this.folders = folders;
  }

  @GetMapping
  public FolderListResponse list(@AuthenticationPrincipal CurrentUser user) {
    return folders.list(user);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public FolderResponse create(
      @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody FolderRequest request) {
    return folders.create(user, request);
  }

  /** The new order of the folders after a drag. */
  @PatchMapping
  public ResponseEntity<Void> reorder(
      @AuthenticationPrincipal CurrentUser user,
      @Valid @RequestBody ReorderFoldersRequest request) {
    folders.reorder(user, request);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/{id}")
  public FolderResponse update(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable Long id,
      @Valid @RequestBody UpdateFolderRequest request) {
    return folders.update(user, id, request);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
    folders.delete(user, id);
    return ResponseEntity.noContent().build();
  }
}
