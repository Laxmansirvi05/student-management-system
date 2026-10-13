package com.campuscompanion.subject;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A numbered unit of a subject. Resources and quiz questions are filed under units. */
@Entity
@Table(name = "subject_units")
public class SubjectUnit {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "subject_id", nullable = false, updatable = false)
  private Subject subject;

  @JdbcTypeCode(SqlTypes.SMALLINT)
  @Column(name = "unit_no", nullable = false)
  private int unitNo;

  @Column(nullable = false, length = 200)
  private String title;

  /** Required by JPA. */
  protected SubjectUnit() {}

  public SubjectUnit(Subject subject, int unitNo, String title) {
    this.subject = subject;
    this.unitNo = unitNo;
    this.title = title;
  }

  public Long getId() {
    return id;
  }

  public Subject getSubject() {
    return subject;
  }

  public int getUnitNo() {
    return unitNo;
  }

  public String getTitle() {
    return title;
  }
}
