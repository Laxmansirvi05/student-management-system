package com.campuscompanion.subject;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

  Optional<Subject> findByCode(String code);

  boolean existsByCodeAndIdNot(String code, Long id);

  List<Subject> findByBranchAndSemesterAndActiveTrueOrderByCode(String branch, int semester);

  /** Active subjects, optionally narrowed to a branch and a semester. Null means "any". */
  @Query(
      """
      select s from Subject s
      where s.active = true
        and (:branch is null or s.branch = :branch)
        and (:semester is null or s.semester = :semester)
      order by s.semester, s.code
      """)
  List<Subject> findActive(@Param("branch") String branch, @Param("semester") Integer semester);

  /** Every subject, active or not, for the admin screens. Null means "any". */
  @Query(
      """
      select s from Subject s
      where (:branch is null or s.branch = :branch)
        and (:semester is null or s.semester = :semester)
      order by s.branch, s.semester, s.code
      """)
  List<Subject> findAllFiltered(
      @Param("branch") String branch, @Param("semester") Integer semester);
}
