package com.campuscompanion.notes;

import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import com.campuscompanion.common.error.ResourceNotFoundException;
import com.campuscompanion.common.error.ValidationException;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.notes.dto.BulkNoteRequest;
import com.campuscompanion.notes.dto.BulkResult;
import com.campuscompanion.notes.dto.CreateNoteRequest;
import com.campuscompanion.notes.dto.NoteListResponse;
import com.campuscompanion.notes.dto.NoteResponse;
import com.campuscompanion.notes.dto.NoteSaved;
import com.campuscompanion.notes.dto.UpdateNoteRequest;
import com.campuscompanion.student.StudentProfileService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notes as a student uses them: browsing in views and folders, writing, organising, duplicating,
 * the Trash and the sharing of notes with classmates. The student always comes from the session.
 * The content of a note is HTML that is rebuilt by {@link NoteHtmlSanitizer} on every write.
 */
@Service
@PreAuthorize("hasRole('STUDENT')")
public class NoteService {

  static final int TITLE_MAX = 200;
  static final int CONTENT_MAX = 200_000;
  static final int RECENT_LIMIT = 30;
  static final int MAX_BULK = 200;
  static final int SEARCH_MAX = 100;

  private static final Set<String> VIEWS =
      Set.of("all", "recent", "pinned", "shared", "attachments", "trash", "archived");
  private static final Set<String> BULK_ACTIONS =
      Set.of("move", "pin", "unpin", "archive", "unarchive", "trash", "restore", "delete");

  private final NoteRepository notes;
  private final NoteShareRecipientRepository recipients;
  private final NoteFolderService folders;
  private final NoteAttachmentService attachments;
  private final NoteAssembler assembler;
  private final StudentProfileService students;
  private final Clock clock;

  public NoteService(
      NoteRepository notes,
      NoteShareRecipientRepository recipients,
      NoteFolderService folders,
      NoteAttachmentService attachments,
      NoteAssembler assembler,
      StudentProfileService students,
      Clock clock) {
    this.notes = notes;
    this.recipients = recipients;
    this.folders = folders;
    this.attachments = attachments;
    this.assembler = assembler;
    this.students = students;
    this.clock = clock;
  }

  /**
   * The student's notes for one view. Pinned notes come first everywhere except Recent, which is
   * "what did I touch last". {@code folder} is a folder's ID or "unfiled".
   */
  @Transactional(readOnly = true)
  public NoteListResponse list(
      CurrentUser user,
      String view,
      String sort,
      String query,
      String folder,
      String tag,
      boolean attachmentsOnly) {
    Long studentId = students.currentStudent(user).studentId();
    String viewName = view != null && VIEWS.contains(view) ? view : "all";
    String term = searchTerm(query);
    if ("shared".equals(viewName)) {
      return new NoteListResponse(sharedWithMe(studentId, term));
    }

    Specification<Note> filter = NoteSpecifications.inView(studentId, viewName);
    if (folder != null && !folder.isBlank()) {
      filter = filter.and(NoteSpecifications.inFolder(folder));
    }
    if (tag != null && !tag.isBlank()) {
      filter = filter.and(NoteSpecifications.withTag(tag.strip()));
    }
    if (term != null) {
      filter = filter.and(NoteSpecifications.matching(term));
    }
    Sort order = orderFor(sort, "recent".equals(viewName));
    List<Note> found =
        "recent".equals(viewName)
            ? notes.findBy(filter, q -> q.sortBy(order).limit(RECENT_LIMIT).all())
            : notes.findAll(filter, order);
    List<NoteResponse> result = assembler.own(found, studentId);
    // Whether a note holds an image or file is a fact about its document, not a column: it is
    // checked here rather than with a pattern match over every row in SQL.
    if (attachmentsOnly) {
      result =
          result.stream().filter(note -> NoteText.hasAttachmentMarkup(note.content())).toList();
    }
    return new NoteListResponse(result);
  }

  /** One note: the student's own, or one shared with them. */
  @Transactional(readOnly = true)
  public NoteResponse one(CurrentUser user, Long id) {
    Long studentId = students.currentStudent(user).studentId();
    Access access = accessTo(id, studentId);
    if (access.owner()) {
      return assembler.one(access.note(), studentId);
    }
    return assembler.sharedWithMe(List.of(access.note()), Map.of(id, access.permission())).get(0);
  }

  /**
   * Creates a note. It needs a title or some content: an empty draft is refused with a plain error,
   * which is a normal answer to the first keystroke of the quick-note composer. A note from the
   * robot with no folder of its own goes into the Quick Notes folder.
   */
  @Transactional
  public NoteResponse create(CurrentUser user, CreateNoteRequest request) {
    Long studentId = students.currentStudent(user).studentId();
    String title = request.title() == null ? "" : request.title().strip();
    String content = cleanContent(request.content());
    if (title.isEmpty() && !NoteText.hasContent(content)) {
      throw new ValidationException("title", "A note needs a title or content.");
    }
    String source =
        Note.SOURCE_ROBOT.equals(request.source()) ? Note.SOURCE_ROBOT : Note.SOURCE_MANUAL;
    Long folderId = folders.ownedFolderId(studentId, request.folderId());
    if (folderId == null && Note.SOURCE_ROBOT.equals(source)) {
      folderId = folders.quickNotesFolder(studentId);
    }
    Note note = new Note(studentId, source);
    note.setTitle(title);
    note.setContent(content);
    note.setFolderId(folderId);
    note.setTags(cleanTags(request.tags()));
    Note saved = notes.save(note);
    attachments.adopt(studentId, saved.getId(), content);
    return assembler.one(saved, studentId);
  }

  /**
   * Changes a note: text, tags, pin, archive, folder, or a restore from the Trash. One write path
   * serves autosave and every toggle. A shared editor may change the title and content only. When
   * an edit leaves the owner's note with no title and no content, the note is deleted instead of
   * saved: that is the "draft became empty again" case.
   */
  @Transactional
  public NoteSaved update(CurrentUser user, Long id, UpdateNoteRequest request) {
    Long studentId = students.currentStudent(user).studentId();
    Access access = accessTo(id, studentId);
    if (!access.owner() && !NoteShareRecipient.EDIT.equals(access.permission())) {
      throw new ResourceNotFoundException();
    }
    Note note = access.note();
    boolean changed = false;
    boolean touchesText = false;

    if (request.title() != null) {
      note.setTitle(request.title().strip());
      changed = true;
      touchesText = true;
    }
    if (request.content() != null) {
      String content = cleanContent(request.content());
      note.setContent(content);
      changed = true;
      touchesText = true;
    }
    if (access.owner()) {
      if (request.tags() != null) {
        note.setTags(cleanTags(request.tags()));
        changed = true;
      }
      if (request.pinned() != null) {
        note.setPinned(request.pinned());
        changed = true;
      }
      if (request.archived() != null) {
        note.setArchived(request.archived());
        changed = true;
      }
      if (Boolean.TRUE.equals(request.restore())) {
        note.setDeletedAt(null);
        changed = true;
      }
      if (Boolean.TRUE.equals(request.unfile())) {
        note.setFolderId(null);
        changed = true;
      } else if (request.folderId() != null) {
        // A folder that is not the student's means "no folder", never someone else's folder.
        note.setFolderId(folders.ownedFolderId(studentId, request.folderId()));
        changed = true;
      }
    }
    if (!changed) {
      throw new ValidationException("title", "Nothing to update.");
    }

    if (touchesText && note.getTitle().isBlank() && !NoteText.hasContent(note.getContent())) {
      // Only the owner can empty a note out of existence.
      if (!access.owner()) {
        throw new ApiException(ErrorCode.ACCESS_DENIED, "A shared note cannot be emptied.");
      }
      attachments.deleteForNotes(List.of(id));
      notes.delete(note);
      return NoteSaved.removed();
    }

    note.touch(clock.instant());
    Note saved = notes.save(note);
    // The writer's own uploads join the note, for a shared editor as for the owner, so that
    // everyone who can read the note can open its pictures.
    attachments.adopt(studentId, id, saved.getContent());
    if (access.owner()) {
      return NoteSaved.of(assembler.one(saved, studentId));
    }
    return NoteSaved.of(
        assembler.sharedWithMe(List.of(saved), Map.of(id, NoteShareRecipient.EDIT)).get(0));
  }

  /**
   * Moves a note to the Trash. Only {@code permanent} destroys it, together with its files, and
   * that is never the default, so a wrong click costs a restore rather than the note.
   */
  @Transactional
  public void delete(CurrentUser user, Long id, boolean permanent) {
    Long studentId = students.currentStudent(user).studentId();
    Note note =
        notes.findByIdAndStudentId(id, studentId).orElseThrow(ResourceNotFoundException::new);
    if (permanent) {
      attachments.deleteForNotes(List.of(id));
      notes.delete(note);
      return;
    }
    note.setDeletedAt(clock.instant());
    note.touch(clock.instant());
  }

  /**
   * Copies a note into the same folder. Content, tags and folder carry over; pinned, archived and
   * sharing do not: a copy is a fresh draft, not a second copy of someone's sharing decisions.
   */
  @Transactional
  public NoteResponse duplicate(CurrentUser user, Long id) {
    Long studentId = students.currentStudent(user).studentId();
    Note source =
        notes
            .findByIdAndStudentId(id, studentId)
            .filter(note -> note.getDeletedAt() == null)
            .orElseThrow(ResourceNotFoundException::new);
    String base = source.getTitle().isBlank() ? "Untitled" : source.getTitle();
    String title = (base + " copy");
    Note copy = new Note(studentId, source.getSource());
    copy.setTitle(title.length() > TITLE_MAX ? title.substring(0, TITLE_MAX) : title);
    copy.setContent(source.getContent());
    copy.setTags(source.getTags());
    copy.setFolderId(source.getFolderId());
    Note saved = notes.save(copy);
    saved.setContent(
        attachments.copyAttachments(studentId, id, saved.getId(), source.getContent()));
    return assembler.one(saved, studentId);
  }

  /**
   * One action on many notes. Every statement is restricted to the student's own notes, so an ID of
   * someone else's in the list simply matches nothing.
   */
  @Transactional
  public BulkResult bulk(CurrentUser user, BulkNoteRequest request) {
    Long studentId = students.currentStudent(user).studentId();
    if ("empty_trash".equals(request.action())) {
      List<Note> trashed = notes.findByStudentIdAndDeletedAtIsNotNull(studentId);
      destroy(studentId, trashed);
      return new BulkResult(true, trashed.size());
    }
    String action = request.action();
    if (!BULK_ACTIONS.contains(action)) {
      throw new ValidationException("action", "Unknown action.");
    }
    List<Long> ids =
        request.ids() == null ? List.of() : request.ids().stream().limit(MAX_BULK).toList();
    if (ids.isEmpty()) {
      throw new ValidationException("ids", "No notes selected.");
    }
    List<Note> selected = notes.findByIdInAndStudentId(ids, studentId);
    if ("delete".equals(action)) {
      destroy(studentId, selected);
      return new BulkResult(true, selected.size());
    }
    Instant now = clock.instant();
    Long folderId =
        "move".equals(action) ? folders.ownedFolderId(studentId, request.folderId()) : null;
    for (Note note : selected) {
      switch (action) {
        case "move" -> note.setFolderId(folderId);
        case "pin" -> note.setPinned(true);
        case "unpin" -> note.setPinned(false);
        case "archive" -> note.setArchived(true);
        case "unarchive" -> note.setArchived(false);
        case "trash" -> note.setDeletedAt(now);
        default -> note.setDeletedAt(null);
      }
      note.touch(now);
    }
    return new BulkResult(true, selected.size());
  }

  // --- helpers -------------------------------------------------------------------------------

  private void destroy(Long studentId, List<Note> toDestroy) {
    if (toDestroy.isEmpty()) {
      return;
    }
    // The files first: once the rows are gone their storage paths are gone with them.
    attachments.deleteForNotes(toDestroy.stream().map(Note::getId).toList());
    notes.deleteAll(toDestroy);
  }

  private List<NoteResponse> sharedWithMe(Long studentId, String term) {
    Map<Long, String> permissions = new HashMap<>();
    recipients
        .findByRecipientId(studentId)
        .forEach(recipient -> permissions.put(recipient.getNoteId(), recipient.getPermission()));
    if (permissions.isEmpty()) {
      return List.of();
    }
    List<Note> found = new ArrayList<>(notes.findByIdInAndDeletedAtIsNull(permissions.keySet()));
    if (term != null) {
      String needle = term.toLowerCase(Locale.ROOT);
      found.removeIf(
          note ->
              !note.getTitle().toLowerCase(Locale.ROOT).contains(needle)
                  && !note.getContent().toLowerCase(Locale.ROOT).contains(needle));
    }
    found.sort((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()));
    return assembler.sharedWithMe(found, permissions);
  }

  private record Access(Note note, boolean owner, String permission) {}

  /** The note if it is the student's, or if it was shared with them. Otherwise it is not found. */
  private Access accessTo(Long noteId, Long studentId) {
    var own = notes.findByIdAndStudentId(noteId, studentId);
    if (own.isPresent()) {
      return new Access(own.get(), true, null);
    }
    NoteShareRecipient recipient =
        recipients
            .findByNoteIdAndRecipientId(noteId, studentId)
            .orElseThrow(ResourceNotFoundException::new);
    Note note = notes.findById(noteId).orElseThrow(ResourceNotFoundException::new);
    return new Access(note, false, recipient.getPermission());
  }

  /**
   * Makes the HTML safe. A note that is too long is refused rather than cut short: silently
   * dropping the end of someone's writing is worse than saying that it cannot be saved.
   */
  static String cleanContent(String raw) {
    if (raw == null) {
      return "";
    }
    if (raw.length() > CONTENT_MAX) {
      throw new ValidationException("content", "This note is too long to save.");
    }
    return NoteHtmlSanitizer.sanitize(raw);
  }

  static List<String> cleanTags(List<String> tags) {
    if (tags == null) {
      return List.of();
    }
    Set<String> clean = new LinkedHashSet<>();
    for (String tag : tags) {
      if (tag != null && !tag.isBlank() && clean.size() < 20) {
        clean.add(tag.strip());
      }
    }
    return List.copyOf(clean);
  }

  private static String searchTerm(String query) {
    if (query == null) {
      return null;
    }
    String term = query.replaceAll("[\\p{Cntrl}]", " ").strip();
    if (term.length() > SEARCH_MAX) {
      term = term.substring(0, SEARCH_MAX);
    }
    return term.isEmpty() ? null : term;
  }

  private static Sort orderFor(String sort, boolean recent) {
    Sort order =
        switch (sort == null ? "updated" : sort) {
          case "alphabetical" ->
              Sort.by(Sort.Order.asc("title").ignoreCase(), Sort.Order.desc("id"));
          case "created" -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
          default -> Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));
        };
    return recent ? order : Sort.by(Sort.Order.desc("pinned")).and(order);
  }
}
