package com.campuscompanion.notes;

import com.campuscompanion.common.error.ConflictException;
import com.campuscompanion.common.error.ResourceNotFoundException;
import com.campuscompanion.common.error.ValidationException;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.notes.dto.FolderListResponse;
import com.campuscompanion.notes.dto.FolderRequest;
import com.campuscompanion.notes.dto.FolderResponse;
import com.campuscompanion.notes.dto.NotePreview;
import com.campuscompanion.notes.dto.ReorderFoldersRequest;
import com.campuscompanion.notes.dto.UpdateFolderRequest;
import com.campuscompanion.student.StudentProfileService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The folders of a student's notes: listing with exact counts and previews, creating, changing,
 * ordering and deleting. A folder that is deleted never takes its notes with it: they become
 * unfiled. Every query carries the student, so a forged ID reaches nothing of anyone else's.
 */
@Service
@PreAuthorize("hasRole('STUDENT')")
public class NoteFolderService {

  /** The folder that notes captured from the robot are filed into. */
  public static final String QUICK_NOTES = "Quick Notes";

  static final Set<String> COLORS =
      Set.of(
          "blue", "cyan", "purple", "indigo", "green", "yellow", "orange", "red", "pink", "teal",
          "neutral");

  private static final int PREVIEWS_PER_FOLDER = 5;

  /**
   * How many recent notes are scanned to build the previews. Bounded on purpose: previews are
   * decoration on a folder card, and a student with 2,000 notes should not pay for all of them. The
   * note count beside a folder is still exact, because it comes from SQL.
   */
  private static final int PREVIEW_SCAN_LIMIT = 300;

  private record Default(String name, String color, String icon) {}

  private static final List<Default> DEFAULTS =
      List.of(
          new Default(QUICK_NOTES, "cyan", "bolt"),
          new Default("Lectures", "purple", "school"),
          new Default("Assignments", "green", "menu_book"),
          new Default("General", "neutral", "folder"));

  private final NoteFolderRepository folders;
  private final NoteRepository notes;
  private final StudentProfileService students;
  private final Clock clock;

  public NoteFolderService(
      NoteFolderRepository folders,
      NoteRepository notes,
      StudentProfileService students,
      Clock clock) {
    this.folders = folders;
    this.notes = notes;
    this.students = students;
    this.clock = clock;
  }

  /** The folders, with an exact note count and up to five previews each. */
  @Transactional
  public FolderListResponse list(CurrentUser user) {
    Long studentId = students.currentStudent(user).studentId();
    List<NoteFolder> all = folders.findByStudentIdOrderByPositionAscIdAsc(studentId);
    if (all.isEmpty()) {
      all = seedDefaults(studentId);
    }
    Map<Long, Integer> counts = new HashMap<>();
    for (Object[] row : notes.countsPerFolder(studentId)) {
      counts.put((Long) row[0], ((Number) row[1]).intValue());
    }
    Map<Long, List<NotePreview>> previews = previews(studentId);
    List<FolderResponse> result = new ArrayList<>();
    for (NoteFolder folder : all) {
      result.add(
          respond(
              folder,
              counts.getOrDefault(folder.getId(), 0),
              previews.getOrDefault(folder.getId(), List.of())));
    }
    return new FolderListResponse(result);
  }

  @Transactional
  public FolderResponse create(CurrentUser user, FolderRequest request) {
    Long studentId = students.currentStudent(user).studentId();
    String name = request.name().strip();
    if (name.isEmpty()) {
      throw new ValidationException("name", "A folder needs a name.");
    }
    if (folders.findByStudentIdAndName(studentId, name).isPresent()) {
      throw new ConflictException("A folder with that name already exists.");
    }
    Long parentId = ownedFolderId(studentId, request.parentId());
    // A new folder lands at the end of the list rather than jumping to the top.
    int position = folders.highestPosition(studentId).map(last -> last + 1).orElse(0);
    NoteFolder folder =
        folders.save(
            new NoteFolder(
                studentId,
                name,
                colorOrBlue(request.color()),
                cleanIcon(request.icon()),
                parentId,
                position));
    return respond(folder, 0, List.of());
  }

  @Transactional
  public FolderResponse update(CurrentUser user, Long id, UpdateFolderRequest request) {
    Long studentId = students.currentStudent(user).studentId();
    NoteFolder folder =
        folders.findByIdAndStudentId(id, studentId).orElseThrow(ResourceNotFoundException::new);
    boolean changed = false;
    if (request.name() != null) {
      String name = request.name().strip();
      if (name.isEmpty()) {
        throw new ValidationException("name", "A folder needs a name.");
      }
      if (folders.existsByStudentIdAndNameAndIdNot(studentId, name, id)) {
        throw new ConflictException("A folder with that name already exists.");
      }
      folder.setName(name);
      changed = true;
    }
    if (request.color() != null) {
      if (!COLORS.contains(request.color())) {
        throw new ValidationException("color", "Unknown folder colour.");
      }
      folder.setColor(request.color());
      changed = true;
    }
    if (request.icon() != null) {
      folder.setIcon(cleanIcon(request.icon()));
      changed = true;
    }
    if (request.position() != null) {
      folder.setPosition(request.position());
      changed = true;
    }
    if (Boolean.TRUE.equals(request.toTopLevel())) {
      folder.setParentId(null);
      changed = true;
    } else if (request.parentId() != null) {
      if (request.parentId().equals(id)) {
        throw new ValidationException("parentId", "A folder cannot contain itself.");
      }
      if (wouldCycle(studentId, id, request.parentId())) {
        throw new ValidationException("parentId", "That would put the folder inside itself.");
      }
      folder.setParentId(ownedFolderId(studentId, request.parentId()));
      changed = true;
    }
    if (!changed) {
      throw new ValidationException("name", "Nothing to update.");
    }
    folder.touch(clock.instant());
    return respond(folders.save(folder), 0, List.of());
  }

  /** The new order after a drag, in one request. */
  @Transactional
  public void reorder(CurrentUser user, ReorderFoldersRequest request) {
    Long studentId = students.currentStudent(user).studentId();
    for (ReorderFoldersRequest.Entry entry : request.order()) {
      folders
          .findByIdAndStudentId(entry.id(), studentId)
          .ifPresent(
              folder -> {
                folder.setPosition(entry.position());
                if (Boolean.TRUE.equals(entry.setParent())) {
                  Long parent = entry.parentId();
                  // A folder cannot be its own parent: the tree would never end.
                  folder.setParentId(
                      parent == null
                              || parent.equals(folder.getId())
                              || wouldCycle(studentId, folder.getId(), parent)
                          ? null
                          : ownedFolderId(studentId, parent));
                }
                folder.touch(clock.instant());
              });
    }
  }

  @Transactional
  public void delete(CurrentUser user, Long id) {
    Long studentId = students.currentStudent(user).studentId();
    NoteFolder folder =
        folders.findByIdAndStudentId(id, studentId).orElseThrow(ResourceNotFoundException::new);
    // The notes are never deleted with the folder. They are made unfiled here, and the database
    // does the same for the notes of any folder inside this one.
    notes.unfile(folder.getId());
    folders.delete(folder);
  }

  // --- used by the other note services -------------------------------------------------------

  /** The ID of the folder if it is the student's, otherwise null. */
  Long ownedFolderId(Long studentId, Long folderId) {
    if (folderId == null) {
      return null;
    }
    return folders.findByIdAndStudentId(folderId, studentId).map(NoteFolder::getId).orElse(null);
  }

  Map<Long, NoteFolder> foldersById(Long studentId) {
    Map<Long, NoteFolder> map = new LinkedHashMap<>();
    folders.findByStudentIdOrderByPositionAscIdAsc(studentId).forEach(f -> map.put(f.getId(), f));
    return map;
  }

  /**
   * The student's Quick Notes folder, created when missing. It is an ordinary folder: if the
   * student deletes it, the next quick capture simply creates it again.
   */
  Long quickNotesFolder(Long studentId) {
    // A student whose first action is a quick note still gets the default folders.
    if (folders.findByStudentIdOrderByPositionAscIdAsc(studentId).isEmpty()) {
      seedDefaults(studentId);
    }
    return folders
        .findByStudentIdAndName(studentId, QUICK_NOTES)
        .map(NoteFolder::getId)
        .orElseGet(
            () ->
                folders
                    .save(new NoteFolder(studentId, QUICK_NOTES, "cyan", "bolt", null, 0))
                    .getId());
  }

  // --- helpers -------------------------------------------------------------------------------

  /** A student who has no folder yet gets the default ones. */
  private List<NoteFolder> seedDefaults(Long studentId) {
    List<NoteFolder> created = new ArrayList<>();
    int position = 0;
    for (Default folder : DEFAULTS) {
      created.add(
          folders.save(
              new NoteFolder(
                  studentId, folder.name(), folder.color(), folder.icon(), null, position++)));
    }
    return created;
  }

  /**
   * Walks up from the proposed parent to see whether it lies inside the folder being moved. Without
   * this, dragging a folder into its own child detaches both from the tree. It is bounded by depth,
   * since a cycle is exactly what is being looked for.
   */
  private boolean wouldCycle(Long studentId, Long movingId, Long proposedParentId) {
    Long cursor = proposedParentId;
    for (int depth = 0; depth < 32 && cursor != null; depth++) {
      if (cursor.equals(movingId)) {
        return true;
      }
      cursor =
          folders.findByIdAndStudentId(cursor, studentId).map(NoteFolder::getParentId).orElse(null);
    }
    return false;
  }

  private Map<Long, List<NotePreview>> previews(Long studentId) {
    Map<Long, List<NotePreview>> byFolder = new HashMap<>();
    for (Object[] row : notes.previewRows(studentId, PREVIEW_SCAN_LIMIT)) {
      if (row[1] == null) {
        continue;
      }
      Long folderId = ((Number) row[1]).longValue();
      List<NotePreview> bucket = byFolder.computeIfAbsent(folderId, key -> new ArrayList<>());
      if (bucket.size() >= PREVIEWS_PER_FOLDER) {
        continue;
      }
      bucket.add(
          new NotePreview(
              ((Number) row[0]).longValue(),
              (String) row[2],
              NoteText.snippet((String) row[3]),
              (String) row[4],
              toInstant(row[5]),
              Boolean.TRUE.equals(row[6])));
    }
    return byFolder;
  }

  static Instant toInstant(Object value) {
    if (value instanceof Instant instant) {
      return instant;
    }
    if (value instanceof OffsetDateTime offset) {
      return offset.toInstant();
    }
    if (value instanceof Timestamp timestamp) {
      return timestamp.toInstant();
    }
    return null;
  }

  private static FolderResponse respond(NoteFolder folder, int count, List<NotePreview> previews) {
    return new FolderResponse(
        folder.getId(),
        folder.getName(),
        folder.getColor(),
        folder.getIcon(),
        folder.getParentId(),
        folder.getPosition(),
        count,
        previews);
  }

  private static String colorOrBlue(String color) {
    return color != null && COLORS.contains(color) ? color : "blue";
  }

  private static String cleanIcon(String icon) {
    if (icon == null || icon.isBlank()) {
      return null;
    }
    return icon.strip().matches("^[a-z0-9_]{1,40}$") ? icon.strip() : null;
  }
}
