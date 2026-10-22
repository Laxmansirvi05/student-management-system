package com.campuscompanion.complaint;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ComplaintSupportRepository
    extends JpaRepository<ComplaintSupport, ComplaintSupport.Key> {

  /** Supporting twice does nothing, even when two requests arrive together. */
  @Modifying
  @Query(
      value =
          "insert into app.complaint_supports (complaint_id, student_id)"
              + " values (:complaintId, :studentId) on conflict do nothing",
      nativeQuery = true)
  void addIfMissing(@Param("complaintId") Long complaintId, @Param("studentId") Long studentId);

  @Modifying
  @Query(
      "delete from ComplaintSupport s where s.complaintId = :complaintId"
          + " and s.studentId = :studentId")
  void remove(@Param("complaintId") Long complaintId, @Param("studentId") Long studentId);

  /** Rows of complaint ID and number of supporters. */
  @Query(
      "select s.complaintId, count(s) from ComplaintSupport s"
          + " where s.complaintId in :ids group by s.complaintId")
  List<Object[]> countsOf(@Param("ids") Collection<Long> ids);

  /** Which of these complaints the student supports. */
  @Query(
      "select s.complaintId from ComplaintSupport s"
          + " where s.studentId = :studentId and s.complaintId in :ids")
  List<Long> supportedAmong(@Param("studentId") Long studentId, @Param("ids") Collection<Long> ids);

  @Query("select s.studentId from ComplaintSupport s where s.complaintId = :complaintId")
  List<Long> supportersOf(@Param("complaintId") Long complaintId);
}
