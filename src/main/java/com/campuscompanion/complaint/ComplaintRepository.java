package com.campuscompanion.complaint;

import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

  /** The public board, newest first: public complaints that are not rejected. */
  @Query(
      """
      select c from Complaint c
      where c.type = com.campuscompanion.complaint.ComplaintType.PUBLIC
        and c.status <> com.campuscompanion.complaint.ComplaintStatus.REJECTED
        and (:category is null or c.category = :category)
        and (:status is null or c.status = :status)
      order by c.createdAt desc, c.id desc
      """)
  Page<Complaint> boardNewest(
      @Param("category") ComplaintCategory category,
      @Param("status") ComplaintStatus status,
      Pageable pageable);

  /** The public board, most supported first. */
  @Query(
      """
      select c from Complaint c
      where c.type = com.campuscompanion.complaint.ComplaintType.PUBLIC
        and c.status <> com.campuscompanion.complaint.ComplaintStatus.REJECTED
        and (:category is null or c.category = :category)
        and (:status is null or c.status = :status)
      order by (select count(s) from ComplaintSupport s where s.complaintId = c.id) desc,
               c.createdAt desc, c.id desc
      """)
  Page<Complaint> boardTop(
      @Param("category") ComplaintCategory category,
      @Param("status") ComplaintStatus status,
      Pageable pageable);

  /** A student's own complaints of every type, newest first. */
  @Query(
      """
      select c from Complaint c
      where c.id in (select o.complaintId from ComplaintOwner o where o.studentId = :studentId)
      order by c.createdAt desc, c.id desc
      """)
  Page<Complaint> ownedBy(@Param("studentId") Long studentId, Pageable pageable);

  /** How many complaints the student raised since the given time, for the daily limit. */
  @Query(
      """
      select count(c) from Complaint c
      where c.createdAt >= :since
        and c.id in (select o.complaintId from ComplaintOwner o where o.studentId = :studentId)
      """)
  long raisedSince(@Param("studentId") Long studentId, @Param("since") Instant since);

  /** Every complaint, for the administrator. All filters are optional. */
  @Query(
      """
      select c from Complaint c
      where (:type is null or c.type = :type)
        and (:status is null or c.status = :status)
      order by case c.status when com.campuscompanion.complaint.ComplaintStatus.OPEN then 0
                             when com.campuscompanion.complaint.ComplaintStatus.IN_REVIEW then 1
                             else 2 end,
               c.createdAt desc, c.id desc
      """)
  Page<Complaint> forAdmin(
      @Param("type") ComplaintType type,
      @Param("status") ComplaintStatus status,
      Pageable pageable);
}
