package com.campuscompanion.student;

import com.campuscompanion.auth.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A student's academic identity, linked one to one with a login account. The roll number is the
 * academic identifier, stored in upper case. There is no email here: the institutional email is the
 * account's login email.
 */
@Entity
@Table(name = "student_profiles")
public class StudentProfile {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false, unique = true, updatable = false)
  private User user;

  @Column(name = "roll_no", nullable = false, length = 20, updatable = false)
  private String rollNo;

  @Column(name = "full_name", nullable = false, length = 120)
  private String fullName;

  @Column(nullable = false, length = 20)
  private String branch;

  @JdbcTypeCode(SqlTypes.SMALLINT)
  @Column(name = "current_semester", nullable = false)
  private int currentSemester;

  @Column(nullable = false, length = 5)
  private String section;

  @JdbcTypeCode(SqlTypes.SMALLINT)
  @Column(name = "admission_year", nullable = false)
  private int admissionYear;

  @Column(length = 20)
  private String phone;

  /** A few lines the student wrote about themselves. Optional. */
  @Column(length = 280)
  private String bio;

  /** Where the student's photo is kept in file storage, or null when there is none. */
  @Column(name = "photo_path", length = 300)
  private String photoPath;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Required by JPA. */
  protected StudentProfile() {}

  public StudentProfile(
      User user,
      String rollNo,
      String fullName,
      String branch,
      int currentSemester,
      String section,
      int admissionYear,
      String phone) {
    this.user = user;
    this.rollNo = rollNo;
    this.fullName = fullName;
    this.branch = branch;
    this.currentSemester = currentSemester;
    this.section = section;
    this.admissionYear = admissionYear;
    this.phone = phone;
  }

  /** Replaces the fields an admin may edit. The roll number and the account never change. */
  public void update(
      String fullName,
      String branch,
      int currentSemester,
      String section,
      int admissionYear,
      String phone) {
    this.fullName = fullName;
    this.branch = branch;
    this.currentSemester = currentSemester;
    this.section = section;
    this.admissionYear = admissionYear;
    this.phone = phone;
  }

  /**
   * Replaces the personal details a student may edit themselves. Everything the college decides
   * (name, roll number, branch, semester, section, admission year) is not touched.
   */
  public void updateOwnDetails(String phone, String bio) {
    this.phone = phone;
    this.bio = bio;
  }

  public void setPhotoPath(String photoPath) {
    this.photoPath = photoPath;
  }

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public String getRollNo() {
    return rollNo;
  }

  public String getFullName() {
    return fullName;
  }

  public String getBranch() {
    return branch;
  }

  public int getCurrentSemester() {
    return currentSemester;
  }

  public String getSection() {
    return section;
  }

  public int getAdmissionYear() {
    return admissionYear;
  }

  public String getPhone() {
    return phone;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public String getBio() {
    return bio;
  }

  public String getPhotoPath() {
    return photoPath;
  }
}
