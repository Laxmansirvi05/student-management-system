package com.campuscompanion.resource;

import com.campuscompanion.auth.Role;
import com.campuscompanion.common.error.ResourceNotFoundException;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.common.storage.StorageClient;
import com.campuscompanion.common.web.PageResponse;
import com.campuscompanion.resource.dto.DownloadUrlResponse;
import com.campuscompanion.resource.dto.ResourceResponse;
import com.campuscompanion.student.StudentProfileService;
import com.campuscompanion.subject.SubjectService;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finding and opening resources (FR-LIB-001 to FR-LIB-003). Students see published resources of
 * every subject. A hidden resource answers "not found" to a student, exactly like one that does not
 * exist. A file is reached only through a signed link that lasts five minutes.
 */
@Service
@Transactional(readOnly = true)
public class ResourceService {

  static final Duration LINK_LIFETIME = Duration.ofMinutes(5);
  static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

  private final LearningResourceRepository resources;
  private final ResourceAssembler assembler;
  private final StorageClient storage;
  private final SubjectService subjects;
  private final StudentProfileService students;

  public ResourceService(
      LearningResourceRepository resources,
      ResourceAssembler assembler,
      StorageClient storage,
      SubjectService subjects,
      StudentProfileService students) {
    this.resources = resources;
    this.assembler = assembler;
    this.storage = storage;
    this.subjects = subjects;
    this.students = students;
  }

  /** Published resources, newest first. Every filter is optional and all given must match. */
  public PageResponse<ResourceResponse> search(
      CurrentUser currentUser,
      Long subjectId,
      Long unitId,
      Integer semester,
      ResourceType type,
      String query,
      String sort,
      int page,
      int size) {
    List<Long> subjectIds = semester == null ? List.of() : subjects.activeIdsOfSemester(semester);
    Page<LearningResource> found =
        resources.search(
            ResourceStatus.PUBLISHED,
            subjectId,
            semester == null,
            // "in ()" is not valid SQL, so an empty semester is searched as an impossible ID.
            subjectIds.isEmpty() ? List.of(-1L) : subjectIds,
            unitId,
            type,
            searchText(query),
            PageRequest.of(page, size, sortOf(sort)));
    return page(found, currentUser);
  }

  /** Published question papers and model papers; subject, exam year and exam type are optional. */
  public PageResponse<ResourceResponse> papers(
      CurrentUser currentUser,
      Long subjectId,
      Integer examYear,
      ExamType examType,
      String query,
      int page,
      int size) {
    return page(
        resources.searchPapers(
            subjectId,
            examYear,
            examType,
            searchText(query),
            PageRequest.of(page, size, NEWEST_FIRST)),
        currentUser);
  }

  /** A few published resources of a unit to revise from: notes first, then the newest. */
  public List<ResourceResponse> forRevision(Long unitId, int limit) {
    return assembler.toResponses(
        resources.findForRevision(unitId, PageRequest.of(0, limit)), null, false);
  }

  public ResourceResponse get(CurrentUser currentUser, Long id) {
    boolean admin = currentUser.role() == Role.ADMIN;
    return assembler.toResponse(readable(currentUser, id), studentIdOf(currentUser), admin);
  }

  /**
   * A link to the file, valid for five minutes, issued only after the visibility check.
   *
   * @param download true for a link that saves the file under its original name, false for a link
   *     that shows it in the page
   */
  public DownloadUrlResponse downloadUrl(CurrentUser currentUser, Long id, boolean download) {
    LearningResource resource = readable(currentUser, id);
    StorageClient.SignedLink link =
        storage.signedUrl(
            resource.getStoragePath(),
            LINK_LIFETIME,
            download ? resource.getOriginalFileName() : null);
    return new DownloadUrlResponse(link.url(), link.expiresAt());
  }

  /** The resource, if this user may read it: published for a student, anything for an admin. */
  private LearningResource readable(CurrentUser currentUser, Long id) {
    return resources
        .findById(id)
        .filter(resource -> resource.isPublished() || currentUser.role() == Role.ADMIN)
        .orElseThrow(ResourceNotFoundException::new);
  }

  private PageResponse<ResourceResponse> page(Page<LearningResource> found, CurrentUser user) {
    List<ResourceResponse> content =
        assembler.toResponses(found.getContent(), studentIdOf(user), false);
    return new PageResponse<>(
        content,
        found.getNumber(),
        found.getSize(),
        found.getTotalElements(),
        found.getTotalPages());
  }

  private Long studentIdOf(CurrentUser currentUser) {
    return currentUser.role() == Role.STUDENT
        ? students.currentStudent(currentUser).studentId()
        : null;
  }

  /**
   * The four sort orders of the contract. The ID is always the last key, so two resources with the
   * same value keep the same order from one page to the next.
   */
  static Sort sortOf(String sort) {
    return switch (sort == null ? "" : sort) {
      case "createdAt,asc" -> Sort.by(Sort.Direction.ASC, "createdAt", "id");
      case "title,asc" -> Sort.by(Sort.Direction.ASC, "title", "id");
      case "title,desc" -> Sort.by(Sort.Direction.DESC, "title", "id");
      default -> NEWEST_FIRST;
    };
  }

  /**
   * Lower case, with the wildcard characters made ordinary, and wrapped for "contains". A blank
   * query means no text filter.
   */
  static String searchText(String query) {
    if (query == null || query.isBlank()) {
      return null;
    }
    String plain =
        query
            .strip()
            .toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    return "%" + plain + "%";
  }
}
