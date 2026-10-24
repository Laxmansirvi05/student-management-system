package com.campuscompanion.progress;

import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.progress.ProgressCalculator.UnitPerformance;
import com.campuscompanion.progress.dto.ProgressResponse;
import com.campuscompanion.progress.dto.ProgressResponse.UnitProgress;
import com.campuscompanion.progress.dto.RecommendationResponse;
import com.campuscompanion.progress.dto.RecommendationResponse.ResourceLink;
import com.campuscompanion.quiz.AnsweredQuestion;
import com.campuscompanion.quiz.QuizService;
import com.campuscompanion.resource.ResourceService;
import com.campuscompanion.student.StudentProfileService;
import com.campuscompanion.subject.SubjectService;
import com.campuscompanion.subject.SubjectService.UnitOfSubject;
import com.campuscompanion.subject.dto.SubjectResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Strengths, weak units and what to revise (FR-PRG-001, FR-PRG-002). The rules are in {@link
 * ProgressCalculator}; this class collects a student's answers, applies the rules to each unit, and
 * adds up to three resources to every unit that needs revision. No AI is involved.
 *
 * <p>Only submitted tests count. A test that ran out of time has no answers to learn from.
 */
@Service
@Transactional(readOnly = true)
public class ProgressService {

  static final int RESOURCES_PER_UNIT = 3;

  private final QuizService quiz;
  private final ResourceService resources;
  private final SubjectService subjects;
  private final StudentProfileService students;

  public ProgressService(
      QuizService quiz,
      ResourceService resources,
      SubjectService subjects,
      StudentProfileService students) {
    this.quiz = quiz;
    this.resources = resources;
    this.subjects = subjects;
    this.students = students;
  }

  public ProgressResponse progressFor(CurrentUser currentUser) {
    Long studentId = students.currentStudent(currentUser).studentId();
    List<UnitProgress> units = unitsOf(studentId);
    List<BigDecimal> percents = quiz.submittedPercents(studentId);
    return new ProgressResponse(
        percents.size(), average(percents), units, recommendationsFrom(units));
  }

  /** The units a student should revise, weakest first. Also shown on the dashboard. */
  public List<RecommendationResponse> recommendationsFor(Long studentId) {
    return recommendationsFrom(unitsOf(studentId));
  }

  /** What answers are grouped by: a unit, or the whole subject for a test without a unit. */
  private record Topic(Long subjectId, Long unitId) {}

  /**
   * The student's level in every unit they have answered questions in, and in every subject they
   * have taken whole-subject tests in (shown with no unit).
   */
  private List<UnitProgress> unitsOf(Long studentId) {
    // The answers arrive newest first, and each topic's list keeps that order.
    Map<Topic, List<Boolean>> answersOf = new LinkedHashMap<>();
    for (AnsweredQuestion answer : quiz.answeredBy(studentId)) {
      answersOf
          .computeIfAbsent(
              new Topic(answer.subjectId(), answer.unitId()), topic -> new ArrayList<>())
          .add(answer.isCorrect());
    }
    Map<Long, SubjectResponse> subjectOf =
        subjects.byIds(answersOf.keySet().stream().map(Topic::subjectId).distinct().toList());
    Map<Long, UnitOfSubject> unitOf =
        subjects.unitsByIds(
            answersOf.keySet().stream().map(Topic::unitId).filter(id -> id != null).toList());

    List<UnitProgress> units = new ArrayList<>();
    for (Map.Entry<Topic, List<Boolean>> entry : answersOf.entrySet()) {
      Topic topic = entry.getKey();
      SubjectResponse subject = subjectOf.get(topic.subjectId());
      UnitOfSubject unit = topic.unitId() == null ? null : unitOf.get(topic.unitId());
      if (subject == null || (topic.unitId() != null && unit == null)) {
        continue;
      }
      UnitPerformance performance = ProgressCalculator.of(entry.getValue());
      units.add(
          new UnitProgress(
              subject.id(),
              subject.code(),
              subject.name(),
              unit == null ? null : unit.id(),
              unit == null ? null : unit.unitNo(),
              unit == null ? null : unit.title(),
              performance.answered(),
              performance.correct(),
              performance.accuracyPercent(),
              performance.level()));
    }
    units.sort(
        Comparator.comparing(UnitProgress::subjectCode)
            .thenComparing(UnitProgress::unitNo, Comparator.nullsFirst(Comparator.naturalOrder())));
    return units;
  }

  /** One recommendation for each unit that needs revision, the weakest unit first. */
  private List<RecommendationResponse> recommendationsFrom(List<UnitProgress> units) {
    return units.stream()
        .filter(unit -> unit.level() == PerformanceLevel.NEEDS_REVISION)
        .sorted(Comparator.comparing(UnitProgress::accuracyPercent))
        .map(
            unit ->
                new RecommendationResponse(
                    unit.subjectId(),
                    unit.subjectCode(),
                    unit.unitId(),
                    unit.unitNo(),
                    unit.unitTitle(),
                    unit.accuracyPercent(),
                    resources
                        .forRevision(unit.subjectId(), unit.unitId(), RESOURCES_PER_UNIT)
                        .stream()
                        .map(
                            resource ->
                                new ResourceLink(
                                    resource.id(), resource.title(), resource.type().name()))
                        .toList()))
        .toList();
  }

  /** The mean of the scores, with two decimals. Null when no test was submitted. */
  static BigDecimal average(List<BigDecimal> percents) {
    if (percents.isEmpty()) {
      return null;
    }
    BigDecimal sum = percents.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    return sum.divide(BigDecimal.valueOf(percents.size()), 2, RoundingMode.HALF_UP);
  }
}
