package com.campuscompanion.quiz;

import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import com.campuscompanion.common.error.ResourceNotFoundException;
import com.campuscompanion.common.error.ValidationException;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.common.web.PageResponse;
import com.campuscompanion.quiz.QuizEvaluationService.Evaluation;
import com.campuscompanion.quiz.QuizEvaluationService.MarkedAnswer;
import com.campuscompanion.quiz.QuizEvaluationService.ServedQuestion;
import com.campuscompanion.quiz.QuizEvaluationService.SubmittedAnswer;
import com.campuscompanion.quiz.dto.QuizAttemptStartedResponse;
import com.campuscompanion.quiz.dto.QuizAttemptSummaryResponse;
import com.campuscompanion.quiz.dto.QuizOption;
import com.campuscompanion.quiz.dto.QuizResultResponse;
import com.campuscompanion.quiz.dto.StartQuizRequest;
import com.campuscompanion.quiz.dto.SubmitQuizRequest;
import com.campuscompanion.student.StudentProfileService;
import com.campuscompanion.subject.SubjectService;
import com.campuscompanion.subject.SubjectService.UnitOfSubject;
import com.campuscompanion.subject.dto.SubjectResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quick Exam for students (FR-QZ-001 to FR-QZ-004): starting a test, submitting it once, reading
 * the result and the history. Every method works only on the tests of the student who is logged in;
 * another student's test is "not found".
 *
 * <p>The time limit belongs to the server. The end of a test is fixed when it starts, and answers
 * that arrive later than that plus a short grace period are refused.
 */
@Service
public class QuizService {

  static final Duration TIME_PER_QUESTION = Duration.ofSeconds(60);

  /** Allows for a slow connection when the test submits itself at zero. */
  static final Duration GRACE = Duration.ofSeconds(15);

  static final Set<Integer> LENGTHS = Set.of(5, 10, 20);

  private final QuizQuestionRepository questions;
  private final QuizAttemptRepository attempts;
  private final QuizAttemptAnswerRepository answers;
  private final QuizEvaluationService evaluator;
  private final StudentProfileService students;
  private final SubjectService subjects;
  private final Clock clock;
  private final Random random = new SecureRandom();

  public QuizService(
      QuizQuestionRepository questions,
      QuizAttemptRepository attempts,
      QuizAttemptAnswerRepository answers,
      QuizEvaluationService evaluator,
      StudentProfileService students,
      SubjectService subjects,
      Clock clock) {
    this.questions = questions;
    this.attempts = attempts;
    this.answers = answers;
    this.evaluator = evaluator;
    this.students = students;
    this.subjects = subjects;
    this.clock = clock;
  }

  /**
   * Starts a test: picks the questions at random from the published questions of the unit, and
   * returns them with their options in random order and without their answers.
   */
  @Transactional
  public QuizAttemptStartedResponse start(CurrentUser currentUser, StartQuizRequest request) {
    Long studentId = students.currentStudent(currentUser).studentId();
    if (!LENGTHS.contains(request.questionCount())) {
      throw new ValidationException("questionCount", "Choose 5, 10 or 20 questions.");
    }
    subjects
        .findUnit(request.unitId())
        .filter(unit -> unit.subjectId().equals(request.subjectId()))
        .orElseThrow(ResourceNotFoundException::new);

    List<Long> available = new ArrayList<>(questions.findPublishedIds(request.unitId()));
    if (available.size() < request.questionCount()) {
      throw new QuizNotEnoughQuestionsException(available.size());
    }
    Collections.shuffle(available, random);
    List<Long> chosen = available.subList(0, request.questionCount());

    QuizAttempt attempt =
        attempts.save(
            new QuizAttempt(
                studentId,
                request.subjectId(),
                request.unitId(),
                chosen.size(),
                clock.instant(),
                TIME_PER_QUESTION.multipliedBy(chosen.size())));
    Map<Long, QuizQuestion> byId = questionsById(chosen);
    List<QuizAttemptStartedResponse.Question> served = new ArrayList<>();
    for (int i = 0; i < chosen.size(); i++) {
      QuizQuestion question = byId.get(chosen.get(i));
      answers.save(new QuizAttemptAnswer(attempt.getId(), question.getId(), i + 1));
      served.add(
          new QuizAttemptStartedResponse.Question(
              question.getId(), i + 1, question.getQuestionText(), shuffledOptions(question)));
    }
    return new QuizAttemptStartedResponse(
        attempt.getId(),
        attempt.getStartedAt(),
        attempt.getExpiresAt(),
        Duration.between(attempt.getStartedAt(), attempt.getExpiresAt()).toSeconds(),
        served);
  }

  /**
   * Marks a test and closes it. A test can be submitted once. Answers that arrive after the time
   * limit and the grace period close the test as expired instead; that change is kept even though
   * the request fails.
   */
  @Transactional(noRollbackFor = QuizAttemptExpiredException.class)
  public QuizResultResponse submit(
      CurrentUser currentUser, Long attemptId, SubmitQuizRequest request) {
    QuizAttempt attempt = ownAttempt(currentUser, attemptId);
    if (attempt.getStatus() == AttemptStatus.SUBMITTED) {
      throw new ApiException(ErrorCode.QUIZ_ALREADY_SUBMITTED, "This test was already submitted.");
    }
    List<QuizAttemptAnswer> rows = answers.findByAttemptIdOrderByPosition(attemptId);
    if (attempt.getStatus() == AttemptStatus.EXPIRED) {
      throw new QuizAttemptExpiredException();
    }
    Instant now = clock.instant();
    if (attempt.isOverdue(now, GRACE)) {
      expire(attempt, rows);
      throw new QuizAttemptExpiredException();
    }

    Map<Long, QuizQuestion> byId = questionsById(questionIdsOf(rows));
    Evaluation evaluation =
        evaluator.evaluate(
            rows.stream()
                .map(
                    row ->
                        new ServedQuestion(
                            row.getQuestionId(), byId.get(row.getQuestionId()).getCorrectOption()))
                .toList(),
            request.answers().stream()
                .map(answer -> new SubmittedAnswer(answer.questionId(), answer.selectedOption()))
                .toList());
    Map<Long, MarkedAnswer> marks =
        evaluation.answers().stream()
            .collect(Collectors.toMap(MarkedAnswer::questionId, Function.identity()));
    for (QuizAttemptAnswer row : rows) {
      MarkedAnswer mark = marks.get(row.getQuestionId());
      row.mark(mark.selectedOption(), mark.correct());
    }
    attempt.submit(now, evaluation.score());
    return resultOf(attempt, rows, byId);
  }

  /** The result of one of the student's own closed tests. */
  @Transactional
  public QuizResultResponse result(CurrentUser currentUser, Long attemptId) {
    QuizAttempt attempt = ownAttempt(currentUser, attemptId);
    List<QuizAttemptAnswer> rows = answers.findByAttemptIdOrderByPosition(attemptId);
    if (attempt.isInProgress()) {
      if (!attempt.isOverdue(clock.instant(), GRACE)) {
        // The answers must stay unknown while the test can still be submitted.
        throw new ApiException(ErrorCode.CONFLICT, "This test has not been submitted yet.");
      }
      expire(attempt, rows);
    }
    return resultOf(attempt, rows, questionsById(questionIdsOf(rows)));
  }

  /** The student's own tests, newest first. */
  @Transactional
  public PageResponse<QuizAttemptSummaryResponse> history(
      CurrentUser currentUser, int page, int size) {
    Long studentId = students.currentStudent(currentUser).studentId();
    closeOverdue(studentId);
    Page<QuizAttempt> found =
        attempts.findByStudentId(
            studentId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt", "id")));
    Map<Long, SubjectResponse> subjectOf =
        subjects.byIds(found.stream().map(QuizAttempt::getSubjectId).distinct().toList());
    Map<Long, UnitOfSubject> unitOf =
        subjects.unitsByIds(found.stream().map(QuizAttempt::getUnitId).distinct().toList());
    List<QuizAttemptSummaryResponse> content =
        found.stream()
            .map(
                attempt -> {
                  SubjectResponse subject = subjectOf.get(attempt.getSubjectId());
                  UnitOfSubject unit = unitOf.get(attempt.getUnitId());
                  return new QuizAttemptSummaryResponse(
                      attempt.getId(),
                      attempt.getSubjectId(),
                      subject == null ? null : subject.code(),
                      subject == null ? null : subject.name(),
                      attempt.getUnitId(),
                      unit == null ? null : unit.unitNo(),
                      unit == null ? null : unit.title(),
                      attempt.getStatus(),
                      attempt.getScore(),
                      attempt.getQuestionCount(),
                      attempt.getStartedAt());
                })
            .toList();
    return new PageResponse<>(
        content,
        found.getNumber(),
        found.getSize(),
        found.getTotalElements(),
        found.getTotalPages());
  }

  /** Every marked answer of a student in submitted tests, newest first. For the progress rules. */
  @Transactional(readOnly = true)
  public List<AnsweredQuestion> answeredBy(Long studentId) {
    return answers.findAnsweredBy(studentId);
  }

  /** The scores of a student's submitted tests, as percentages. For the progress summary. */
  @Transactional(readOnly = true)
  public List<BigDecimal> submittedPercents(Long studentId) {
    return attempts.findByStudentIdAndStatus(studentId, AttemptStatus.SUBMITTED).stream()
        .map(attempt -> percent(attempt.getScore(), attempt.getQuestionCount()))
        .toList();
  }

  /** Score out of total as a percentage with two decimals, rounded half up. */
  public static BigDecimal percent(int score, int total) {
    return BigDecimal.valueOf(score)
        .multiply(BigDecimal.valueOf(100))
        .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
  }

  private QuizAttempt ownAttempt(CurrentUser currentUser, Long attemptId) {
    Long studentId = students.currentStudent(currentUser).studentId();
    return attempts
        .findOwnForUpdate(attemptId, studentId)
        .orElseThrow(ResourceNotFoundException::new);
  }

  /** Closes the student's tests whose time and grace period have passed without a submission. */
  private void closeOverdue(Long studentId) {
    Instant latestEnd = clock.instant().minus(GRACE);
    for (QuizAttempt overdue :
        attempts.findByStudentIdAndStatusAndExpiresAtBefore(
            studentId, AttemptStatus.IN_PROGRESS, latestEnd)) {
      expire(overdue, answers.findByAttemptIdOrderByPosition(overdue.getId()));
    }
  }

  private static void expire(QuizAttempt attempt, List<QuizAttemptAnswer> rows) {
    attempt.expire();
    rows.forEach(row -> row.mark(null, false));
  }

  private QuizResultResponse resultOf(
      QuizAttempt attempt, List<QuizAttemptAnswer> rows, Map<Long, QuizQuestion> byId) {
    SubjectResponse subject = subjects.find(attempt.getSubjectId()).orElse(null);
    UnitOfSubject unit = subjects.findUnit(attempt.getUnitId()).orElse(null);
    List<QuizResultResponse.Item> items =
        rows.stream()
            .map(
                row -> {
                  QuizQuestion question = byId.get(row.getQuestionId());
                  return new QuizResultResponse.Item(
                      question.getId(),
                      row.getPosition(),
                      question.getQuestionText(),
                      optionsInOrder(question),
                      row.getSelectedOption(),
                      question.getCorrectOption(),
                      row.isCorrect(),
                      question.getExplanation());
                })
            .toList();
    return new QuizResultResponse(
        attempt.getId(),
        attempt.getStatus(),
        attempt.getScore(),
        attempt.getQuestionCount(),
        percent(attempt.getScore(), attempt.getQuestionCount()),
        attempt.getStartedAt(),
        attempt.getSubmittedAt(),
        attempt.getSubjectId(),
        subject == null ? null : subject.code(),
        subject == null ? null : subject.name(),
        attempt.getUnitId(),
        unit == null ? null : unit.unitNo(),
        unit == null ? null : unit.title(),
        items);
  }

  private Map<Long, QuizQuestion> questionsById(List<Long> ids) {
    return questions.findAllById(ids).stream()
        .collect(Collectors.toMap(QuizQuestion::getId, Function.identity()));
  }

  private static List<Long> questionIdsOf(List<QuizAttemptAnswer> rows) {
    return rows.stream().map(QuizAttemptAnswer::getQuestionId).toList();
  }

  private static List<QuizOption> optionsInOrder(QuizQuestion question) {
    List<QuizOption> options = new ArrayList<>();
    for (OptionKey key : OptionKey.values()) {
      options.add(new QuizOption(key, question.optionText(key)));
    }
    return options;
  }

  /** The four options in random order, so the position of the right one cannot be learnt. */
  private List<QuizOption> shuffledOptions(QuizQuestion question) {
    List<QuizOption> options = optionsInOrder(question);
    Collections.shuffle(options, random);
    return options;
  }
}
