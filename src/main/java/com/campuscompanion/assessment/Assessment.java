package com.campuscompanion.assessment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Something that is marked in a subject, for example "Internal 1". Students see its marks only
 * after it is published.
 */
@Entity
@Table(name = "assessments")
public class Assessment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "subject_id", nullable = false)
  private Long subjectId;

  @Column(nullable = false, length = 100)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "assessment_type", nullable = false, length = 20)
  private AssessmentType type;

  @Column(name = "max_marks", nullable = false, precision = 5, scale = 2)
  private BigDecimal maxMarks;

  @Column(name = "held_on")
  private LocalDate heldOn;

  @Column(name = "academic_year", nullable = false, length = 9)
  private String academicYear;

  @Column(nullable = false)
  private boolean published;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  /** Required by JPA. */
  protected Assessment() {}

  public Assessment(
      Long subjectId,
      String name,
      AssessmentType type,
      BigDecimal maxMarks,
      LocalDate heldOn,
      String academicYear,
      boolean published) {
    update(subjectId, name, type, maxMarks, heldOn, academicYear, published);
  }

  public void update(
      Long subjectId,
      String name,
      AssessmentType type,
      BigDecimal maxMarks,
      LocalDate heldOn,
      String academicYear,
      boolean published) {
    this.subjectId = subjectId;
    this.name = name;
    this.type = type;
    this.maxMarks = maxMarks;
    this.heldOn = heldOn;
    this.academicYear = academicYear;
    this.published = published;
  }

  public Long getId() {
    return id;
  }

  public Long getSubjectId() {
    return subjectId;
  }

  public String getName() {
    return name;
  }

  public AssessmentType getType() {
    return type;
  }

  public BigDecimal getMaxMarks() {
    return maxMarks;
  }

  public LocalDate getHeldOn() {
    return heldOn;
  }

  public String getAcademicYear() {
    return academicYear;
  }

  public boolean isPublished() {
    return published;
  }
}
