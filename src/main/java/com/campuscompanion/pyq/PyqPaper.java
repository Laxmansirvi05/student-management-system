package com.campuscompanion.pyq;

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

/**
 * One previous-year question paper: which branch and subject, which sitting, and where the PDF is
 * kept. The PDF's bytes are in file storage; this row holds only the details and the path.
 */
@Entity
@Table(name = "pyq_papers")
public class PyqPaper {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 10)
  private String branch;

  @Column(name = "subject_name", nullable = false, length = 120)
  private String subjectName;

  @JdbcTypeCode(SqlTypes.SMALLINT)
  @Column(name = "exam_year", nullable = false)
  private int examYear;

  @JdbcTypeCode(SqlTypes.SMALLINT)
  @Column(name = "exam_month", nullable = false)
  private int examMonth;

  @Enumerated(EnumType.STRING)
  @Column(name = "exam_kind", nullable = false, length = 10)
  private PyqExamKind examKind;

  @JdbcTypeCode(SqlTypes.SMALLINT)
  @Column(name = "mid_number")
  private Integer midNumber;

  @Column(length = 10)
  private String regulation;

  @Column(name = "course_code", length = 40)
  private String courseCode;

  @JdbcTypeCode(SqlTypes.SMALLINT)
  @Column(nullable = false)
  private int variant;

  @Column(name = "storage_path", nullable = false, updatable = false, length = 300)
  private String storagePath;

  @Column(name = "size_bytes", nullable = false, updatable = false)
  private int sizeBytes;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "checksum_sha256", nullable = false, updatable = false, length = 64)
  private String checksumSha256;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private PyqStatus status = PyqStatus.PUBLISHED;

  @Column(name = "download_count", nullable = false)
  private int downloadCount;

  @Column(name = "created_by", updatable = false)
  private Long createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  /** Required by JPA. */
  protected PyqPaper() {}

  public PyqPaper(
      Details details, String storagePath, int sizeBytes, String checksumSha256, Long createdBy) {
    describe(details, PyqStatus.PUBLISHED);
    this.storagePath = storagePath;
    this.sizeBytes = sizeBytes;
    this.checksumSha256 = checksumSha256;
    this.createdBy = createdBy;
  }

  /** What an admin can say about a paper, already checked and tidied. */
  public record Details(
      String branch,
      String subjectName,
      int examYear,
      int examMonth,
      PyqExamKind examKind,
      Integer midNumber,
      String regulation,
      String courseCode,
      int variant) {}

  public void describe(Details details, PyqStatus status) {
    this.branch = details.branch();
    this.subjectName = details.subjectName();
    this.examYear = details.examYear();
    this.examMonth = details.examMonth();
    this.examKind = details.examKind();
    this.midNumber = details.midNumber();
    this.regulation = details.regulation();
    this.courseCode = details.courseCode();
    this.variant = details.variant();
    this.status = status;
    this.updatedAt = Instant.now();
  }

  public boolean isPublished() {
    return status == PyqStatus.PUBLISHED;
  }

  public Long getId() {
    return id;
  }

  public String getBranch() {
    return branch;
  }

  public String getSubjectName() {
    return subjectName;
  }

  public int getExamYear() {
    return examYear;
  }

  public int getExamMonth() {
    return examMonth;
  }

  public PyqExamKind getExamKind() {
    return examKind;
  }

  public Integer getMidNumber() {
    return midNumber;
  }

  public String getRegulation() {
    return regulation;
  }

  public String getCourseCode() {
    return courseCode;
  }

  public int getVariant() {
    return variant;
  }

  public String getStoragePath() {
    return storagePath;
  }

  public int getSizeBytes() {
    return sizeBytes;
  }

  public PyqStatus getStatus() {
    return status;
  }

  public int getDownloadCount() {
    return downloadCount;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
