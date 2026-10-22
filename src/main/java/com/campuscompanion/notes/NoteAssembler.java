package com.campuscompanion.notes;

import com.campuscompanion.notes.dto.NoteFolderRef;
import com.campuscompanion.notes.dto.NoteResponse;
import com.campuscompanion.student.StudentContext;
import com.campuscompanion.student.StudentProfileService;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Builds {@link NoteResponse} values: the folder of each note and whether it is shared. */
@Component
public class NoteAssembler {

  private final NoteFolderRepository folders;
  private final NoteShareRepository shares;
  private final NoteShareRecipientRepository recipients;
  private final StudentProfileService students;

  public NoteAssembler(
      NoteFolderRepository folders,
      NoteShareRepository shares,
      NoteShareRecipientRepository recipients,
      StudentProfileService students) {
    this.folders = folders;
    this.shares = shares;
    this.recipients = recipients;
    this.students = students;
  }

  /** The student's own notes. */
  @Transactional(readOnly = true)
  public List<NoteResponse> own(Collection<Note> notes, Long studentId) {
    if (notes.isEmpty()) {
      return List.of();
    }
    Map<Long, NoteFolder> byId = new java.util.HashMap<>();
    folders.findByStudentIdOrderByPositionAscIdAsc(studentId).forEach(f -> byId.put(f.getId(), f));
    Set<Long> shared = sharedIds(notes);
    return notes.stream()
        .map(
            note ->
                respond(
                    note, byId.get(note.getFolderId()), shared.contains(note.getId()), null, null))
        .toList();
  }

  /** Notes that other students shared with this one, with the permission each was shared with. */
  @Transactional(readOnly = true)
  public List<NoteResponse> sharedWithMe(
      Collection<Note> notes, Map<Long, String> permissionByNote) {
    if (notes.isEmpty()) {
      return List.of();
    }
    Map<Long, StudentContext> owners =
        students.studentsByIds(notes.stream().map(Note::getStudentId).distinct().toList());
    return notes.stream()
        .map(
            note -> {
              StudentContext owner = owners.get(note.getStudentId());
              return respond(
                  note,
                  null,
                  true,
                  permissionByNote.getOrDefault(note.getId(), NoteShareRecipient.VIEW),
                  owner == null ? null : owner.fullName());
            })
        .toList();
  }

  /** One note, for its owner. */
  @Transactional(readOnly = true)
  public NoteResponse one(Note note, Long studentId) {
    return own(List.of(note), studentId).get(0);
  }

  private Set<Long> sharedIds(Collection<Note> notes) {
    List<Long> ids = notes.stream().map(Note::getId).toList();
    Set<Long> shared = new HashSet<>();
    shares.findByNoteIdIn(ids).stream()
        .filter(share -> NoteShare.VIEW.equals(share.getLinkAccess()))
        .forEach(share -> shared.add(share.getNoteId()));
    recipients.findByNoteIdIn(ids).forEach(recipient -> shared.add(recipient.getNoteId()));
    return shared;
  }

  static NoteResponse respond(
      Note note, NoteFolder folder, boolean shared, String sharedPermission, String sharedBy) {
    return new NoteResponse(
        note.getId(),
        note.getTitle(),
        note.getContent(),
        note.getSource(),
        note.getFolderId(),
        List.copyOf(note.getTags()),
        note.isPinned(),
        note.isArchived(),
        note.getDeletedAt(),
        note.getCreatedAt(),
        note.getUpdatedAt(),
        folder == null
            ? null
            : new NoteFolderRef(folder.getId(), folder.getName(), folder.getColor()),
        shared,
        sharedPermission,
        sharedBy);
  }
}
