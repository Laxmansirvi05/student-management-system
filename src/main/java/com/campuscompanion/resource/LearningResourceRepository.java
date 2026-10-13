package com.campuscompanion.resource;

import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LearningResourceRepository extends JpaRepository<LearningResource, Long> {

  /**
   * The resource search. Every filter is optional (null means "any"), and all the filters given
   * must match. The search text must already be lower case and wrapped in percent signs. To limit
   * the search to some subjects (a semester), pass anySubject = false and their IDs.
   */
  @Query(
      """
      select r from LearningResource r
      where (:status is null or r.status = :status)
        and (:subjectId is null or r.subjectId = :subjectId)
        and (:anySubject = true or r.subjectId in :subjectIds)
        and (:unitId is null or r.unitId = :unitId)
        and (:type is null or r.resourceType = :type)
        and (:search is null or lower(r.title) like :search
             or lower(coalesce(r.description, '')) like :search)
      """)
  Page<LearningResource> search(
      @Param("status") ResourceStatus status,
      @Param("subjectId") Long subjectId,
      @Param("anySubject") boolean anySubject,
      @Param("subjectIds") Collection<Long> subjectIds,
      @Param("unitId") Long unitId,
      @Param("type") ResourceType type,
      @Param("search") String search,
      Pageable pageable);

  /** Published question papers and model papers; subject, exam year and exam type are optional. */
  @Query(
      """
      select r from LearningResource r
      where r.status = com.campuscompanion.resource.ResourceStatus.PUBLISHED
        and r.id in (select q.resourceId from QuestionPaper q
                     where (:examYear is null or q.examYear = :examYear)
                       and (:examType is null or q.examType = :examType))
        and (:subjectId is null or r.subjectId = :subjectId)
        and (:search is null or lower(r.title) like :search
             or lower(coalesce(r.description, '')) like :search)
      """)
  Page<LearningResource> searchPapers(
      @Param("subjectId") Long subjectId,
      @Param("examYear") Integer examYear,
      @Param("examType") ExamType examType,
      @Param("search") String search,
      Pageable pageable);

  /** The published resources a student has bookmarked. A hidden resource drops out by itself. */
  @Query(
      """
      select r from LearningResource r
      where r.status = com.campuscompanion.resource.ResourceStatus.PUBLISHED
        and r.id in (select b.resourceId from Bookmark b where b.studentId = :studentId)
      """)
  Page<LearningResource> findBookmarked(@Param("studentId") Long studentId, Pageable pageable);
}
