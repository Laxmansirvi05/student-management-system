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

  /** The student's level in every unit they have answered questions in. */
  private List<UnitProgress> unitsOf(Long studentId) {
    // The answers arrive newest first, and each unit's list keeps that order.
    Map<Long, List<Boolean>> answersOfUnit = new LinkedHashMap<>();
    Map<Long, Long> subjectOfUnit = new LinkedHashMap<>();
    for (AnsweredQuestion answer : quiz.answeredBy(studentId)) {
      answersOfUnit
          .computeIfAbsent(answer.unitId(), unit -> new ArrayList<>())
          .add(answer.isCorrect());
      subjectOfUnit.put(answer.unitId(), answer.subjectId());
    }
    Map<Long, SubjectResponse> subjectOf =
        subjects.byIds(subjectOfUnit.values().stream().distinct().toList());
    Map<Long, UnitOfSubject> unitOf = subjects.unitsByIds(List.copyOf(answersOfUnit.keySet()));

    List<UnitProgress> units = new ArrayList<>();
    for (Map.Entry<Long, List<Boolean>> entry : answersOfUnit.entrySet()) {
      UnitOfSubject unit = unitOf.get(entry.getKey());
      SubjectResponse subject = subjectOf.get(subjectOfUnit.get(entry.getKey()));
      if (unit == null || subject == null) {
        continue;
      }
      UnitPerformance performance = ProgressCalculator.of(entry.getValue());
      units.add(
          new UnitProgress(
              subject.id(),
              subject.code(),
              subject.name(),
              unit.id(),
              unit.unitNo(),
              unit.title(),
              performance.answered(),
              performance.correct(),
              performance.accuracyPercent(),
              performance.level()));
    }
    units.sort(Comparator.comparing(UnitProgress::subjectCode).thenComparing(UnitProgress::unitNo));
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
                    resources.forRevision(unit.unitId(), RESOURCES_PER_UNIT).stream()
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
