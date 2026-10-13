package com.campuscompanion.timetable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One class in the weekly timetable of a section. Day 1 is Monday. */
@Entity
@Table(name = "timetable_entries")
public class TimetableEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 20)
  private String branch;

  @JdbcTypeCode(SqlTypes.SMALLINT)
  @Column(nullable = false)
  private int semester;

  @Column(nullable = false, length = 5)
  private String section;

  @JdbcTypeCode(SqlTypes.SMALLINT)
  @Column(name = "day_of_week", nullable = false)
  private int dayOfWeek;

  @Column(name = "start_time", nullable = false)
  private LocalTime startTime;

  @Column(name = "end_time", nullable = false)
  private LocalTime endTime;

  @Column(name = "subject_id", nullable = false)
  private Long subjectId;

  @Column(length = 30)
  private String room;

  @Column(name = "faculty_name", length = 120)
  private String facultyName;

  /** Required by JPA. */
  protected TimetableEntry() {}

  public TimetableEntry(
      String branch,
      int semester,
      String section,
      int dayOfWeek,
      LocalTime startTime,
      LocalTime endTime,
      Long subjectId,
      String room,
      String facultyName) {
    update(branch, semester, section, dayOfWeek, startTime, endTime, subjectId, room, facultyName);
  }

  public void update(
      String branch,
      int semester,
      String section,
      int dayOfWeek,
      LocalTime startTime,
      LocalTime endTime,
      Long subjectId,
      String room,
      String facultyName) {
    this.branch = branch;
    this.semester = semester;
    this.section = section;
    this.dayOfWeek = dayOfWeek;
    this.startTime = startTime;
    this.endTime = endTime;
    this.subjectId = subjectId;
    this.room = room;
    this.facultyName = facultyName;
  }

  public Long getId() {
    return id;
  }

  public String getBranch() {
    return branch;
  }

  public int getSemester() {
    return semester;
  }

  public String getSection() {
    return section;
  }

  public int getDayOfWeek() {
    return dayOfWeek;
  }

  public LocalTime getStartTime() {
    return startTime;
  }

  public LocalTime getEndTime() {
    return endTime;
  }

  public Long getSubjectId() {
    return subjectId;
  }

  public String getRoom() {
    return room;
  }

  public String getFacultyName() {
    return facultyName;
  }
}
