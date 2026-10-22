package com.campuscompanion.complaint;

import com.campuscompanion.audit.AuditAction;
import com.campuscompanion.audit.AuditService;
import com.campuscompanion.audit.AuditSnapshot;
import com.campuscompanion.common.error.ResourceNotFoundException;
import com.campuscompanion.common.web.PageResponse;
import com.campuscompanion.complaint.dto.AdminComplaintResponse;
import com.campuscompanion.complaint.dto.AdminComplaintResponse.StudentInfo;
import com.campuscompanion.complaint.dto.ComplaintHandleRequest;
import com.campuscompanion.student.StudentContext;
import com.campuscompanion.student.StudentProfileService;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Complaints as the administrator handles them (FR-CMP-004). The rule of a secure complaint is kept
 * here, in one place: for a secure complaint this service never reads {@link ComplaintOwner}, so no
 * response can carry the student (ADR 0013). A test asserts it.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminComplaintService {

  private final ComplaintRepository complaints;
  private final ComplaintOwnerRepository owners;
  private final ComplaintSupportRepository supports;
  private final StudentProfileService students;
  private final AuditService audit;
  private final Clock clock;

  public AdminComplaintService(
      ComplaintRepository complaints,
      ComplaintOwnerRepository owners,
      ComplaintSupportRepository supports,
      StudentProfileService students,
      AuditService audit,
      Clock clock) {
    this.complaints = complaints;
    this.owners = owners;
    this.supports = supports;
    this.students = students;
    this.audit = audit;
    this.clock = clock;
  }

  /** Open complaints first, then in review, then the rest; newest first inside each. */
  @Transactional(readOnly = true)
  public PageResponse<AdminComplaintResponse> list(
      ComplaintType type, ComplaintStatus status, int page, int size) {
    Page<Complaint> found = complaints.forAdmin(type, status, PageRequest.of(page, size));
    return new PageResponse<>(
        respond(found.getContent(), false),
        found.getNumber(),
        found.getSize(),
        found.getTotalElements(),
        found.getTotalPages());
  }

  /** One complaint, with its supporters when it is a public one. */
  @Transactional(readOnly = true)
  public AdminComplaintResponse one(Long id) {
    return respond(List.of(find(id)), true).get(0);
  }

  @Transactional
  public AdminComplaintResponse handle(Long id, ComplaintHandleRequest request) {
    Complaint complaint = find(id);
    String response =
        request.response() == null || request.response().isBlank()
            ? null
            : request.response().strip();
    AuditSnapshot before =
        new AuditSnapshot.ComplaintHandling(
            complaint.getStatus().name(), complaint.getAdminResponse() != null);
    complaint.handle(request.status(), response, clock.instant());
    complaints.flush();
    // The audit entry holds the status only, never the student.
    audit.record(
        AuditAction.COMPLAINT_UPDATED,
        "COMPLAINT",
        complaint.getId(),
        before,
        new AuditSnapshot.ComplaintHandling(
            complaint.getStatus().name(), complaint.getAdminResponse() != null));
    return respond(List.of(complaint), true).get(0);
  }

  private Complaint find(Long id) {
    return complaints.findById(id).orElseThrow(ResourceNotFoundException::new);
  }

  private List<AdminComplaintResponse> respond(List<Complaint> list, boolean withSupporters) {
    if (list.isEmpty()) {
      return List.of();
    }
    // Owners are read only for complaints that are not secure.
    List<Long> openIds =
        list.stream()
            .filter(c -> c.getType() != ComplaintType.SECURE)
            .map(Complaint::getId)
            .toList();
    Map<Long, Long> ownerOf = new HashMap<>();
    if (!openIds.isEmpty()) {
      owners
          .findByComplaintIdIn(openIds)
          .forEach(o -> ownerOf.put(o.getComplaintId(), o.getStudentId()));
    }
    List<Long> publicIds =
        list.stream()
            .filter(c -> c.getType() == ComplaintType.PUBLIC)
            .map(Complaint::getId)
            .toList();
    Map<Long, Integer> counts = new HashMap<>();
    if (!publicIds.isEmpty()) {
      for (Object[] row : supports.countsOf(publicIds)) {
        counts.put((Long) row[0], ((Long) row[1]).intValue());
      }
    }
    Map<Long, List<Long>> supporterIds = new HashMap<>();
    if (withSupporters) {
      for (Long id : publicIds) {
        supporterIds.put(id, supports.supportersOf(id));
      }
    }
    Set<Long> needed = new HashSet<>(ownerOf.values());
    supporterIds.values().forEach(needed::addAll);
    Map<Long, StudentContext> people = needed.isEmpty() ? Map.of() : students.studentsByIds(needed);
    return list.stream()
        .map(
            c -> {
              boolean secure = c.getType() == ComplaintType.SECURE;
              int count = counts.getOrDefault(c.getId(), 0);
              List<StudentInfo> backers =
                  supporterIds.getOrDefault(c.getId(), List.of()).stream()
                      .map(people::get)
                      .filter(java.util.Objects::nonNull)
                      .map(AdminComplaintService::info)
                      .toList();
              StudentContext owner = secure ? null : people.get(ownerOf.get(c.getId()));
              return new AdminComplaintResponse(
                  c.getId(),
                  c.getType(),
                  c.getCategory(),
                  c.getTitle(),
                  c.getDescription(),
                  c.getStatus(),
                  c.getAdminResponse(),
                  c.getRespondedAt(),
                  // A secure complaint's time is cut to the day, so it cannot be matched to a
                  // login.
                  secure ? c.getCreatedAt().truncatedTo(ChronoUnit.DAYS) : c.getCreatedAt(),
                  owner == null ? null : info(owner),
                  secure,
                  c.getType() == ComplaintType.PUBLIC && !c.isShowIdentity(),
                  count,
                  count >= ComplaintService.GROUP_THRESHOLD,
                  backers);
            })
        .collect(Collectors.toList());
  }

  private static StudentInfo info(StudentContext student) {
    return new StudentInfo(
        student.rollNo(),
        student.fullName(),
        student.branch(),
        student.currentSemester(),
        student.section());
  }
}
