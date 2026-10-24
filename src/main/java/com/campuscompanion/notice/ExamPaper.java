package com.campuscompanion.notice;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalDate;

/** One paper of an exam time table published as a notice: the day and the subject. */
@Embeddable
public class ExamPaper {

  @Column(name = "exam_date", nullable = false)
  private LocalDate examDate;

  @Column(nullable = false, length = 120)
  private String subject;

  protected ExamPaper() {}

  public ExamPaper(LocalDate examDate, String subject) {
    this.examDate = examDate;
    this.subject = subject;
  }

  public LocalDate getExamDate() {
    return examDate;
  }

  public String getSubject() {
    return subject;
  }
}
