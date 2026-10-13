package com.campuscompanion.resource;

import com.campuscompanion.common.error.ResourceNotFoundException;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.common.web.PageResponse;
import com.campuscompanion.resource.dto.ResourceResponse;
import com.campuscompanion.student.StudentProfileService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A student's own bookmarks (FR-LIB-004). Adding or removing twice has no extra effect, and every
 * call works only on the bookmarks of the student who is logged in.
 */
@Service
public class BookmarkService {

  private final BookmarkRepository bookmarks;
  private final LearningResourceRepository resources;
  private final ResourceAssembler assembler;
  private final StudentProfileService students;

  public BookmarkService(
      BookmarkRepository bookmarks,
      LearningResourceRepository resources,
      ResourceAssembler assembler,
      StudentProfileService students) {
    this.bookmarks = bookmarks;
    this.resources = resources;
    this.assembler = assembler;
    this.students = students;
  }

  /** The student's bookmarked resources that are still published, newest first. */
  @Transactional(readOnly = true)
  public PageResponse<ResourceResponse> list(CurrentUser currentUser, int page, int size) {
    Long studentId = students.currentStudent(currentUser).studentId();
    Page<LearningResource> found =
        resources.findBookmarked(
            studentId, PageRequest.of(page, size, ResourceService.NEWEST_FIRST));
    return new PageResponse<>(
        assembler.toResponses(found.getContent(), studentId, false),
        found.getNumber(),
        found.getSize(),
        found.getTotalElements(),
        found.getTotalPages());
  }

  @Transactional
  public void add(CurrentUser currentUser, Long resourceId) {
    Long studentId = students.currentStudent(currentUser).studentId();
    resources
        .findById(resourceId)
        .filter(LearningResource::isPublished)
        .orElseThrow(ResourceNotFoundException::new);
    Bookmark.Key key = new Bookmark.Key(studentId, resourceId);
    if (!bookmarks.existsById(key)) {
      bookmarks.save(new Bookmark(studentId, resourceId));
    }
  }

  @Transactional
  public void remove(CurrentUser currentUser, Long resourceId) {
    Long studentId = students.currentStudent(currentUser).studentId();
    Bookmark.Key key = new Bookmark.Key(studentId, resourceId);
    if (bookmarks.existsById(key)) {
      bookmarks.deleteById(key);
    }
  }
}
