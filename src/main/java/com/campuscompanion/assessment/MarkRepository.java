package com.campuscompanion.assessment;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarkRepository extends JpaRepository<Mark, Long> {

  /** One student's marks in these assessments. The student ID always comes from the session. */
  List<Mark> findByStudentIdAndAssessmentIdIn(Long studentId, Collection<Long> assessmentIds);

  List<Mark> findByAssessmentId(Long assessmentId);

  /** The highest marks saved for an assessment, or null when none are saved. */
  @Query("select max(m.marksObtained) from Mark m where m.assessmentId = :assessmentId")
  BigDecimal highestMarks(@Param("assessmentId") Long assessmentId);
}
