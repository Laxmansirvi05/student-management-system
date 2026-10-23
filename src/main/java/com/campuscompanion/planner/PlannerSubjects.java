package com.campuscompanion.planner;

import com.campuscompanion.common.error.ValidationException;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.planner.dto.PlannerSubject;
import com.campuscompanion.subject.SubjectService;
import com.campuscompanion.subject.dto.SubjectResponse;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** The subjects tasks and exams may refer to: only those the student can see. */
@Component
class PlannerSubjects {

  private final SubjectService subjects;

  PlannerSubjects(SubjectService subjects) {
    this.subjects = subjects;
  }

  /** The chosen subject, if it is one of the student's; otherwise a field error. Null is "none". */
  Long checked(CurrentUser user, Long subjectId) {
    if (subjectId == null) {
      return null;
    }
    boolean visible =
        subjects.visibleTo(user, null).stream().anyMatch(entry -> entry.id().equals(subjectId));
    if (!visible) {
      throw new ValidationException("subjectId", "Choose one of your subjects.");
    }
    return subjectId;
  }

  /** The names of these subjects, by ID, to show next to tasks and exams. */
  Map<Long, PlannerSubject> named(Collection<Long> subjectIds) {
    return subjects.byIds(subjectIds).values().stream()
        .collect(Collectors.toMap(SubjectResponse::id, PlannerSubjects::toSubject));
  }

  private static PlannerSubject toSubject(SubjectResponse subject) {
    return new PlannerSubject(subject.id(), subject.code(), subject.name());
  }
}
