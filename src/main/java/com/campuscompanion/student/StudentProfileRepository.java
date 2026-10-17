package com.campuscompanion.student;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {

  /** Finds the profile that belongs to a login account. */
  Optional<StudentProfile> findByUserId(Long userId);

  /** The roll number must already be in upper case. */
  boolean existsByRollNo(String rollNo);

  /** Roll numbers must already be in upper case. */
  java.util.List<StudentProfile> findByRollNoIn(java.util.Collection<String> rollNos);

  /** The students of one class, in roll-number order, for attendance and marks sheets. */
  List<StudentProfile> findByBranchAndCurrentSemesterAndSectionOrderByRollNo(
      String branch, int currentSemester, String section);

  /** The students of a branch and semester, across sections, in roll-number order. */
  List<StudentProfile> findByBranchAndCurrentSemesterOrderByRollNo(
      String branch, int currentSemester);

  /**
   * The admin's student list. Every filter is optional (null means "any"). The search text must
   * already be lower case and wrapped in percent signs; it matches roll number, name or email.
   */
  @Query(
      value =
          """
          select p from StudentProfile p join fetch p.user u
          where (:branch is null or p.branch = :branch)
            and (:semester is null or p.currentSemester = :semester)
            and (:section is null or p.section = :section)
            and (:search is null or lower(p.rollNo) like :search
                 or lower(p.fullName) like :search or u.loginEmail like :search)
          """,
      countQuery =
          """
          select count(p) from StudentProfile p join p.user u
          where (:branch is null or p.branch = :branch)
            and (:semester is null or p.currentSemester = :semester)
            and (:section is null or p.section = :section)
            and (:search is null or lower(p.rollNo) like :search
                 or lower(p.fullName) like :search or u.loginEmail like :search)
          """)
  Page<StudentProfile> search(
      @Param("branch") String branch,
      @Param("semester") Integer semester,
      @Param("section") String section,
      @Param("search") String search,
      Pageable pageable);
}
