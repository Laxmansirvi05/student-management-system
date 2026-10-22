package com.campuscompanion.notes;

import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.notes.dto.AttachmentListResponse;
import com.campuscompanion.notes.dto.AttachmentResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Students: the images and files inside their notes. Contract: docs/09-api-contract.md 8.20. */
@RestController
@RequestMapping("/api/v1/notes/attachments")
public class NoteAttachmentController {

  private final NoteAttachmentService attachments;

  public NoteAttachmentController(NoteAttachmentService attachments) {
    this.attachments = attachments;
  }

  @GetMapping
  public AttachmentListResponse list(
      @AuthenticationPrincipal CurrentUser user, @RequestParam(required = false) String kind) {
    return attachments.list(user, kind);
  }

  /** One part "file": an image, PDF, document, spreadsheet or text file of at most 10 MB. */
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public AttachmentResponse upload(
      @AuthenticationPrincipal CurrentUser user,
      @RequestPart("file") MultipartFile file,
      @RequestParam(required = false) Long noteId)
      throws IOException {
    if (file.getSize() > NoteFileChecker.MAX_BYTES) {
      // Refused by its size alone, before the file is read into memory.
      throw new ApiException(ErrorCode.UPLOAD_TOO_LARGE, "Attachments must be under 10 MB.");
    }
    return attachments.upload(
        user, file.getBytes(), file.getOriginalFilename(), file.getContentType(), noteId);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
    attachments.delete(user, id);
    return ResponseEntity.noContent().build();
  }

  /**
   * Sends the reader on to a short-lived signed link for the file. The address is the one written
   * into the notes, so it keeps working for as long as the note can be read.
   */
  @GetMapping("/{id}/file")
  public ResponseEntity<Void> file(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable Long id,
      @RequestParam(defaultValue = "false") boolean download) {
    String link = attachments.linkFor(user, id, download);
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(link))
        // Shorter than the link's own life, so a cached redirect never points at an expired link.
        .header(
            HttpHeaders.CACHE_CONTROL,
            CacheControl.maxAge(java.time.Duration.ofMinutes(3)).cachePrivate().getHeaderValue())
        .build();
  }
}
