package com.campuscompanion.resource;

import com.campuscompanion.common.error.ResourceNotFoundException;
import com.campuscompanion.common.error.ValidationException;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.common.storage.StorageClient;
import com.campuscompanion.common.storage.StorageUnavailableException;
import com.campuscompanion.common.web.PageResponse;
import com.campuscompanion.resource.dto.QuestionPaperDetails;
import com.campuscompanion.resource.dto.ResourceMetadata;
import com.campuscompanion.resource.dto.ResourceResponse;
import com.campuscompanion.resource.dto.ResourceUpdateRequest;
import com.campuscompanion.subject.SubjectService;
import com.campuscompanion.subject.dto.SubjectResponse;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Uploading and managing resources (FR-ADM-007). The file is checked first, then stored, then its
 * details are saved. If saving the details fails, the stored file is removed again, so a file never
 * stays in storage without a row, and a row never points at a missing file.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminResourceService {

  private static final Logger log = LoggerFactory.getLogger(AdminResourceService.class);
  private static final int MAX_FILE_NAME = 255;

  private final LearningResourceRepository resources;
  private final QuestionPaperRepository papers;
  private final ResourceAssembler assembler;
  private final StorageClient storage;
  private final PdfValidator validator;
  private final SubjectService subjects;

  public AdminResourceService(
      LearningResourceRepository resources,
      QuestionPaperRepository papers,
      ResourceAssembler assembler,
      StorageClient storage,
      PdfValidator validator,
      SubjectService subjects) {
    this.resources = resources;
    this.papers = papers;
    this.assembler = assembler;
    this.storage = storage;
    this.validator = validator;
    this.subjects = subjects;
  }

  /** Every resource, hidden ones included, newest first. */
  @Transactional(readOnly = true)
  public PageResponse<ResourceResponse> list(
      Long subjectId, ResourceStatus status, int page, int size) {
    Page<LearningResource> found =
        resources.search(
            status,
            subjectId,
            true,
            List.of(-1L),
            null,
            null,
            null,
            PageRequest.of(page, size, ResourceService.NEWEST_FIRST));
    return new PageResponse<>(
        assembler.toResponses(found.getContent(), null, true),
        found.getNumber(),
        found.getSize(),
        found.getTotalElements(),
        found.getTotalPages());
  }

  @Transactional
  public ResourceResponse upload(
      ResourceMetadata metadata,
      byte[] content,
      String declaredType,
      String fileName,
      CurrentUser admin) {
    SubjectResponse subject = checked(metadata);
    String checksum = validator.validate(content, declaredType, fileName);
    // The path comes from the subject code and a random ID. Nothing of the file name is in it.
    String path = "resources/" + subject.code() + "/" + UUID.randomUUID() + ".pdf";

    storage.upload(path, content, "application/pdf");
    try {
      LearningResource resource =
          resources.saveAndFlush(
              new LearningResource(
                  metadata.title().strip(),
                  blankToNull(metadata.description()),
                  metadata.subjectId(),
                  metadata.unitId(),
                  metadata.type(),
                  path,
                  displayName(fileName),
                  content.length,
                  checksum,
                  admin.id()));
      savePaperDetails(resource.getId(), metadata.questionPaper());
      return assembler.toResponse(resource, null, true);
    } catch (RuntimeException databaseFailure) {
      removeStoredFile(path);
      throw databaseFailure;
    }
  }

  @Transactional
  public ResourceResponse update(Long id, ResourceUpdateRequest request) {
    LearningResource resource = resources.findById(id).orElseThrow(ResourceNotFoundException::new);
    ResourceMetadata metadata = request.metadata();
    checked(metadata);
    resource.describe(
        metadata.title().strip(),
        blankToNull(metadata.description()),
        metadata.subjectId(),
        metadata.unitId(),
        metadata.type(),
        request.status());
    if (metadata.questionPaper() == null) {
      papers.findById(id).ifPresent(papers::delete);
    } else {
      savePaperDetails(id, metadata.questionPaper());
    }
    papers.flush();
    return assembler.toResponse(resource, null, true);
  }

  /**
   * Removes the row and then the file. If the file cannot be removed, the exception undoes the
   * removal of the row, so the two always stay together.
   */
  @Transactional
  public void delete(Long id) {
    LearningResource resource = resources.findById(id).orElseThrow(ResourceNotFoundException::new);
    resources.delete(resource);
    resources.flush();
    storage.delete(resource.getStoragePath());
  }

  /** Checks the details against the subject, the unit and the rule for exam details. */
  private SubjectResponse checked(ResourceMetadata metadata) {
    SubjectResponse subject =
        subjects
            .find(metadata.subjectId())
            .orElseThrow(() -> new ValidationException("subjectId", "Choose an existing subject."));
    if (metadata.unitId() != null) {
      boolean unitOfSubject =
          subjects
              .findUnit(metadata.unitId())
              .filter(unit -> unit.subjectId().equals(metadata.subjectId()))
              .isPresent();
      if (!unitOfSubject) {
        throw new ValidationException("unitId", "Choose a unit of this subject.");
      }
    }
    if (metadata.type().isPaper() && metadata.questionPaper() == null) {
      throw new ValidationException("questionPaper", "Enter the exam year and the exam type.");
    }
    if (!metadata.type().isPaper() && metadata.questionPaper() != null) {
      throw new ValidationException(
          "questionPaper", "Only question papers and model papers have exam details.");
    }
    return subject;
  }

  private void savePaperDetails(Long resourceId, QuestionPaperDetails details) {
    if (details == null) {
      return;
    }
    QuestionPaper paper = papers.findById(resourceId).orElse(null);
    if (paper == null) {
      papers.saveAndFlush(
          new QuestionPaper(
              resourceId, details.examYear(), details.examType(), details.maxMarks()));
    } else {
      paper.update(details.examYear(), details.examType(), details.maxMarks());
    }
  }

  private void removeStoredFile(String path) {
    try {
      storage.delete(path);
    } catch (StorageUnavailableException stillThere) {
      log.error("A stored file was left without a database row and needs cleaning up.");
    }
  }

  /**
   * The uploaded file name, made safe to show: no folders, no control characters, not too long. It
   * is never used in a path.
   */
  static String displayName(String fileName) {
    String name = fileName == null ? "" : fileName;
    name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
    name = name.replaceAll("\\p{Cntrl}", "").strip();
    if (name.isEmpty()) {
      return "document.pdf";
    }
    return name.length() > MAX_FILE_NAME ? name.substring(name.length() - MAX_FILE_NAME) : name;
  }

  private static String blankToNull(String text) {
    return text == null || text.isBlank() ? null : text.strip();
  }
}
