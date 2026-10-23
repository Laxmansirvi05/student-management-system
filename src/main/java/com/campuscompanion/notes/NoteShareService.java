package com.campuscompanion.notes;

import com.campuscompanion.common.config.IdentityProperties;
import com.campuscompanion.common.error.ResourceNotFoundException;
import com.campuscompanion.common.error.ValidationException;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.notes.dto.NoteResponse;
import com.campuscompanion.notes.dto.ShareRecipientResponse;
import com.campuscompanion.notes.dto.ShareResponse;
import com.campuscompanion.student.StudentContext;
import com.campuscompanion.student.StudentProfileService;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sharing a note with other students (ADR 0014). There are two ways, both for signed-in students
 * only: a link that any student who has it can open to read the note, and named classmates who can
 * read or also edit it. There is no public, signed-out access and no link that edits, because an
 * edit with no known author could not be attributed.
 */
@Service
@PreAuthorize("hasRole('STUDENT')")
public class NoteShareService {

  private final NoteShareRepository shares;
  private final NoteShareRecipientRepository recipients;
  private final NoteRepository notes;
  private final NoteAssembler assembler;
  private final StudentProfileService students;
  private final IdentityProperties identity;
  private final Clock clock;

  public NoteShareService(
      NoteShareRepository shares,
      NoteShareRecipientRepository recipients,
      NoteRepository notes,
      NoteAssembler assembler,
      StudentProfileService students,
      IdentityProperties identity,
      Clock clock) {
    this.shares = shares;
    this.recipients = recipients;
    this.notes = notes;
    this.assembler = assembler;
    this.students = students;
    this.identity = identity;
    this.clock = clock;
  }

  /** The current sharing state, or {@code share: null} when the note was never shared. */
  @Transactional(readOnly = true)
  public ShareResponse get(CurrentUser user, Long noteId) {
    Long studentId = students.currentStudent(user).studentId();
    owned(noteId, studentId);
    return read(noteId);
  }

  /**
   * Sets who may open the link. Turning sharing off and on again keeps the same address on purpose:
   * a link the owner already sent should work again when sharing is switched back on.
   */
  @Transactional
  public ShareResponse setLinkAccess(CurrentUser user, Long noteId, String linkAccess) {
    Long studentId = students.currentStudent(user).studentId();
    owned(noteId, studentId);
    if (!NoteShare.PRIVATE.equals(linkAccess) && !NoteShare.VIEW.equals(linkAccess)) {
      throw new ValidationException("linkAccess", "Unknown link access level.");
    }
    NoteShare share = shares.findByNoteId(noteId).orElse(null);
    if (share == null) {
      shares.save(
          new NoteShare(
              noteId, studentId, UUID.randomUUID().toString().replace("-", ""), linkAccess));
    } else {
      share.setLinkAccess(linkAccess, clock.instant());
    }
    return read(noteId);
  }

  /** Shares the note with a classmate named by roll number or college email. */
  @Transactional
  public ShareResponse addRecipient(
      CurrentUser user, Long noteId, String identifier, String permission) {
    Long studentId = students.currentStudent(user).studentId();
    owned(noteId, studentId);
    StudentContext recipient =
        students
            .findByRollNo(rollNoOf(identifier))
            .orElseThrow(
                () ->
                    new ValidationException(
                        "identifier", "No student uses that roll number or email."));
    if (recipient.studentId().equals(studentId)) {
      throw new ValidationException("identifier", "You already have access to this note.");
    }
    String level =
        NoteShareRecipient.EDIT.equals(permission)
            ? NoteShareRecipient.EDIT
            : NoteShareRecipient.VIEW;
    NoteShareRecipient existing =
        recipients.findByNoteIdAndRecipientId(noteId, recipient.studentId()).orElse(null);
    if (existing == null) {
      recipients.save(new NoteShareRecipient(noteId, studentId, recipient.studentId(), level));
    } else {
      existing.setPermission(level);
    }
    return read(noteId);
  }

  @Transactional
  public ShareResponse removeRecipient(CurrentUser user, Long noteId, Long recipientId) {
    Long studentId = students.currentStudent(user).studentId();
    owned(noteId, studentId);
    recipients.findByNoteIdAndRecipientId(noteId, recipientId).ifPresent(recipients::delete);
    return read(noteId);
  }

  /** Stops all sharing: the link goes private and every named classmate loses access. */
  @Transactional
  public ShareResponse stop(CurrentUser user, Long noteId) {
    Long studentId = students.currentStudent(user).studentId();
    owned(noteId, studentId);
    recipients.deleteByNoteId(noteId);
    shares
        .findByNoteId(noteId)
        .ifPresent(share -> share.setLinkAccess(NoteShare.PRIVATE, clock.instant()));
    return read(noteId);
  }

  /**
   * A note opened through its share link: read-only, for any signed-in student, and only while the
   * link is switched on. A link that is off, unknown or whose note is in the Trash is not found.
   */
  @Transactional(readOnly = true)
  public NoteResponse openByLink(CurrentUser user, String slug) {
    students.currentStudent(user);
    NoteShare share = shares.findBySlug(slug).orElseThrow(ResourceNotFoundException::new);
    if (!NoteShare.VIEW.equals(share.getLinkAccess())) {
      throw new ResourceNotFoundException();
    }
    Note note =
        notes
            .findById(share.getNoteId())
            .filter(n -> n.getDeletedAt() == null)
            .orElseThrow(ResourceNotFoundException::new);
    return assembler
        .sharedWithMe(List.of(note), Map.of(note.getId(), NoteShareRecipient.VIEW))
        .get(0);
  }

  // --- helpers -------------------------------------------------------------------------------

  private Note owned(Long noteId, Long studentId) {
    return notes
        .findByIdAndStudentId(noteId, studentId)
        .orElseThrow(ResourceNotFoundException::new);
  }

  /** A roll number from a roll number or from a college email address. */
  private String rollNoOf(String identifier) {
    String value = identifier == null ? "" : identifier.strip().toLowerCase(Locale.ROOT);
    int at = value.indexOf('@');
    if (at >= 0) {
      if (!value.substring(at + 1).equals(identity.studentEmailDomain())) {
        return "";
      }
      value = value.substring(0, at);
    }
    return value.toUpperCase(Locale.ROOT);
  }

  /**
   * The sharing state as the owner sees it. A classmate is listed by roll number only, which the
   * owner typed: returning their name would let any student look up the name behind any roll number
   * by "sharing" with it.
   */
  private ShareResponse read(Long noteId) {
    NoteShare share = shares.findByNoteId(noteId).orElse(null);
    List<NoteShareRecipient> rows = recipients.findByNoteIdOrderByIdAsc(noteId);
    if (share == null && rows.isEmpty()) {
      return new ShareResponse(null);
    }
    Map<Long, StudentContext> people =
        students.studentsByIds(rows.stream().map(NoteShareRecipient::getRecipientId).toList());
    List<ShareRecipientResponse> listed =
        rows.stream()
            .map(
                row -> {
                  StudentContext person = people.get(row.getRecipientId());
                  return new ShareRecipientResponse(
                      row.getId(),
                      row.getRecipientId(),
                      person == null ? null : person.rollNo(),
                      row.getPermission());
                })
            .toList();
    return new ShareResponse(
        new ShareResponse.ShareState(
            share == null ? "" : share.getSlug(),
            share == null ? NoteShare.PRIVATE : share.getLinkAccess(),
            listed));
  }
}
