package com.campuscompanion.common.config;

import com.campuscompanion.auth.AdminAccounts;
import com.campuscompanion.auth.Role;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.quiz.AdminQuizQuestionService;
import com.campuscompanion.quiz.Difficulty;
import com.campuscompanion.quiz.OptionKey;
import com.campuscompanion.quiz.QuestionStatus;
import com.campuscompanion.quiz.dto.QuizQuestionWriteRequest;
import com.campuscompanion.subject.AdminSubjectService;
import com.campuscompanion.subject.SubjectService;
import com.campuscompanion.subject.dto.AdminSubjectResponse;
import com.campuscompanion.subject.dto.UnitResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Practice questions for the demo subjects, so Quick Exam can be tried at once. They are general
 * textbook questions kept in {@code demo/quiz-questions.json}, and are saved through the same admin
 * service an administrator uses. A question whose subject or unit does not exist is skipped.
 */
@Component
public class DemoQuizSeeder {

  private static final Logger log = LoggerFactory.getLogger(DemoQuizSeeder.class);
  private static final String FILE = "demo/quiz-questions.json";

  /** One question as it is written in the file. "correct" is A, B, C or D. */
  record DemoQuestion(
      String subject,
      int unit,
      Difficulty difficulty,
      String text,
      List<String> options,
      OptionKey correct,
      String explanation) {}

  private final AdminQuizQuestionService questions;
  private final AdminSubjectService adminSubjects;
  private final SubjectService subjects;
  private final AdminAccounts admins;
  private final ObjectMapper json;

  public DemoQuizSeeder(
      AdminQuizQuestionService questions,
      AdminSubjectService adminSubjects,
      SubjectService subjects,
      AdminAccounts admins,
      ObjectMapper json) {
    this.questions = questions;
    this.adminSubjects = adminSubjects;
    this.subjects = subjects;
    this.admins = admins;
    this.json = json;
  }

  /** Loads the demo questions once. They are all saved, or none of them is. */
  @Transactional
  public void seedIfEmpty() {
    Optional<Long> adminId = admins.firstAdminId();
    if (adminId.isEmpty()) {
      return;
    }
    CurrentUser admin = new CurrentUser(adminId.get(), Role.ADMIN);
    DemoAcademicSeeder.actAs(admin);
    try {
      if (questions.anyQuestionExists()) {
        return;
      }
      List<AdminSubjectResponse> allSubjects = adminSubjects.list(null, null);
      int saved = 0;
      for (DemoQuestion question : read()) {
        Optional<QuizQuestionWriteRequest> request = toRequest(question, allSubjects);
        if (request.isPresent()) {
          questions.create(request.get(), admin);
          saved++;
        }
      }
      log.info("Loaded {} demo practice questions.", saved);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private Optional<QuizQuestionWriteRequest> toRequest(
      DemoQuestion question, List<AdminSubjectResponse> allSubjects) {
    Optional<AdminSubjectResponse> subject =
        allSubjects.stream().filter(entry -> entry.code().equals(question.subject())).findFirst();
    if (subject.isEmpty()) {
      return Optional.empty();
    }
    Optional<UnitResponse> unit =
        subjects.unitsOf(subject.get().id()).stream()
            .filter(entry -> entry.unitNo() == question.unit())
            .findFirst();
    return unit.map(
        found ->
            new QuizQuestionWriteRequest(
                subject.get().id(),
                found.id(),
                question.text(),
                question.options().get(0),
                question.options().get(1),
                question.options().get(2),
                question.options().get(3),
                question.correct(),
                question.explanation(),
                question.difficulty(),
                QuestionStatus.PUBLISHED));
  }

  private List<DemoQuestion> read() {
    try (InputStream file = new ClassPathResource(FILE).getInputStream()) {
      return List.of(json.readValue(file, DemoQuestion[].class));
    } catch (IOException e) {
      throw new UncheckedIOException("The demo questions could not be read", e);
    }
  }
}
