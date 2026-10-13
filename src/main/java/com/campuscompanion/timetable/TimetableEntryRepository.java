package com.campuscompanion.timetable;

import java.time.LocalTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimetableEntryRepository extends JpaRepository<TimetableEntry, Long> {

  /** The whole week of one section, in day and time order. */
  List<TimetableEntry> findByBranchAndSemesterAndSectionOrderByDayOfWeekAscStartTimeAsc(
      String branch, int semester, String section);

  /** One day of one section, in time order. */
  List<TimetableEntry> findByBranchAndSemesterAndSectionAndDayOfWeekOrderByStartTimeAsc(
      String branch, int semester, String section, int dayOfWeek);

  /** The admin's list. Every filter is optional (null means "any"). */
  @Query(
      """
      select t from TimetableEntry t
      where (:branch is null or t.branch = :branch)
        and (:semester is null or t.semester = :semester)
        and (:section is null or t.section = :section)
      order by t.branch, t.semester, t.section, t.dayOfWeek, t.startTime
      """)
  List<TimetableEntry> findFiltered(
      @Param("branch") String branch,
      @Param("semester") Integer semester,
      @Param("section") String section);

  /**
   * True when another class of the same section, on the same day, shares any time with the given
   * one. Two classes overlap when each starts before the other ends. excludeId leaves out the entry
   * being edited; pass -1 for a new entry.
   */
  @Query(
      """
      select count(t) > 0 from TimetableEntry t
      where t.branch = :branch and t.semester = :semester and t.section = :section
        and t.dayOfWeek = :dayOfWeek
        and t.startTime < :endTime and t.endTime > :startTime
        and t.id <> :excludeId
      """)
  boolean overlaps(
      @Param("branch") String branch,
      @Param("semester") int semester,
      @Param("section") String section,
      @Param("dayOfWeek") int dayOfWeek,
      @Param("startTime") LocalTime startTime,
      @Param("endTime") LocalTime endTime,
      @Param("excludeId") long excludeId);
}
