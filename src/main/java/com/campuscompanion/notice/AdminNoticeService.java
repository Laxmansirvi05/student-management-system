package com.campuscompanion.notice;

import com.campuscompanion.common.error.ResourceNotFoundException;
import com.campuscompanion.common.error.ValidationException;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.common.storage.StorageClient;
import com.campuscompanion.common.web.PageResponse;
import com.campuscompanion.notice.dto.NoticeDocumentLinkResponse;
import com.campuscompanion.notice.dto.NoticeResponse;
import com.campuscompanion.notice.dto.NoticeWriteRequest;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  private static final Logger log = LoggerFactory.getLogger(AdminNoticeService.class);

  private final NoticeRepository notices;
  private final StorageClient storage;
  private final NoticeDocumentChecker checker;
  private final Clock clock;

  public AdminNoticeService(
      NoticeRepository notices, StorageClient storage, NoticeDocumentChecker checker, Clock clock) {
    this.notices = notices;
    this.storage = storage;
    this.checker = checker;
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
    notice.replaceExamPapers(papers(request, List.of()));
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
    notice.replaceExamPapers(papers(request, notice.getExamPapers()));
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

  /** Deletes the notice and then its document, so a failed file removal keeps the notice. */
  @Transactional
  public void delete(Long id) {
    Notice notice = find(id);
    String document = notice.getDocumentPath();
    notices.delete(notice);
    notices.flush();
    if (document != null) {
      storage.delete(document);
    }
  }

  /**
   * Adds the official document (the scanned circular) to a notice, or replaces it. The new file is
   * stored first; if the notice cannot be saved, it is removed again.
   */
  @Transactional
  public NoticeResponse attachDocument(
      Long id, byte[] content, String declaredType, String fileName) {
    Notice notice = find(id);
    NoticeDocumentChecker.Checked checked = checker.check(content, declaredType, fileName);
    String extension = "application/pdf".equals(checked.contentType()) ? ".pdf" : ".jpg";
    String path = "notices/" + UUID.randomUUID() + extension;
    storage.upload(path, checked.content(), checked.contentType());
    String previous;
    try {
      previous =
          notice.attachDocument(
              path, checked.contentType(), checked.content().length, clock.instant());
      notices.flush();
    } catch (RuntimeException databaseFailure) {
      removeQuietly(path);
      throw databaseFailure;
    }
    if (previous != null) {
      removeQuietly(previous);
    }
    return NoticeService.toResponse(notice);
  }

  /** Takes the document off a notice; the notice stays as text. */
  @Transactional
  public NoticeResponse removeDocument(Long id) {
    Notice notice = find(id);
    String previous = notice.removeDocument(clock.instant());
    if (previous == null) {
      throw new ResourceNotFoundException();
    }
    notices.flush();
    storage.delete(previous);
    return NoticeService.toResponse(notice);
  }

  /** A five-minute link for the admin, drafts included. */
  @Transactional(readOnly = true)
  public NoticeDocumentLinkResponse documentLink(Long id, boolean download) {
    return NoticeService.linkTo(find(id), download, storage);
  }

  /**
   * A file that is no longer referenced is removed; if storage refuses, the notice is still right,
   * so the problem is logged for the clean-up job (T7-09) instead of failing the request.
   */
  private void removeQuietly(String path) {
    try {
      storage.delete(path);
    } catch (RuntimeException storageFailure) {
      log.warn(
          "An unreferenced notice document could not be removed: {}",
          storageFailure.getClass().getSimpleName());
    }
  }

  private Notice find(Long id) {
    return notices.findById(id).orElseThrow(ResourceNotFoundException::new);
  }

  /** A blank target means "all branches". Branch codes are stored in upper case. */
  /**
   * The papers to save: only an exam notice has them. Null in the request keeps what is saved, so
   * an older client cannot wipe a time table by leaving the field out.
   */
  private static List<ExamPaper> papers(NoticeWriteRequest request, List<ExamPaper> saved) {
    if (request.category() != NoticeCategory.EXAM) {
      if (request.examPapers() != null && !request.examPapers().isEmpty()) {
        throw new ValidationException("examPapers", "Only an exam notice can list exam papers.");
      }
      return List.of();
    }
    if (request.examPapers() == null) {
      return saved;
    }
    return request.examPapers().stream()
        .map(paper -> new ExamPaper(paper.examDate(), paper.subject().strip()))
        .toList();
  }

  private static String branch(String targetBranch) {
    return targetBranch == null || targetBranch.isBlank()
        ? null
        : targetBranch.strip().toUpperCase(Locale.ROOT);
  }
}
