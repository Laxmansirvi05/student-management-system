package com.campuscompanion.complaint.dto;

import com.campuscompanion.complaint.ComplaintCategory;
import com.campuscompanion.complaint.ComplaintStatus;
import com.campuscompanion.complaint.ComplaintType;
import java.time.Instant;
import java.util.List;

/**
 * A complaint as the administrator sees it. {@code student} is null for a secure complaint, always:
 * that is the promise of a secure complaint. Its time is cut to the day.
 */
public record AdminComplaintResponse(
    Long id,
    ComplaintType type,
    ComplaintCategory category,
    String title,
    String description,
    ComplaintStatus status,
    String adminResponse,
    Instant respondedAt,
    Instant createdAt,
    StudentInfo student,
    boolean identityProtected,
    boolean hiddenFromStudents,
    int supporters,
    boolean group,
    List<StudentInfo> supportedBy) {

  public record StudentInfo(
      String rollNo, String fullName, String branch, int semester, String section) {}
}
