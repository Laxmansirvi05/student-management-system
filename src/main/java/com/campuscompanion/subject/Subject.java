package com.campuscompanion.subject;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A subject taught to one branch in one semester. Subjects are deactivated, never deleted. */
@Entity
@Table(name = "subjects")
public class Subject {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 20)
  private String code;

  @Column(nullable = false, length = 150)
  private String name;

  @Column(nullable = false, length = 20)
  private String branch;

  @JdbcTypeCode(SqlTypes.SMALLINT)
  @Column(nullable = false)
  private int semester;

  @JdbcTypeCode(SqlTypes.SMALLINT)
  @Column(nullable = false)
  private int credits;

  @Column(nullable = false)
  private boolean active = true;

  /** Required by JPA. */
  protected Subject() {}

  public Subject(String code, String name, String branch, int semester, int credits) {
    this.code = code;
    this.name = name;
    this.branch = branch;
    this.semester = semester;
    this.credits = credits;
  }

  public Long getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public String getBranch() {
    return branch;
  }

  public int getSemester() {
    return semester;
  }

  public int getCredits() {
    return credits;
  }

  public boolean isActive() {
    return active;
  }
}
