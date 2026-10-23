package com.campuscompanion.planner;

import com.campuscompanion.common.config.CampusTime;
import com.campuscompanion.common.error.ResourceNotFoundException;
import com.campuscompanion.common.error.ValidationException;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.planner.dto.PlannerSubject;
import com.campuscompanion.planner.dto.StudyTaskListResponse;
import com.campuscompanion.planner.dto.StudyTaskRequest;
import com.campuscompanion.planner.dto.StudyTaskResponse;
import com.campuscompanion.student.StudentProfileService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A student's study tasks (FR-PLN-001): add, edit, complete, reopen, delete, list. The student is
 * always the one of the session, and every lookup is by that student, so another student's task is
 * "not found".
 */
@Service
@PreAuthorize("hasRole('STUDENT')")
public class StudyTaskService {

  /** Pending tasks: the soonest due first, tasks without a date last, newest first among equals. */
  private static final Comparator<StudyTask> PENDING_ORDER =
      Comparator.comparing(StudyTask::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(StudyTask::getCreatedAt, Comparator.reverseOrder());

  /** Done tasks: the most recently completed first. */
  private static final Comparator<StudyTask> DONE_ORDER =
      Comparator.comparing(StudyTask::getCompletedAt, Comparator.reverseOrder());

  private final StudyTaskRepository tasks;
  private final StudentProfileService students;
  private final PlannerSubjects subjects;
  private final Clock clock;

  public StudyTaskService(
      StudyTaskRepository tasks,
      StudentProfileService students,
      PlannerSubjects subjects,
      Clock clock) {
    this.tasks = tasks;
    this.students = students;
    this.subjects = subjects;
    this.clock = clock;
  }

  /** One view of the list: pending (overdue included), overdue, done or all. */
  @Transactional(readOnly = true)
  public StudyTaskListResponse list(CurrentUser user, String view) {
    Long studentId = students.currentStudent(user).studentId();
    LocalDate today = CampusTime.today(clock);
    List<StudyTask> all = tasks.findByStudentId(studentId);
    List<StudyTask> pending =
        all.stream()
            .filter(task -> task.getStatus() == TaskStatus.PENDING)
            .sorted(PENDING_ORDER)
            .toList();
    List<StudyTask> overdue =
        pending.stream()
            .filter(task -> PlannerRules.isOverdue(task.getStatus(), task.getDueDate(), today))
            .toList();
    List<StudyTask> done =
        all.stream()
            .filter(task -> task.getStatus() == TaskStatus.DONE)
            .sorted(DONE_ORDER)
            .toList();
    List<StudyTask> shown =
        switch (view) {
          case "overdue" -> overdue;
          case "done" -> done;
          case "all" -> Stream.concat(pending.stream(), done.stream()).toList();
          default -> pending;
        };
    return new StudyTaskListResponse(
        respond(shown, today),
        new StudyTaskListResponse.Counts(pending.size(), overdue.size(), done.size()));
  }

  @Transactional
  public StudyTaskResponse create(CurrentUser user, StudyTaskRequest request) {
    Long studentId = students.currentStudent(user).studentId();
    LocalDate today = CampusTime.today(clock);
    checkDate(request.dueDate(), null, today);
    StudyTask task =
        new StudyTask(
            studentId,
            request.title().strip(),
            notesOf(request.notes()),
            subjects.checked(user, request.subjectId()),
            request.dueDate(),
            clock.instant());
    return respond(List.of(tasks.save(task)), today).get(0);
  }

  @Transactional
  public StudyTaskResponse update(CurrentUser user, Long id, StudyTaskRequest request) {
    StudyTask task = owned(user, id);
    LocalDate today = CampusTime.today(clock);
    checkDate(request.dueDate(), task.getDueDate(), today);
    task.change(
        request.title().strip(),
        notesOf(request.notes()),
        subjects.checked(user, request.subjectId()),
        request.dueDate());
    return respond(List.of(task), today).get(0);
  }

  @Transactional
  public StudyTaskResponse complete(CurrentUser user, Long id) {
    StudyTask task = owned(user, id);
    task.complete(clock.instant());
    return respond(List.of(task), CampusTime.today(clock)).get(0);
  }

  @Transactional
  public StudyTaskResponse reopen(CurrentUser user, Long id) {
    StudyTask task = owned(user, id);
    task.reopen();
    return respond(List.of(task), CampusTime.today(clock)).get(0);
  }

  /**
   * Deletes the task for good: a task is the student's own note, with nothing else hanging on it.
   */
  @Transactional
  public void delete(CurrentUser user, Long id) {
    tasks.delete(owned(user, id));
  }

  private StudyTask owned(CurrentUser user, Long id) {
    Long studentId = students.currentStudent(user).studentId();
    return tasks.findByIdAndStudentId(id, studentId).orElseThrow(ResourceNotFoundException::new);
  }

  private static void checkDate(LocalDate chosen, LocalDate current, LocalDate today) {
    String problem = PlannerRules.problemWithDate(chosen, current, today);
    if (problem != null) {
      throw new ValidationException("dueDate", problem);
    }
  }

  private static String notesOf(String notes) {
    return notes == null || notes.isBlank() ? null : notes.strip();
  }

  private List<StudyTaskResponse> respond(List<StudyTask> list, LocalDate today) {
    Map<Long, PlannerSubject> named =
        subjects.named(
            list.stream().map(StudyTask::getSubjectId).filter(Objects::nonNull).toList());
    return list.stream()
        .map(
            task ->
                new StudyTaskResponse(
                    task.getId(),
                    task.getTitle(),
                    task.getNotes(),
                    task.getSubjectId() == null ? null : named.get(task.getSubjectId()),
                    task.getDueDate(),
                    task.getStatus().name(),
                    PlannerRules.isOverdue(task.getStatus(), task.getDueDate(), today),
                    task.getCompletedAt(),
                    task.getCreatedAt()))
        .toList();
  }
}
