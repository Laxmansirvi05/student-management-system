package com.campuscompanion.resource;

import com.campuscompanion.resource.dto.QuestionPaperDetails;
import com.campuscompanion.resource.dto.ResourceResponse;
import com.campuscompanion.subject.SubjectService;
import com.campuscompanion.subject.SubjectService.UnitOfSubject;
import com.campuscompanion.subject.dto.SubjectResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Turns resource rows into responses. It looks up the subject, the unit, the exam details and the
 * student's bookmarks for a whole page at once, so a list costs a fixed number of queries.
 */
@Component
class ResourceAssembler {

  private final SubjectService subjects;
  private final QuestionPaperRepository papers;
  private final BookmarkRepository bookmarks;

  ResourceAssembler(
      SubjectService subjects, QuestionPaperRepository papers, BookmarkRepository bookmarks) {
    this.subjects = subjects;
    this.papers = papers;
    this.bookmarks = bookmarks;
  }

  /**
   * @param studentId whose bookmarks to mark, or null for an admin
   * @param withStatus whether to say if a resource is hidden; only admins are told
   */
  List<ResourceResponse> toResponses(
      List<LearningResource> found, Long studentId, boolean withStatus) {
    List<Long> ids = found.stream().map(LearningResource::getId).toList();
    Map<Long, SubjectResponse> subjectOf =
        subjects.byIds(found.stream().map(LearningResource::getSubjectId).distinct().toList());
    Map<Long, UnitOfSubject> unitOf =
        subjects.unitsByIds(
            found.stream()
                .map(LearningResource::getUnitId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
    Map<Long, QuestionPaper> paperOf =
        papers.findAllById(ids).stream()
            .collect(Collectors.toMap(QuestionPaper::getResourceId, Function.identity()));
    Set<Long> saved =
        studentId == null || ids.isEmpty() ? Set.of() : bookmarks.bookmarkedAmong(studentId, ids);

    return found.stream()
        .map(
            resource -> {
              SubjectResponse subject = subjectOf.get(resource.getSubjectId());
              UnitOfSubject unit =
                  resource.getUnitId() == null ? null : unitOf.get(resource.getUnitId());
              QuestionPaper paper = paperOf.get(resource.getId());
              return new ResourceResponse(
                  resource.getId(),
                  resource.getTitle(),
                  resource.getDescription(),
                  resource.getSubjectId(),
                  subject == null ? null : subject.code(),
                  subject == null ? null : subject.name(),
                  resource.getUnitId(),
                  unit == null ? null : unit.unitNo(),
                  unit == null ? null : unit.title(),
                  resource.getResourceType(),
                  resource.getFileSizeBytes(),
                  resource.getCreatedAt(),
                  saved.contains(resource.getId()),
                  paper == null
                      ? null
                      : new QuestionPaperDetails(
                          paper.getExamYear(), paper.getExamType(), paper.getMaxMarks()),
                  withStatus ? resource.getStatus() : null);
            })
        .toList();
  }

  ResourceResponse toResponse(LearningResource resource, Long studentId, boolean withStatus) {
    return toResponses(List.of(resource), studentId, withStatus).get(0);
  }
}
