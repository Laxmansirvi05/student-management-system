package com.campuscompanion.pyq;

import com.campuscompanion.common.error.ConflictException;
import com.campuscompanion.common.error.FieldErrorDetail;
import com.campuscompanion.common.error.ResourceNotFoundException;
import com.campuscompanion.common.error.ValidationException;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.common.storage.StorageClient;
import com.campuscompanion.common.web.PageResponse;
import com.campuscompanion.pyq.dto.PyqLinkResponse;
import com.campuscompanion.pyq.dto.PyqMetadata;
import com.campuscompanion.pyq.dto.PyqPaperResponse;
import com.campuscompanion.pyq.dto.PyqUpdateRequest;
import com.campuscompanion.resource.PdfValidator;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin: add, correct, hide and remove previous-year papers. A file is checked like every upload
 * (it must be a real PDF of at most 10 MB), stored under a path made of the branch and a random ID,
 * and refused when the same file, or the same sitting, is already in the library.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminPyqService {

  private static final Logger log = LoggerFactory.getLogger(AdminPyqService.class);

  private final PyqPaperRepository papers;
  private final PyqBookmarkRepository bookmarks;
  private final PyqAssembler assembler;
  private final StorageClient storage;
  private final PdfValidator validator;
  private final Validator beanValidator;

  public AdminPyqService(
      PyqPaperRepository papers,
      PyqBookmarkRepository bookmarks,
      PyqAssembler assembler,
      StorageClient storage,
      PdfValidator validator,
      Validator beanValidator) {
    this.beanValidator = beanValidator;
    this.papers = papers;
    this.bookmarks = bookmarks;
    this.assembler = assembler;
    this.storage = storage;
    this.validator = validator;
  }

  /** Every paper, hidden ones included. Every filter is optional. */
  @Transactional(readOnly = true)
  public PageResponse<PyqPaperResponse> list(
      String branch, PyqStatus status, String query, int page, int size) {
    PyqSearchTerms terms = PyqSearchTerms.of(query);
    Page<PyqPaper> found =
        papers.search(
            status,
            PyqService.branchOrNull(branch),
            null,
            null,
            null,
            null,
            terms.first(),
            terms.second(),
            terms.third(),
            PageRequest.of(page, size, PyqService.sortOf("subject")));
    return new PageResponse<>(
        assembler.toResponses(found.getContent(), Set.of()),
        found.getNumber(),
        found.getSize(),
        found.getTotalElements(),
        found.getTotalPages());
  }

  @Transactional
  public PyqPaperResponse upload(
      PyqMetadata metadata,
      byte[] content,
      String declaredType,
      String fileName,
      CurrentUser admin) {
    PyqPaper.Details details = checked(metadata);
    String checksum = validator.validate(content, declaredType, fileName);
    papers
        .findByChecksumSha256(checksum)
        .ifPresent(
            existing -> {
              throw new ConflictException(
                  "This file is already in the library: " + describe(existing));
            });
    refuseIfSittingTaken(details, null);
    // The path comes from the branch and a random ID. Nothing of the file name is in it.
    String path = "pyq/" + details.branch() + "/" + UUID.randomUUID() + ".pdf";

    storage.upload(path, content, "application/pdf");
    try {
      PyqPaper paper =
          papers.saveAndFlush(new PyqPaper(details, path, content.length, checksum, admin.id()));
      return assembler.toResponse(paper, Set.of());
    } catch (RuntimeException databaseFailure) {
      removeStoredFile(path);
      throw databaseFailure;
    }
  }

  /** A five-minute link to open any paper, hidden ones too. It is not counted as a download. */
  @Transactional(readOnly = true)
  public PyqLinkResponse link(Long id) {
    PyqPaper paper = papers.findById(id).orElseThrow(ResourceNotFoundException::new);
    StorageClient.SignedLink link =
        storage.signedUrl(paper.getStoragePath(), PyqService.LINK_LIFETIME, null);
    return new PyqLinkResponse(link.url(), link.expiresAt());
  }

  @Transactional
  public PyqPaperResponse update(Long id, PyqUpdateRequest request) {
    PyqPaper paper = papers.findById(id).orElseThrow(ResourceNotFoundException::new);
    PyqPaper.Details details = checked(request.metadata());
    refuseIfSittingTaken(details, id);
    paper.describe(details, request.status());
    papers.flush();
    return assembler.toResponse(paper, Set.of());
  }

  /**
   * Removes the bookmarks, the row and then the file. If the file cannot be removed, the exception
   * undoes the removal of the rows, so the row and the file always stay together.
   */
  @Transactional
  public void delete(Long id) {
    PyqPaper paper = papers.findById(id).orElseThrow(ResourceNotFoundException::new);
    bookmarks.deleteAllOf(id);
    papers.delete(paper);
    papers.flush();
    storage.delete(paper.getStoragePath());
  }

  /** Tidies the details and checks the rules that the annotations cannot. */
  private PyqPaper.Details checked(PyqMetadata metadata) {
    // Details read from a folder path have not been through the request checks, so run them here.
    List<FieldErrorDetail> problems = new ArrayList<>();
    for (ConstraintViolation<PyqMetadata> violation : beanValidator.validate(metadata)) {
      problems.add(
          new FieldErrorDetail(violation.getPropertyPath().toString(), violation.getMessage()));
    }
    if (!problems.isEmpty()) {
      throw new ValidationException(problems);
    }
    if (metadata.kind() != PyqExamKind.MID && metadata.midNumber() != null) {
      throw new ValidationException("midNumber", "Only a mid paper has a number.");
    }
    String regulation = blankToNull(metadata.regulation());
    String courseCode = blankToNull(metadata.courseCode());
    return new PyqPaper.Details(
        metadata.branch().strip().toUpperCase(Locale.ROOT),
        metadata.subject().strip().replaceAll("\\s+", " "),
        metadata.year(),
        metadata.month(),
        metadata.kind(),
        metadata.midNumber(),
        regulation == null ? null : regulation.toUpperCase(Locale.ROOT),
        courseCode == null ? null : courseCode.toUpperCase(Locale.ROOT),
        metadata.variant() == null ? 1 : metadata.variant());
  }

  private void refuseIfSittingTaken(PyqPaper.Details details, Long ownId) {
    papers
        .findSameSitting(
            details.branch(),
            details.subjectName(),
            details.examYear(),
            details.examMonth(),
            details.examKind(),
            details.midNumber() == null ? 0 : details.midNumber(),
            details.regulation() == null ? "" : details.regulation(),
            details.variant())
        .filter(existing -> !existing.getId().equals(ownId))
        .ifPresent(
            existing -> {
              throw new ConflictException(
                  "The library already has this paper: "
                      + describe(existing)
                      + ". If it is a different question set, give it a set number.");
            });
  }

  private static String describe(PyqPaper paper) {
    return paper.getBranch()
        + " · "
        + paper.getSubjectName()
        + " · "
        + PyqAssembler.monthName(paper.getExamMonth())
        + " "
        + paper.getExamYear()
        + " "
        + paper.getExamKind().name().toLowerCase(Locale.ROOT);
  }

  private void removeStoredFile(String path) {
    try {
      storage.delete(path);
    } catch (RuntimeException cleanupFailed) {
      // The caller still sees the original failure; the stray file is reported here.
      log.warn("A stored paper could not be removed after a failed save: {}", path);
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
