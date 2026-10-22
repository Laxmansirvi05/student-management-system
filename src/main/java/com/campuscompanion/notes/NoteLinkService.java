package com.campuscompanion.notes;

import com.campuscompanion.common.error.ResourceNotFoundException;
import com.campuscompanion.common.error.ValidationException;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.notes.dto.AddLinkRequest;
import com.campuscompanion.notes.dto.LinkTargetResponse;
import com.campuscompanion.notes.dto.LinkTargetsResponse;
import com.campuscompanion.notes.dto.NoteLinkResponse;
import com.campuscompanion.notes.dto.NoteLinksResponse;
import com.campuscompanion.resource.ResourceService;
import com.campuscompanion.resource.dto.ResourceResponse;
import com.campuscompanion.student.StudentProfileService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Links from a note to another note, a folder or a learning resource. A link stores an ID, never a
 * copy, so the target stays the one source of truth. A target that was deleted since resolves to a
 * null label rather than vanishing, so the student can see that the link existed.
 */
@Service
@PreAuthorize("hasRole('STUDENT')")
public class NoteLinkService {

  static final int PER_TYPE_LIMIT = 5;
  private static final Set<String> TYPES = Set.of("note", "folder", "resource");

  private final NoteLinkRepository links;
  private final NoteRepository notes;
  private final NoteFolderService folders;
  private final ResourceService resources;
  private final StudentProfileService students;

  public NoteLinkService(
      NoteLinkRepository links,
      NoteRepository notes,
      NoteFolderService folders,
      ResourceService resources,
      StudentProfileService students) {
    this.links = links;
    this.notes = notes;
    this.folders = folders;
    this.resources = resources;
    this.students = students;
  }

  /** The outgoing links of a note, resolved for display. */
  @Transactional(readOnly = true)
  public NoteLinksResponse list(CurrentUser user, Long noteId) {
    Long studentId = students.currentStudent(user).studentId();
    return read(user, studentId, noteId);
  }

  @Transactional
  public NoteLinksResponse add(CurrentUser user, Long noteId, AddLinkRequest request) {
    Long studentId = students.currentStudent(user).studentId();
    notes.findByIdAndStudentId(noteId, studentId).orElseThrow(ResourceNotFoundException::new);
    String type = request.targetType();
    if (!TYPES.contains(type)) {
      throw new ValidationException("targetType", "Invalid link target.");
    }
    if ("note".equals(type) && request.targetId().equals(noteId)) {
      throw new ValidationException("targetId", "A note cannot link to itself.");
    }
    // The target must be one the student may see, or a title of someone else's would leak.
    if (!targetExists(user, studentId, type, request.targetId())) {
      throw new ResourceNotFoundException();
    }
    if (links
        .findBySourceNoteIdAndTargetTypeAndTargetId(noteId, type, request.targetId())
        .isEmpty()) {
      links.save(new NoteLink(studentId, noteId, type, request.targetId()));
    }
    return read(user, studentId, noteId);
  }

  @Transactional
  public NoteLinksResponse remove(CurrentUser user, Long noteId, Long linkId) {
    Long studentId = students.currentStudent(user).studentId();
    links.findByIdAndSourceNoteIdAndStudentId(linkId, noteId, studentId).ifPresent(links::delete);
    return read(user, studentId, noteId);
  }

  /** The best few matches of each kind for the picker, from two characters of search. */
  @Transactional(readOnly = true)
  public LinkTargetsResponse targets(CurrentUser user, String query, Long exclude) {
    Long studentId = students.currentStudent(user).studentId();
    String term = query == null ? "" : query.strip();
    if (term.length() < 2) {
      return new LinkTargetsResponse(List.of());
    }
    List<LinkTargetResponse> targets = new ArrayList<>();
    var noteFilter =
        NoteSpecifications.inView(studentId, "all").and(NoteSpecifications.titleMatching(term));
    List<Note> found =
        notes.findBy(
            noteFilter,
            q -> q.sortBy(Sort.by(Sort.Order.desc("updatedAt"))).limit(PER_TYPE_LIMIT + 1).all());
    found.stream()
        .filter(note -> !note.getId().equals(exclude))
        .limit(PER_TYPE_LIMIT)
        .forEach(
            note ->
                targets.add(
                    new LinkTargetResponse(
                        "note",
                        note.getId(),
                        note.getTitle().isBlank() ? "Untitled" : note.getTitle(),
                        "Note")));
    String needle = term.toLowerCase(Locale.ROOT);
    folders.foldersById(studentId).values().stream()
        .filter(folder -> folder.getName().toLowerCase(Locale.ROOT).contains(needle))
        .limit(PER_TYPE_LIMIT)
        .forEach(
            folder ->
                targets.add(
                    new LinkTargetResponse("folder", folder.getId(), folder.getName(), "Folder")));
    resources
        .search(user, null, null, null, null, term, "newest", 0, PER_TYPE_LIMIT)
        .content()
        .forEach(
            resource ->
                targets.add(
                    new LinkTargetResponse(
                        "resource", resource.id(), resource.title(), resource.subjectName())));
    return new LinkTargetsResponse(targets);
  }

  // --- helpers -------------------------------------------------------------------------------

  private boolean targetExists(CurrentUser user, Long studentId, String type, Long targetId) {
    return switch (type) {
      case "note" -> notes.findByIdAndStudentId(targetId, studentId).isPresent();
      case "folder" -> folders.ownedFolderId(studentId, targetId) != null;
      default -> resourceOrNull(user, targetId) != null;
    };
  }

  private ResourceResponse resourceOrNull(CurrentUser user, Long id) {
    try {
      return resources.get(user, id);
    } catch (ResourceNotFoundException missing) {
      return null;
    }
  }

  private NoteLinksResponse read(CurrentUser user, Long studentId, Long noteId) {
    List<NoteLink> rows = links.findBySourceNoteIdAndStudentIdOrderByIdAsc(noteId, studentId);
    Map<Long, Note> noteTargets = new java.util.HashMap<>();
    notes
        .findAllById(
            rows.stream()
                .filter(l -> l.getTargetType().equals("note"))
                .map(NoteLink::getTargetId)
                .toList())
        .stream()
        .filter(note -> note.getStudentId().equals(studentId))
        .forEach(note -> noteTargets.put(note.getId(), note));
    Map<Long, NoteFolder> folderTargets = folders.foldersById(studentId);
    List<NoteLinkResponse> result = new ArrayList<>();
    for (NoteLink link : rows) {
      LinkTargetResponse target =
          switch (link.getTargetType()) {
            case "note" -> {
              Note note = noteTargets.get(link.getTargetId());
              yield new LinkTargetResponse(
                  "note",
                  link.getTargetId(),
                  note == null ? null : (note.getTitle().isBlank() ? "Untitled" : note.getTitle()),
                  null);
            }
            case "folder" -> {
              NoteFolder folder = folderTargets.get(link.getTargetId());
              yield new LinkTargetResponse(
                  "folder",
                  link.getTargetId(),
                  folder == null ? null : folder.getName(),
                  folder == null ? null : "Folder");
            }
            default -> {
              ResourceResponse resource = resourceOrNull(user, link.getTargetId());
              yield new LinkTargetResponse(
                  "resource",
                  link.getTargetId(),
                  resource == null ? null : resource.title(),
                  resource == null ? null : resource.subjectName());
            }
          };
      result.add(new NoteLinkResponse(link.getId(), target));
    }
    return new NoteLinksResponse(result);
  }
}
