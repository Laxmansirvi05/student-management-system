package com.campuscompanion.complaint;

import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ConflictException;
import com.campuscompanion.common.error.ErrorCode;
import com.campuscompanion.common.error.ResourceNotFoundException;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.common.web.PageResponse;
import com.campuscompanion.complaint.dto.ComplaintRequest;
import com.campuscompanion.complaint.dto.ComplaintResponse;
import com.campuscompanion.student.StudentContext;
import com.campuscompanion.student.StudentProfileService;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
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
 * Complaints as a student uses them (FR-CMP-001 to FR-CMP-003): raise one, read the public board,
 * read their own, support a public complaint, withdraw an open one. The student always comes from
 * the session, never from the request. See ADR 0013 for who sees what.
 */
@Service
@PreAuthorize("hasRole('STUDENT')")
public class ComplaintService {

  /** From this many supporters, a public complaint is marked a group complaint. */
  public static final int GROUP_THRESHOLD = 5;

  static final int DAILY_LIMIT = 5;

  private final ComplaintRepository complaints;
  private final ComplaintOwnerRepository owners;
  private final ComplaintSupportRepository supports;
  private final StudentProfileService students;
  private final Clock clock;

  public ComplaintService(
      ComplaintRepository complaints,
      ComplaintOwnerRepository owners,
      ComplaintSupportRepository supports,
      StudentProfileService students,
      Clock clock) {
    this.complaints = complaints;
    this.owners = owners;
    this.supports = supports;
    this.students = students;
    this.clock = clock;
  }

  @Transactional
  public ComplaintResponse raise(CurrentUser currentUser, ComplaintRequest request) {
    StudentContext student = students.currentStudent(currentUser);
    long today =
        complaints.raisedSince(student.studentId(), clock.instant().minus(Duration.ofDays(1)));
    if (today >= DAILY_LIMIT) {
      throw new ApiException(
          ErrorCode.RATE_LIMITED,
          "You can raise " + DAILY_LIMIT + " complaints a day. Please try again tomorrow.");
    }
    Complaint complaint =
        complaints.save(
            new Complaint(
                request.type(),
                request.category(),
                request.title().strip(),
                request.description().strip(),
                Boolean.TRUE.equals(request.showIdentity())));
    owners.save(new ComplaintOwner(complaint.getId(), student.studentId()));
    return respond(List.of(complaint), student, true).get(0);
  }

  /** The public board: newest first, or most supported first with {@code sort=top}. */
  @Transactional(readOnly = true)
  public PageResponse<ComplaintResponse> board(
      CurrentUser currentUser,
      ComplaintCategory category,
      ComplaintStatus status,
      String sort,
      int page,
      int size) {
    StudentContext student = students.currentStudent(currentUser);
    PageRequest paging = PageRequest.of(page, size);
    Page<Complaint> found =
        "top".equals(sort)
            ? complaints.boardTop(category, status, paging)
            : complaints.boardNewest(category, status, paging);
    return new PageResponse<>(
        respond(found.getContent(), student, false),
        found.getNumber(),
        found.getSize(),
        found.getTotalElements(),
        found.getTotalPages());
  }

  /** The student's own complaints of every type. */
  @Transactional(readOnly = true)
  public PageResponse<ComplaintResponse> mine(CurrentUser currentUser, int page, int size) {
    StudentContext student = students.currentStudent(currentUser);
    Page<Complaint> found = complaints.ownedBy(student.studentId(), PageRequest.of(page, size));
    return new PageResponse<>(
        respond(found.getContent(), student, true),
        found.getNumber(),
        found.getSize(),
        found.getTotalElements(),
        found.getTotalPages());
  }

  /** One complaint: a public one that is on the board, or one of the student's own. */
  @Transactional(readOnly = true)
  public ComplaintResponse one(CurrentUser currentUser, Long id) {
    StudentContext student = students.currentStudent(currentUser);
    Complaint complaint = complaints.findById(id).orElseThrow(ResourceNotFoundException::new);
    if (!complaint.isOnPublicBoard() && !isOwner(complaint, student)) {
      throw new ResourceNotFoundException();
    }
    return respond(List.of(complaint), student, false).get(0);
  }

  /** Deletes the student's own complaint while it is still open. */
  @Transactional
  public void withdraw(CurrentUser currentUser, Long id) {
    StudentContext student = students.currentStudent(currentUser);
    Complaint complaint = complaints.findById(id).orElseThrow(ResourceNotFoundException::new);
    if (!isOwner(complaint, student)) {
      throw new ResourceNotFoundException();
    }
    if (complaint.getStatus() != ComplaintStatus.OPEN) {
      throw new ConflictException("Only an open complaint can be withdrawn.");
    }
    complaints.delete(complaint);
  }

  /** Supports a public complaint. Doing it twice changes nothing. */
  @Transactional
  public ComplaintResponse support(CurrentUser currentUser, Long id) {
    StudentContext student = students.currentStudent(currentUser);
    Complaint complaint = onBoard(id);
    if (isOwner(complaint, student)) {
      throw new ConflictException("You cannot support your own complaint.");
    }
    if (complaint.getStatus() == ComplaintStatus.RESOLVED) {
      throw new ConflictException("This complaint is already resolved.");
    }
    supports.addIfMissing(id, student.studentId());
    return respond(List.of(complaint), student, false).get(0);
  }

  /** Takes the student's support back. Doing it when there is none changes nothing. */
  @Transactional
  public ComplaintResponse unsupport(CurrentUser currentUser, Long id) {
    StudentContext student = students.currentStudent(currentUser);
    Complaint complaint = onBoard(id);
    supports.remove(id, student.studentId());
    return respond(List.of(complaint), student, false).get(0);
  }

  // --- helpers ---------------------------------------------------------------------------------

  private Complaint onBoard(Long id) {
    Complaint complaint = complaints.findById(id).orElseThrow(ResourceNotFoundException::new);
    if (!complaint.isOnPublicBoard()) {
      throw new ResourceNotFoundException();
    }
    return complaint;
  }

  private boolean isOwner(Complaint complaint, StudentContext student) {
    return owners
        .findById(complaint.getId())
        .map(owner -> owner.getStudentId().equals(student.studentId()))
        .orElse(false);
  }

  /**
   * Builds the responses. The author's roll number is looked up only for a public complaint that
   * shows it. {@code ownList} is true when every complaint is the student's own.
   */
  private List<ComplaintResponse> respond(
      List<Complaint> list, StudentContext viewer, boolean ownList) {
    if (list.isEmpty()) {
      return List.of();
    }
    List<Long> ids = list.stream().map(Complaint::getId).toList();
    Map<Long, Long> ownerOf =
        owners.findByComplaintIdIn(ids).stream()
            .collect(
                Collectors.toMap(ComplaintOwner::getComplaintId, ComplaintOwner::getStudentId));
    Map<Long, Integer> supporterCount = new HashMap<>();
    for (Object[] row : supports.countsOf(ids)) {
      supporterCount.put((Long) row[0], ((Long) row[1]).intValue());
    }
    Set<Long> supported = Set.copyOf(supports.supportedAmong(viewer.studentId(), ids));
    Collection<Long> shown =
        list.stream()
            .filter(c -> c.getType() == ComplaintType.PUBLIC && c.isShowIdentity())
            .map(c -> ownerOf.get(c.getId()))
            .toList();
    Map<Long, StudentContext> authors = shown.isEmpty() ? Map.of() : students.studentsByIds(shown);
    return list.stream()
        .map(
            c -> {
              Long ownerId = ownerOf.get(c.getId());
              boolean mine = ownList || viewer.studentId().equals(ownerId);
              StudentContext author =
                  c.getType() == ComplaintType.PUBLIC && c.isShowIdentity()
                      ? authors.get(ownerId)
                      : null;
              int count =
                  c.getType() == ComplaintType.PUBLIC
                      ? supporterCount.getOrDefault(c.getId(), 0)
                      : 0;
              return new ComplaintResponse(
                  c.getId(),
                  c.getType(),
                  c.getCategory(),
                  c.getTitle(),
                  c.getDescription(),
                  c.getStatus(),
                  c.getAdminResponse(),
                  c.getRespondedAt(),
                  c.getCreatedAt(),
                  author == null ? null : author.rollNo(),
                  count,
                  count >= GROUP_THRESHOLD,
                  supported.contains(c.getId()),
                  mine);
            })
        .toList();
  }
}
