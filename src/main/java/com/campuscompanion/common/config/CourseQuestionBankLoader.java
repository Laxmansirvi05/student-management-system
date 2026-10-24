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
import com.campuscompanion.subject.dto.AdminSubjectResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Loads the course question banks in {@code question-bank/*.json} into Quick Exam. Each file holds
 * the multiple-choice questions of one subject, named by its code (for example 25CS21001); they
 * were written from the course notes and previous-year papers and belong to the whole subject, not
 * to a unit. A question is added once, published, through the same admin service an administrator
 * uses: a question already in the subject with the same text is skipped, and a file whose subject
 * does not exist is skipped. Turned off with QUESTION_BANK_ENABLED=false.
 */
@Component
@Order(3)
public class CourseQuestionBankLoader implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(CourseQuestionBankLoader.class);
  private static final String FILES = "classpath:question-bank/*.json";

  /** One question as it is written in a file. "correct" is A, B, C or D. */
  record BankQuestion(
      Difficulty difficulty,
      String text,
      List<String> options,
      OptionKey correct,
      String explanation) {}

  /** One file: the subject's code and its questions. */
  record Bank(String subject, List<BankQuestion> questions) {}

  private final QuestionBankProperties properties;
  private final AdminQuizQuestionService questions;
  private final AdminSubjectService subjects;
  private final AdminAccounts admins;
  private final ObjectMapper json;

  public CourseQuestionBankLoader(
      QuestionBankProperties properties,
      AdminQuizQuestionService questions,
      AdminSubjectService subjects,
      AdminAccounts admins,
      ObjectMapper json) {
    this.properties = properties;
    this.questions = questions;
    this.subjects = subjects;
    this.admins = admins;
    this.json = json;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (properties.enabled()) {
      load();
    }
  }

  /** Adds every bundled question that is not there yet; returns how many were added. */
  public int load() {
    Optional<Long> adminId = admins.firstAdminId();
    if (adminId.isEmpty()) {
      return 0;
    }
    CurrentUser admin = new CurrentUser(adminId.get(), Role.ADMIN);
    DemoAcademicSeeder.actAs(admin);
    try {
      List<AdminSubjectResponse> allSubjects = subjects.list(null, null);
      int added = 0;
      for (Bank bank : read()) {
        Optional<AdminSubjectResponse> subject =
            allSubjects.stream().filter(entry -> entry.code().equals(bank.subject())).findFirst();
        if (subject.isEmpty()) {
          continue;
        }
        for (BankQuestion question : bank.questions()) {
          if (!questions.existsInSubject(subject.get().id(), question.text())) {
            questions.create(toRequest(subject.get().id(), question), admin);
            added++;
          }
        }
      }
      if (added > 0) {
        log.info("Loaded {} questions from the course question banks.", added);
      }
      return added;
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private static QuizQuestionWriteRequest toRequest(Long subjectId, BankQuestion question) {
    return new QuizQuestionWriteRequest(
        subjectId,
        null,
        question.text(),
        question.options().get(0),
        question.options().get(1),
        question.options().get(2),
        question.options().get(3),
        question.correct(),
        question.explanation(),
        question.difficulty(),
        QuestionStatus.PUBLISHED);
  }

  private List<Bank> read() {
    try {
      Resource[] files = new PathMatchingResourcePatternResolver().getResources(FILES);
      List<Bank> banks = new ArrayList<>();
      for (Resource file : files) {
        try (InputStream in = file.getInputStream()) {
          banks.add(json.readValue(in, Bank.class));
        }
      }
      return banks;
    } catch (IOException e) {
      throw new UncheckedIOException("The course question banks could not be read", e);
    }
  }
}
