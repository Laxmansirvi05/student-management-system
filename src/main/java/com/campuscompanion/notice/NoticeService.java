package com.campuscompanion.notice;

import com.campuscompanion.auth.Role;
import com.campuscompanion.common.config.CampusTime;
import com.campuscompanion.common.error.ResourceNotFoundException;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.common.storage.StorageClient;
import com.campuscompanion.common.web.PageResponse;
import com.campuscompanion.notice.dto.NoticeDocumentLinkResponse;
import com.campuscompanion.notice.dto.NoticeResponse;
import com.campuscompanion.student.StudentContext;
import com.campuscompanion.student.StudentProfileService;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reading notices (FR-NOT-001). A student sees published notices for everyone and those targeted at
 * their own branch and semester. The filter is part of the database query, so a draft or a notice
 * for another class is never loaded. An admin reading here sees every published notice.
 */
@Service
@Transactional(readOnly = true)
public class NoticeService {

  private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "publishedAt", "id");
  static final Duration LINK_LIFETIME = Duration.ofMinutes(5);

  private final NoticeRepository notices;
  private final StudentProfileService students;
  private final StorageClient storage;
  private final Clock clock;

  public NoticeService(
      NoticeRepository notices,
      StudentProfileService students,
      StorageClient storage,
      Clock clock) {
    this.notices = notices;
    this.students = students;
    this.storage = storage;
    this.clock = clock;
  }

  public PageResponse<NoticeResponse> visibleTo(
      CurrentUser currentUser, NoticeCategory category, int page, int size) {
    Audience audience = audienceOf(currentUser);
    return PageResponse.of(
        notices
            .findVisible(
                audience.branch(),
                audience.semester(),
                category,
                PageRequest.of(page, size, NEWEST_FIRST))
            .map(NoticeService::toResponse));
  }

  /** One notice, or "not found" when it is a draft or meant for another class. */
  public NoticeResponse oneVisibleTo(CurrentUser currentUser, Long id) {
    Audience audience = audienceOf(currentUser);
    return notices
        .findVisibleById(id, audience.branch(), audience.semester())
        .map(NoticeService::toResponse)
        .orElseThrow(ResourceNotFoundException::new);
  }

  /**
   * A five-minute link to the official document of a notice the user may see. "Not found" when the
   * notice is hidden from them or has no document. With {@code download}, the file is saved under a
   * readable name instead of being shown.
   */
  public NoticeDocumentLinkResponse documentLink(
      CurrentUser currentUser, Long id, boolean download) {
    Audience audience = audienceOf(currentUser);
    Notice notice =
        notices
            .findVisibleById(id, audience.branch(), audience.semester())
            .orElseThrow(ResourceNotFoundException::new);
    return linkTo(notice, download, storage);
  }

  static NoticeDocumentLinkResponse linkTo(Notice notice, boolean download, StorageClient storage) {
    if (notice.getDocumentPath() == null) {
      throw new ResourceNotFoundException();
    }
    StorageClient.SignedLink link =
        storage.signedUrl(
            notice.getDocumentPath(), LINK_LIFETIME, download ? downloadName(notice) : null);
    return new NoticeDocumentLinkResponse(link.url(), link.expiresAt(), notice.getDocumentType());
  }

  /** "Notice - Dussehra vacation 19 to 25 October 2026.pdf": only plain characters are kept. */
  static String downloadName(Notice notice) {
    String title =
        notice.getTitle().replaceAll("[^A-Za-z0-9 ,()&-]", " ").replaceAll("\\s+", " ").strip();
    if (title.length() > 120) {
      title = title.substring(0, 120).strip();
    }
    return "Notice - "
        + title
        + ("application/pdf".equals(notice.getDocumentType()) ? ".pdf" : ".jpg");
  }

  /** The newest notices a student may see, for the dashboard. */
  public List<NoticeResponse> recentFor(StudentContext student, int limit) {
    return notices
        .findVisible(
            student.branch(),
            (short) student.currentSemester(),
            null,
            PageRequest.of(0, limit, NEWEST_FIRST))
        .map(NoticeService::toResponse)
        .getContent();
  }

  /** Notices due from today up to the given number of days ahead, soonest first. */
  public List<NoticeResponse> deadlinesFor(StudentContext student, int daysAhead) {
    LocalDate today = CampusTime.today(clock);
    return notices
        .findDeadlines(
            student.branch(), (short) student.currentSemester(), today, today.plusDays(daysAhead))
        .stream()
        .map(NoticeService::toResponse)
        .toList();
  }

  /** Whose notices to show: a student's class, or everything for an admin. */
  private record Audience(String branch, Short semester) {}

  private Audience audienceOf(CurrentUser currentUser) {
    if (currentUser.role() != Role.STUDENT) {
      return new Audience(null, null);
    }
    StudentContext student = students.currentStudent(currentUser);
    return new Audience(student.branch(), (short) student.currentSemester());
  }

  static NoticeResponse toResponse(Notice notice) {
    return new NoticeResponse(
        notice.getId(),
        notice.getTitle(),
        notice.getBody(),
        notice.getCategory(),
        notice.getTargetBranch(),
        notice.getTargetSemester(),
        notice.getDueDate(),
        notice.getStatus(),
        notice.getPublishedAt(),
        notice.getDocumentType());
  }
}
