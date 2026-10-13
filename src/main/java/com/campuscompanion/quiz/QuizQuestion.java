package com.campuscompanion.quiz;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One multiple-choice question of the question bank, with four options and one correct option. */
@Entity
@Table(name = "quiz_questions")
public class QuizQuestion {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "subject_id", nullable = false)
  private Long subjectId;

  @Column(name = "unit_id", nullable = false)
  private Long unitId;

  @Column(name = "question_text", nullable = false, length = 1000)
  private String questionText;

  @Column(name = "option_a", nullable = false, length = 300)
  private String optionA;

  @Column(name = "option_b", nullable = false, length = 300)
  private String optionB;

  @Column(name = "option_c", nullable = false, length = 300)
  private String optionC;

  @Column(name = "option_d", nullable = false, length = 300)
  private String optionD;

  /** Never sent to a student before the attempt is closed. */
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "correct_option", nullable = false, length = 1)
  private OptionKey correctOption;

  @Column(nullable = false, length = 2000)
  private String explanation;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Difficulty difficulty;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20, updatable = false)
  private QuestionSource source;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private QuestionStatus status;

  @Column(name = "created_by", nullable = false, updatable = false)
  private Long createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  /** Required by JPA. */
  protected QuizQuestion() {}

  public QuizQuestion(QuestionSource source, Long createdBy) {
    this.source = source;
    this.createdBy = createdBy;
  }

  /** Sets what is asked and how it is marked. */
  public void write(
      Long subjectId,
      Long unitId,
      String questionText,
      String optionA,
      String optionB,
      String optionC,
      String optionD,
      OptionKey correctOption) {
    this.subjectId = subjectId;
    this.unitId = unitId;
    this.questionText = questionText;
    this.optionA = optionA;
    this.optionB = optionB;
    this.optionC = optionC;
    this.optionD = optionD;
    this.correctOption = correctOption;
    this.updatedAt = Instant.now();
  }

  /** Sets the details that do not change how a test is marked. */
  public void describe(String explanation, Difficulty difficulty, QuestionStatus status) {
    this.explanation = explanation;
    this.difficulty = difficulty;
    this.status = status;
    this.updatedAt = Instant.now();
  }

  /** True when a test taken with this content would be asked and marked in the same way. */
  public boolean asksTheSame(
      Long subjectId,
      Long unitId,
      String questionText,
      String optionA,
      String optionB,
      String optionC,
      String optionD,
      OptionKey correctOption) {
    return this.subjectId.equals(subjectId)
        && this.unitId.equals(unitId)
        && this.questionText.equals(questionText)
        && this.optionA.equals(optionA)
        && this.optionB.equals(optionB)
        && this.optionC.equals(optionC)
        && this.optionD.equals(optionD)
        && this.correctOption == correctOption;
  }

  public String optionText(OptionKey key) {
    return switch (key) {
      case A -> optionA;
      case B -> optionB;
      case C -> optionC;
      case D -> optionD;
    };
  }

  public Long getId() {
    return id;
  }

  public Long getSubjectId() {
    return subjectId;
  }

  public Long getUnitId() {
    return unitId;
  }

  public String getQuestionText() {
    return questionText;
  }

  public OptionKey getCorrectOption() {
    return correctOption;
  }

  public String getExplanation() {
    return explanation;
  }

  public Difficulty getDifficulty() {
    return difficulty;
  }

  public QuestionSource getSource() {
    return source;
  }

  public QuestionStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
