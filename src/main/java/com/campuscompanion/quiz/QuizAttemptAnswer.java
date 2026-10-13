package com.campuscompanion.quiz;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One question served in an attempt, its place in the test, and what the student chose. */
@Entity
@Table(name = "quiz_attempt_answers")
public class QuizAttemptAnswer {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "attempt_id", nullable = false, updatable = false)
  private Long attemptId;

  @Column(name = "question_id", nullable = false, updatable = false)
  private Long questionId;

  @JdbcTypeCode(SqlTypes.SMALLINT)
  @Column(nullable = false, updatable = false)
  private int position;

  /** Null when the question was not answered. */
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "selected_option", length = 1)
  private OptionKey selectedOption;

  /** Null until the attempt is closed. */
  @Column(name = "is_correct")
  private Boolean correct;

  /** Required by JPA. */
  protected QuizAttemptAnswer() {}

  public QuizAttemptAnswer(Long attemptId, Long questionId, int position) {
    this.attemptId = attemptId;
    this.questionId = questionId;
    this.position = position;
  }

  public void mark(OptionKey selectedOption, boolean correct) {
    this.selectedOption = selectedOption;
    this.correct = correct;
  }

  public Long getAttemptId() {
    return attemptId;
  }

  public Long getQuestionId() {
    return questionId;
  }

  public int getPosition() {
    return position;
  }

  public OptionKey getSelectedOption() {
    return selectedOption;
  }

  public boolean isCorrect() {
    return Boolean.TRUE.equals(correct);
  }
}
