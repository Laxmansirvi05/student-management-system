package com.campuscompanion.notice;

import com.campuscompanion.common.error.ResourceNotFoundException;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.common.web.PageResponse;
import com.campuscompanion.notice.dto.NoticeResponse;
import com.campuscompanion.notice.dto.NoticeWriteRequest;
import java.time.Clock;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Managing notices (FR-ADM-006): write a draft, edit it, publish it, take it back to a draft, or
 * delete it.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminNoticeService {

  private final NoticeRepository notices;
  private final Clock clock;

  public AdminNoticeService(NoticeRepository notices, Clock clock) {
    this.notices = notices;
    this.clock = clock;
  }

  /** Every notice, drafts included, newest first. */
  @Transactional(readOnly = true)
  public PageResponse<NoticeResponse> list(int page, int size) {
    return PageResponse.of(
        notices
            .findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")))
            .map(NoticeService::toResponse));
  }

  /** A new notice always starts as a draft. */
  @Transactional
  public NoticeResponse create(NoticeWriteRequest request, CurrentUser admin) {
    Notice notice =
        new Notice(
            request.title().strip(),
            request.body().strip(),
            request.category(),
            branch(request.targetBranch()),
            request.targetSemester(),
            request.dueDate(),
            admin.id());
    return NoticeService.toResponse(notices.save(notice));
  }

  @Transactional
  public NoticeResponse update(Long id, NoticeWriteRequest request) {
    Notice notice = find(id);
    notice.update(
        request.title().strip(),
        request.body().strip(),
        request.category(),
        branch(request.targetBranch()),
        request.targetSemester(),
        request.dueDate());
    return NoticeService.toResponse(notice);
  }

  @Transactional
  public void publish(Long id) {
    Notice notice = find(id);
    if (notice.getStatus() != NoticeStatus.PUBLISHED) {
      notice.publish(clock.instant());
    }
  }

  @Transactional
  public void unpublish(Long id) {
    find(id).unpublish(clock.instant());
  }

  @Transactional
  public void delete(Long id) {
    notices.delete(find(id));
  }

  private Notice find(Long id) {
    return notices.findById(id).orElseThrow(ResourceNotFoundException::new);
  }

  /** A blank target means "all branches". Branch codes are stored in upper case. */
  private static String branch(String targetBranch) {
    return targetBranch == null || targetBranch.isBlank()
        ? null
        : targetBranch.strip().toUpperCase(Locale.ROOT);
  }
}
