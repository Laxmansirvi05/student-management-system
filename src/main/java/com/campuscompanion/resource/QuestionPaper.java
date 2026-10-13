package com.campuscompanion.resource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The exam details of a resource that is a question paper or a model paper. */
@Entity
@Table(name = "question_papers")
public class QuestionPaper {

  /** The same ID as the resource it describes. */
  @Id
  @Column(name = "resource_id")
  private Long resourceId;

  @JdbcTypeCode(SqlTypes.SMALLINT)
  @Column(name = "exam_year", nullable = false)
  private int examYear;

  @Enumerated(EnumType.STRING)
  @Column(name = "exam_type", nullable = false, length = 20)
  private ExamType examType;

  @Column(name = "max_marks")
  private Short maxMarks;

  /** Required by JPA. */
  protected QuestionPaper() {}

  public QuestionPaper(Long resourceId, int examYear, ExamType examType, Integer maxMarks) {
    this.resourceId = resourceId;
    update(examYear, examType, maxMarks);
  }

  public void update(int examYear, ExamType examType, Integer maxMarks) {
    this.examYear = examYear;
    this.examType = examType;
    this.maxMarks = maxMarks == null ? null : maxMarks.shortValue();
  }

  public Long getResourceId() {
    return resourceId;
  }

  public int getExamYear() {
    return examYear;
  }

  public ExamType getExamType() {
    return examType;
  }

  public Integer getMaxMarks() {
    return maxMarks == null ? null : maxMarks.intValue();
  }
}
