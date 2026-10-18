package com.campuscompanion.pyq;

import com.campuscompanion.common.error.ResourceNotFoundException;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.common.storage.StorageClient;
import com.campuscompanion.common.web.PageResponse;
import com.campuscompanion.pyq.dto.PyqLinkResponse;
import com.campuscompanion.pyq.dto.PyqOverviewResponse;
import com.campuscompanion.pyq.dto.PyqPaperResponse;
import com.campuscompanion.pyq.dto.PyqSubjectResponse;
import com.campuscompanion.student.StudentProfileService;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The students' view of the previous-year papers: browse by branch and subject, search, open or
 * save a paper, and keep a list of favourites. A hidden paper answers "not found", exactly like one
 * that does not exist. A file is reached only through a signed link that lasts five minutes.
 */
@Service
public class PyqService {

  static final Duration LINK_LIFETIME = Duration.ofMinutes(5);

  private final PyqPaperRepository papers;
  private final PyqBookmarkRepository bookmarks;
  private final PyqAssembler assembler;
  private final StorageClient storage;
  private final StudentProfileService students;

  public PyqService(
      PyqPaperRepository papers,
      PyqBookmarkRepository bookmarks,
      PyqAssembler assembler,
      StorageClient storage,
      StudentProfileService students) {
    this.papers = papers;
    this.bookmarks = bookmarks;
    this.assembler = assembler;
    this.storage = storage;
    this.students = students;
  }

  @Transactional(readOnly = true)
  public PyqOverviewResponse overview() {
    List<PyqOverviewResponse.BranchCount> branches =
        papers.branchCounts().stream()
            .map(
                row ->
                    new PyqOverviewResponse.BranchCount(
                        (String) row[0], (Long) row[1], (Long) row[2]))
            .toList();
    long total = branches.stream().mapToLong(PyqOverviewResponse.BranchCount::papers).sum();
    return new PyqOverviewResponse(branches, papers.years(), papers.regulations(), total);
  }

  /** One card per subject, by name. Both filters are optional. */
  @Transactional(readOnly = true)
  public List<PyqSubjectResponse> subjects(String branch, String query) {
    PyqSearchTerms terms = PyqSearchTerms.of(query);
    return papers.subjects(branchOrNull(branch), terms.first(), terms.second(), terms.third());
  }

  /** Published papers. Every filter is optional and all given must match. */
  @Transactional(readOnly = true)
  public PageResponse<PyqPaperResponse> search(
      CurrentUser currentUser,
      String branch,
      String subject,
      Integer year,
      PyqExamKind kind,
      String regulation,
      String query,
      String sort,
      int page,
      int size) {
    PyqSearchTerms terms = PyqSearchTerms.of(query);
    Page<PyqPaper> found =
        papers.search(
            PyqStatus.PUBLISHED,
            branchOrNull(branch),
            blankToNull(subject),
            year,
            kind,
            blankToNull(regulation),
            terms.first(),
            terms.second(),
            terms.third(),
            PageRequest.of(page, size, sortOf(sort)));
    return page(found, currentUser);
  }

  /** The student's saved papers that are still published, newest first. */
  @Transactional(readOnly = true)
  public PageResponse<PyqPaperResponse> saved(CurrentUser currentUser, int page, int size) {
    Long studentId = students.currentStudent(currentUser).studentId();
    Page<PyqPaper> found = papers.findSaved(studentId, PageRequest.of(page, size, sortOf(null)));
    return page(found, currentUser);
  }

  /**
   * A link to the file, valid for five minutes, issued only for a published paper.
   *
   * @param download true for a link that saves the file under a readable name, false for one that
   *     shows it in the page. Only a download is counted.
   */
  @Transactional
  public PyqLinkResponse link(Long id, boolean download) {
    PyqPaper paper = published(id);
    if (download) {
      papers.countDownload(id);
    }
    StorageClient.SignedLink link =
        storage.signedUrl(
            paper.getStoragePath(),
            LINK_LIFETIME,
            download ? PyqAssembler.downloadName(paper) : null);
    return new PyqLinkResponse(link.url(), link.expiresAt());
  }

  @Transactional
  public void save(CurrentUser currentUser, Long id) {
    Long studentId = students.currentStudent(currentUser).studentId();
    published(id);
    bookmarks.addIfMissing(studentId, id);
  }

  @Transactional
  public void unsave(CurrentUser currentUser, Long id) {
    Long studentId = students.currentStudent(currentUser).studentId();
    PyqBookmark.Key key = new PyqBookmark.Key(studentId, id);
    if (bookmarks.existsById(key)) {
      bookmarks.deleteById(key);
    }
  }

  private PyqPaper published(Long id) {
    return papers
        .findById(id)
        .filter(PyqPaper::isPublished)
        .orElseThrow(ResourceNotFoundException::new);
  }

  private PageResponse<PyqPaperResponse> page(Page<PyqPaper> found, CurrentUser user) {
    Long studentId = students.currentStudent(user).studentId();
    List<Long> ids = found.getContent().stream().map(PyqPaper::getId).toList();
    Set<Long> saved =
        ids.isEmpty() ? Set.of() : new HashSet<>(bookmarks.savedAmong(studentId, ids));
    return new PageResponse<>(
        assembler.toResponses(found.getContent(), saved),
        found.getNumber(),
        found.getSize(),
        found.getTotalElements(),
        found.getTotalPages());
  }

  /** The sort orders of the contract. The ID is always the last key, so pages never overlap. */
  static Sort sortOf(String sort) {
    return switch (sort == null ? "" : sort) {
      case "oldest" ->
          Sort.by(Sort.Order.asc("examYear"), Sort.Order.asc("examMonth"), Sort.Order.asc("id"));
      case "popular" ->
          Sort.by(
              Sort.Order.desc("downloadCount"),
              Sort.Order.desc("examYear"),
              Sort.Order.desc("examMonth"),
              Sort.Order.desc("id"));
      case "subject" ->
          Sort.by(
              Sort.Order.asc("subjectName"),
              Sort.Order.desc("examYear"),
              Sort.Order.desc("examMonth"),
              Sort.Order.desc("id"));
      default ->
          Sort.by(Sort.Order.desc("examYear"), Sort.Order.desc("examMonth"), Sort.Order.desc("id"));
    };
  }

  static String branchOrNull(String branch) {
    return branch == null || branch.isBlank() ? null : branch.strip().toUpperCase(Locale.ROOT);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
