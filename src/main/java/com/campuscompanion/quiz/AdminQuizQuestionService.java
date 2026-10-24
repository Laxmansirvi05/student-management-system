package com.campuscompanion.quiz;

import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import com.campuscompanion.common.error.ResourceNotFoundException;
import com.campuscompanion.common.error.ValidationException;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.common.web.PageResponse;
import com.campuscompanion.quiz.dto.AdminQuizQuestionResponse;
import com.campuscompanion.quiz.dto.QuizQuestionWriteRequest;
import com.campuscompanion.subject.SubjectService;
import com.campuscompanion.subject.SubjectService.UnitOfSubject;
import com.campuscompanion.subject.dto.SubjectResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The question bank (FR-ADM-008). A question is written, published, and later retired. Nothing is
 * ever deleted. Once a question has been served in a test, what it asks and how it is marked are
 * fixed, so that the results students already have stay true; a corrected question is added as a
 * new one and the old one is retired.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminQuizQuestionService {

  private final QuizQuestionRepository questions;
  private final QuizAttemptAnswerRepository answers;
  private final SubjectService subjects;

  public AdminQuizQuestionService(
      QuizQuestionRepository questions,
      QuizAttemptAnswerRepository answers,
      SubjectService subjects) {
    this.questions = questions;
    this.answers = answers;
    this.subjects = subjects;
  }

  /** The questions, newest first. Every filter is optional. */
  @Transactional(readOnly = true)
  public PageResponse<AdminQuizQuestionResponse> list(
      Long subjectId, Long unitId, QuestionStatus status, int page, int size) {
    Page<QuizQuestion> found =
        questions.search(
            subjectId,
            unitId,
            status,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    List<Long> ids = found.stream().map(QuizQuestion::getId).toList();
    Set<Long> used = ids.isEmpty() ? Set.of() : new HashSet<>(answers.usedAmong(ids));
    Map<Long, SubjectResponse> subjectOf =
        subjects.byIds(found.stream().map(QuizQuestion::getSubjectId).distinct().toList());
    Map<Long, UnitOfSubject> unitOf =
        subjects.unitsByIds(found.stream().map(QuizQuestion::getUnitId).distinct().toList());
    List<AdminQuizQuestionResponse> content =
        found.stream()
            .map(
                question ->
                    toResponse(
                        question,
                        subjectOf.get(question.getSubjectId()),
                        unitOf.get(question.getUnitId()),
                        used.contains(question.getId())))
            .toList();
    return new PageResponse<>(
        content,
        found.getNumber(),
        found.getSize(),
        found.getTotalElements(),
        found.getTotalPages());
  }

  /** True once the question bank holds at least one question. */
  @Transactional(readOnly = true)
  public boolean anyQuestionExists() {
    return questions.count() > 0;
  }

  /** Whether the subject already has a question with this text, so a bank is loaded only once. */
  @Transactional(readOnly = true)
  public boolean existsInSubject(Long subjectId, String questionText) {
    return questions.existsBySubjectIdAndQuestionText(subjectId, questionText);
  }

  @Transactional
  public AdminQuizQuestionResponse create(QuizQuestionWriteRequest request, CurrentUser admin) {
    UnitOfSubject unit = unitOf(request);
    if (request.status() == QuestionStatus.RETIRED) {
      throw new ValidationException("status", "A new question is a draft or published.");
    }
    QuizQuestion question = new QuizQuestion(QuestionSource.ADMIN, admin.id());
    write(question, request);
    return toResponse(questions.save(question), subjectOf(request), unit, false);
  }

  @Transactional
  public AdminQuizQuestionResponse update(Long id, QuizQuestionWriteRequest request) {
    QuizQuestion question = questions.findById(id).orElseThrow(ResourceNotFoundException::new);
    UnitOfSubject unit = unitOf(request);
    boolean used = answers.existsByQuestionId(id);
    if (used) {
      if (!asksTheSame(question, request)) {
        throw new ApiException(
            ErrorCode.CONFLICT,
            "This question has been used in a test, so its text, options and correct option can no"
                + " longer change. Retire it and add the corrected question as a new one.");
      }
      if (request.status() == QuestionStatus.DRAFT) {
        throw new ValidationException(
            "status", "A question that has been used in a test is published or retired.");
      }
    }
    write(question, request);
    return toResponse(question, subjectOf(request), unit, used);
  }

  private static void write(QuizQuestion question, QuizQuestionWriteRequest request) {
    question.write(
        request.subjectId(),
        request.unitId(),
        request.questionText().strip(),
        request.optionA().strip(),
        request.optionB().strip(),
        request.optionC().strip(),
        request.optionD().strip(),
        request.correctOption());
    question.describe(request.explanation().strip(), request.difficulty(), request.status());
  }

  private static boolean asksTheSame(QuizQuestion question, QuizQuestionWriteRequest request) {
    return question.asksTheSame(
        request.subjectId(),
        request.unitId(),
        request.questionText().strip(),
        request.optionA().strip(),
        request.optionB().strip(),
        request.optionC().strip(),
        request.optionD().strip(),
        request.correctOption());
  }

  /**
   * The unit of the request, checked to exist and to belong to the subject of the request; null for
   * a question of the whole subject.
   */
  private UnitOfSubject unitOf(QuizQuestionWriteRequest request) {
    if (subjects.find(request.subjectId()).isEmpty()) {
      throw new ValidationException("subjectId", "Choose an existing subject.");
    }
    if (request.unitId() == null) {
      return null;
    }
    return subjects
        .findUnit(request.unitId())
        .filter(unit -> unit.subjectId().equals(request.subjectId()))
        .orElseThrow(() -> new ValidationException("unitId", "Choose a unit of this subject."));
  }

  private SubjectResponse subjectOf(QuizQuestionWriteRequest request) {
    return subjects.find(request.subjectId()).orElse(null);
  }

  private static AdminQuizQuestionResponse toResponse(
      QuizQuestion question, SubjectResponse subject, UnitOfSubject unit, boolean used) {
    return new AdminQuizQuestionResponse(
        question.getId(),
        question.getSubjectId(),
        subject == null ? null : subject.code(),
        question.getUnitId(),
        unit == null ? null : unit.unitNo(),
        unit == null ? null : unit.title(),
        question.getQuestionText(),
        question.optionText(OptionKey.A),
        question.optionText(OptionKey.B),
        question.optionText(OptionKey.C),
        question.optionText(OptionKey.D),
        question.getCorrectOption(),
        question.getExplanation(),
        question.getDifficulty(),
        question.getStatus(),
        question.getSource(),
        used,
        question.getCreatedAt());
  }
}
