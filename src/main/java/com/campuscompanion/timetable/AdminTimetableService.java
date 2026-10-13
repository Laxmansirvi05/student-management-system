package com.campuscompanion.timetable;

import com.campuscompanion.common.error.ConflictException;
import com.campuscompanion.common.error.ResourceNotFoundException;
import com.campuscompanion.common.error.ValidationException;
import com.campuscompanion.subject.SubjectService;
import com.campuscompanion.timetable.dto.AdminTimetableEntryResponse;
import com.campuscompanion.timetable.dto.TimetableWriteRequest;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Managing the timetable (FR-ADM-005). A class must end after it starts, and two classes of the
 * same section may not share any time on the same day.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminTimetableService {

  private static final long NO_ENTRY = -1;

  private final TimetableEntryRepository entries;
  private final SubjectService subjects;

  public AdminTimetableService(TimetableEntryRepository entries, SubjectService subjects) {
    this.entries = entries;
    this.subjects = subjects;
  }

  @Transactional(readOnly = true)
  public List<AdminTimetableEntryResponse> list(String branch, Integer semester, String section) {
    return entries.findFiltered(blankToNull(branch), semester, blankToNull(section)).stream()
        .map(AdminTimetableService::toResponse)
        .toList();
  }

  @Transactional
  public AdminTimetableEntryResponse create(TimetableWriteRequest request) {
    Slot slot = checked(request, NO_ENTRY);
    TimetableEntry entry =
        new TimetableEntry(
            slot.branch(),
            request.semester(),
            slot.section(),
            request.dayOfWeek(),
            slot.start(),
            slot.end(),
            request.subjectId(),
            blankToNull(request.room()),
            blankToNull(request.facultyName()));
    return toResponse(entries.save(entry));
  }

  @Transactional
  public AdminTimetableEntryResponse update(Long id, TimetableWriteRequest request) {
    TimetableEntry entry = entries.findById(id).orElseThrow(ResourceNotFoundException::new);
    Slot slot = checked(request, id);
    entry.update(
        slot.branch(),
        request.semester(),
        slot.section(),
        request.dayOfWeek(),
        slot.start(),
        slot.end(),
        request.subjectId(),
        blankToNull(request.room()),
        blankToNull(request.facultyName()));
    return toResponse(entry);
  }

  @Transactional
  public void delete(Long id) {
    entries.delete(entries.findById(id).orElseThrow(ResourceNotFoundException::new));
  }

  /** The request with its text tidied and its times read, after every rule has been checked. */
  private record Slot(String branch, String section, LocalTime start, LocalTime end) {}

  private Slot checked(TimetableWriteRequest request, long excludeId) {
    if (subjects.find(request.subjectId()).isEmpty()) {
      throw new ValidationException("subjectId", "Choose an existing subject.");
    }
    LocalTime start = time("startTime", request.startTime());
    LocalTime end = time("endTime", request.endTime());
    if (!end.isAfter(start)) {
      throw new ValidationException("endTime", "The class must end after it starts.");
    }
    String branch = request.branch().strip().toUpperCase(Locale.ROOT);
    String section = request.section().strip().toUpperCase(Locale.ROOT);
    if (entries.overlaps(
        branch, request.semester(), section, request.dayOfWeek(), start, end, excludeId)) {
      throw new ConflictException("This class overlaps another class of the same section.");
    }
    return new Slot(branch, section, start, end);
  }

  private static LocalTime time(String field, String text) {
    try {
      return LocalTime.parse(text, TimetableService.HOUR_MINUTE);
    } catch (DateTimeParseException notATime) {
      throw new ValidationException(field, "Write the time like 09:00.");
    }
  }

  private static String blankToNull(String text) {
    return text == null || text.isBlank() ? null : text.strip();
  }

  static AdminTimetableEntryResponse toResponse(TimetableEntry entry) {
    return new AdminTimetableEntryResponse(
        entry.getId(),
        entry.getBranch(),
        entry.getSemester(),
        entry.getSection(),
        entry.getDayOfWeek(),
        TimetableService.format(entry.getStartTime()),
        TimetableService.format(entry.getEndTime()),
        entry.getSubjectId(),
        entry.getRoom(),
        entry.getFacultyName());
  }
}
