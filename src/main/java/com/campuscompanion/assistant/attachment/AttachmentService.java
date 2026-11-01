package com.campuscompanion.assistant.attachment;

import com.campuscompanion.assistant.dto.AttachmentResponse;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.student.StudentProfileService;
import java.util.Locale;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Attaching files to the Quick Assistant: reads an uploaded file, keeps what was read for a short
 * time ({@link AttachmentStore}), and tells the page what was really found, so the page can show
 * "Ready" only for a file whose content is available to the model. Nothing is stored in the
 * database or in file storage.
 */
@Service
@PreAuthorize("hasRole('STUDENT')")
public class AttachmentService {

  private static final int PREVIEW_CHARS = 160;

  private final AttachmentReader reader;
  private final AttachmentStore store;
  private final StudentProfileService students;

  public AttachmentService(
      AttachmentReader reader, AttachmentStore store, StudentProfileService students) {
    this.reader = reader;
    this.store = store;
    this.students = students;
  }

  /** Reads the file and keeps it for the student; throws an error that says why when it cannot. */
  public AttachmentResponse attach(CurrentUser user, String fileName, byte[] content) {
    long studentId = students.currentStudent(user).studentId();
    ReadFile file = reader.read(fileName, content);
    String id = store.add(studentId, file);
    return new AttachmentResponse(
        id,
        file.name(),
        file.kind().name().toLowerCase(Locale.ROOT),
        file.pages(),
        file.pagesRead(),
        file.characters(),
        file.pictures().size(),
        file.notice(),
        preview(file));
  }

  /** Forgets a file the student attached. */
  public void remove(CurrentUser user, String id) {
    store.remove(students.currentStudent(user).studentId(), id);
  }

  private static String preview(ReadFile file) {
    for (Segment segment : file.segments()) {
      if (!segment.hasPicture() && !segment.text().isBlank()) {
        String text = segment.text().replaceAll("\\s+", " ").strip();
        return text.length() > PREVIEW_CHARS ? text.substring(0, PREVIEW_CHARS) + "…" : text;
      }
    }
    return null;
  }
}
