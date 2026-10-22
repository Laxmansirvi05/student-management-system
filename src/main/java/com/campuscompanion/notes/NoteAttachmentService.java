package com.campuscompanion.notes;

import com.campuscompanion.common.error.ResourceNotFoundException;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.common.storage.StorageClient;
import com.campuscompanion.common.storage.StorageUnavailableException;
import com.campuscompanion.notes.dto.AttachmentListResponse;
import com.campuscompanion.notes.dto.AttachmentResponse;
import com.campuscompanion.student.StudentProfileService;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The images and files inside notes. The bytes are in the private storage bucket under a path the
 * server makes; the browser only ever sees this application's own address for a file, which sends
 * the reader to a five-minute signed link after checking that they may read the note.
 */
@Service
@PreAuthorize("hasRole('STUDENT')")
public class NoteAttachmentService {

  static final String URL_PREFIX = "/api/v1/notes/attachments/";
  static final Duration LINK_LIFETIME = Duration.ofMinutes(5);
  private static final int LIST_LIMIT = 200;
  private static final Pattern REFERENCE =
      Pattern.compile("/api/v1/notes/attachments/(\\d{1,18})/file");
  private static final Logger log = LoggerFactory.getLogger(NoteAttachmentService.class);

  private final NoteAttachmentRepository attachments;
  private final NoteRepository notes;
  private final NoteShareRepository shares;
  private final NoteShareRecipientRepository recipients;
  private final NoteFileChecker checker;
  private final StorageClient storage;
  private final StudentProfileService students;

  public NoteAttachmentService(
      NoteAttachmentRepository attachments,
      NoteRepository notes,
      NoteShareRepository shares,
      NoteShareRecipientRepository recipients,
      NoteFileChecker checker,
      StorageClient storage,
      StudentProfileService students) {
    this.attachments = attachments;
    this.notes = notes;
    this.shares = shares;
    this.recipients = recipients;
    this.checker = checker;
    this.storage = storage;
    this.students = students;
  }

  /**
   * Stores one image or file. The note may not exist yet: the editor can upload before the first
   * save, and the note adopts the attachment as soon as its content refers to it.
   */
  @Transactional
  public AttachmentResponse upload(
      CurrentUser user, byte[] content, String originalName, String contentType, Long noteId) {
    Long studentId = students.currentStudent(user).studentId();
    NoteFileChecker.Prepared prepared = checker.prepare(content, contentType, originalName);
    // A forged note ID must not attach a file to someone else's note.
    Long ownedNote =
        noteId == null
            ? null
            : notes.findByIdAndStudentId(noteId, studentId).map(Note::getId).orElse(null);
    String path = "notes/" + studentId + "/" + UUID.randomUUID() + "." + prepared.extension();
    storage.upload(path, prepared.content(), prepared.mimeType());
    NoteAttachment saved =
        attachments.save(
            new NoteAttachment(
                studentId,
                ownedNote,
                path,
                displayName(originalName, prepared),
                prepared.mimeType(),
                prepared.content().length,
                prepared.kind()));
    return respond(saved, null);
  }

  /** Every attachment of the student, newest first, with the title of the note it is in. */
  @Transactional(readOnly = true)
  public AttachmentListResponse list(CurrentUser user, String kind) {
    Long studentId = students.currentStudent(user).studentId();
    String filter = "image".equals(kind) || "file".equals(kind) ? kind : null;
    List<NoteAttachment> found =
        attachments.forStudent(studentId, filter, PageRequest.of(0, LIST_LIMIT));
    Map<Long, String> titles = new HashMap<>();
    notes
        .findAllById(
            found.stream()
                .map(NoteAttachment::getNoteId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList())
        .forEach(note -> titles.put(note.getId(), note.getTitle()));
    return new AttachmentListResponse(
        found.stream()
            .map(a -> respond(a, a.getNoteId() == null ? null : titles.get(a.getNoteId())))
            .toList());
  }

  /**
   * Removes the stored file and its row. The note's own text is left alone: rewriting a document
   * from a file manager would be a surprising edit, so a removed picture shows as a broken image.
   */
  @Transactional
  public void delete(CurrentUser user, Long id) {
    Long studentId = students.currentStudent(user).studentId();
    NoteAttachment attachment =
        attachments.findByIdAndStudentId(id, studentId).orElseThrow(ResourceNotFoundException::new);
    removeFromStorage(attachment);
    attachments.delete(attachment);
  }

  /**
   * A signed link to read the file, after checking that the student owns it or that the note it is
   * in is shared with them.
   */
  @Transactional(readOnly = true)
  public String linkFor(CurrentUser user, Long id, boolean download) {
    Long studentId = students.currentStudent(user).studentId();
    NoteAttachment attachment =
        attachments.findById(id).orElseThrow(ResourceNotFoundException::new);
    if (!attachment.getStudentId().equals(studentId) && !isSharedWith(attachment, studentId)) {
      throw new ResourceNotFoundException();
    }
    return storage
        .signedUrl(
            attachment.getStoragePath(), LINK_LIFETIME, download ? attachment.getName() : null)
        .url();
  }

  // --- used by NoteService -------------------------------------------------------------------

  /** Attaches the student's loose uploads that the content refers to to the note. */
  void adopt(Long studentId, Long noteId, String content) {
    Matcher match = REFERENCE.matcher(content == null ? "" : content);
    while (match.find()) {
      attachments
          .findByIdAndStudentId(Long.parseLong(match.group(1)), studentId)
          .filter(attachment -> attachment.getNoteId() == null)
          .ifPresent(attachment -> attachment.setNoteId(noteId));
    }
  }

  /**
   * Removes the files of notes that are being destroyed for real. A storage failure is logged but
   * does not stop the delete: an orphaned file costs a little space, whereas refusing would leave
   * the student unable to remove their own note.
   */
  void deleteForNotes(Long studentId, Collection<Long> noteIds) {
    for (Long noteId : noteIds) {
      List<NoteAttachment> rows = attachments.findByNoteIdAndStudentId(noteId, studentId);
      rows.forEach(this::removeFromStorage);
      attachments.deleteAll(rows);
    }
  }

  private void removeFromStorage(NoteAttachment attachment) {
    // A duplicated note has its own rows over the same file: only the last row removes it.
    if (attachments.countByStoragePath(attachment.getStoragePath()) > 1) {
      return;
    }
    try {
      storage.delete(attachment.getStoragePath());
    } catch (StorageUnavailableException unavailable) {
      log.warn("A note attachment could not be removed from storage; the row is removed anyway.");
    }
  }

  /**
   * Gives a copy of a note its own attachment rows over the same stored files, and returns the
   * content with every address changed to the copy's rows.
   */
  String copyAttachments(Long studentId, Long sourceNoteId, Long copyNoteId, String content) {
    String result = content;
    for (NoteAttachment source : attachments.findByNoteIdAndStudentId(sourceNoteId, studentId)) {
      NoteAttachment copy =
          attachments.save(
              new NoteAttachment(
                  studentId,
                  copyNoteId,
                  source.getStoragePath(),
                  source.getName(),
                  source.getMimeType(),
                  source.getSizeBytes(),
                  source.getKind()));
      result =
          result.replace(
              URL_PREFIX + source.getId() + "/file", URL_PREFIX + copy.getId() + "/file");
    }
    return result;
  }

  private boolean isSharedWith(NoteAttachment attachment, Long studentId) {
    Long noteId = attachment.getNoteId();
    if (noteId == null) {
      return false;
    }
    return recipients.findByNoteIdAndRecipientId(noteId, studentId).isPresent()
        || shares
            .findByNoteId(noteId)
            .map(share -> NoteShare.VIEW.equals(share.getLinkAccess()))
            .orElse(false);
  }

  private static AttachmentResponse respond(NoteAttachment a, String noteTitle) {
    return new AttachmentResponse(
        a.getId(),
        a.getNoteId(),
        noteTitle,
        URL_PREFIX + a.getId() + "/file",
        a.getName(),
        a.getMimeType(),
        a.getSizeBytes(),
        a.getKind(),
        a.getCreatedAt());
  }

  /** The name shown for the file. A picture is stored as a JPEG, so its name ends in .jpg. */
  private static String displayName(String originalName, NoteFileChecker.Prepared prepared) {
    String name = safeName(originalName);
    if (!"image".equals(prepared.kind())) {
      return name;
    }
    int dot = name.lastIndexOf('.');
    String stem = dot > 0 ? name.substring(0, dot) : name;
    return stem + ".jpg";
  }

  /** Path separators and control characters out of a name the user chose. */
  static String safeName(String name) {
    String cleaned =
        name == null
            ? ""
            : name.replace('/', '-').replace('\\', '-').replaceAll("[\\p{Cntrl}]", "").strip();
    if (cleaned.length() > 120) {
      cleaned = cleaned.substring(0, 120);
    }
    return cleaned.isEmpty() ? "attachment" : cleaned;
  }
}
