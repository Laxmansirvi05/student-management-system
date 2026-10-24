package com.campuscompanion.resource;

import com.campuscompanion.audit.AuditAction;
import com.campuscompanion.audit.AuditService;
import com.campuscompanion.audit.AuditSnapshot;
import com.campuscompanion.common.error.ConflictException;
import com.campuscompanion.common.error.ResourceNotFoundException;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.common.web.PageResponse;
import com.campuscompanion.resource.dto.ResourceReportRequest;
import com.campuscompanion.resource.dto.ResourceReportResponse;
import com.campuscompanion.student.StudentContext;
import com.campuscompanion.student.StudentProfileService;
import java.time.Clock;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reports on learning resources (FR-LIB-005): a student reports a problem with a resource they can
 * see; an administrator lists the reports and resolves them. Resolving is audited by status only.
 */
@Service
public class ResourceReportService {

  static final String ALREADY_REPORTED =
      "You have already reported this resource. An administrator will look at it.";

  private final ResourceReportRepository reports;
  private final LearningResourceRepository resources;
  private final StudentProfileService students;
  private final AuditService audit;
  private final Clock clock;

  public ResourceReportService(
      ResourceReportRepository reports,
      LearningResourceRepository resources,
      StudentProfileService students,
      AuditService audit,
      Clock clock) {
    this.reports = reports;
    this.resources = resources;
    this.students = students;
    this.audit = audit;
    this.clock = clock;
  }

  /** A student reports a published resource. A second open report of the same resource is 409. */
  @PreAuthorize("hasRole('STUDENT')")
  @Transactional
  public ResourceReportResponse report(
      CurrentUser user, Long resourceId, ResourceReportRequest request) {
    Long studentId = students.currentStudent(user).studentId();
    LearningResource resource =
        resources
            .findById(resourceId)
            .filter(LearningResource::isPublished)
            .orElseThrow(ResourceNotFoundException::new);
    if (reports.existsByResourceIdAndStudentIdAndStatus(
        resourceId, studentId, ResourceReportStatus.OPEN)) {
      throw new ConflictException(ALREADY_REPORTED);
    }
    ResourceReport saved;
    try {
      saved =
          reports.saveAndFlush(
              new ResourceReport(resourceId, studentId, request.reason().strip(), clock.instant()));
    } catch (DataIntegrityViolationException sameMomentDuplicate) {
      throw new ConflictException(ALREADY_REPORTED);
    }
    return respond(saved, resource.getTitle(), null);
  }

  /** Reports for the administrator, newest first: open ones, resolved ones or all. */
  @PreAuthorize("hasRole('ADMIN')")
  @Transactional(readOnly = true)
  public PageResponse<ResourceReportResponse> list(String status, int page, int size) {
    PageRequest request = PageRequest.of(page, size);
    Page<ResourceReport> found =
        "ALL".equals(status)
            ? reports.findAllByOrderByCreatedAtDesc(request)
            : reports.findByStatusOrderByCreatedAtDesc(
                ResourceReportStatus.valueOf(status), request);
    Map<Long, String> titles =
        resources.findAllById(found.map(ResourceReport::getResourceId).toSet()).stream()
            .collect(Collectors.toMap(LearningResource::getId, LearningResource::getTitle));
    Map<Long, StudentContext> people =
        students.studentsByIds(found.map(ResourceReport::getStudentId).toSet());
    Function<ResourceReport, ResourceReportResponse> toResponse =
        report -> {
          StudentContext who = people.get(report.getStudentId());
          return respond(
              report, titles.get(report.getResourceId()), who == null ? null : who.rollNo());
        };
    return PageResponse.of(found.map(toResponse));
  }

  /** Marks a report resolved. Doing it again changes nothing and records nothing. */
  @PreAuthorize("hasRole('ADMIN')")
  @Transactional
  public ResourceReportResponse resolve(Long id) {
    ResourceReport report = reports.findById(id).orElseThrow(ResourceNotFoundException::new);
    if (report.getStatus() == ResourceReportStatus.OPEN) {
      report.resolve(clock.instant());
      audit.record(
          AuditAction.RESOURCE_REPORT_RESOLVED,
          "resource_report",
          report.getId(),
          new AuditSnapshot.ResourceReportStatus(ResourceReportStatus.OPEN.name()),
          new AuditSnapshot.ResourceReportStatus(ResourceReportStatus.RESOLVED.name()));
    }
    String title =
        resources.findById(report.getResourceId()).map(LearningResource::getTitle).orElse(null);
    return respond(report, title, null);
  }

  private static ResourceReportResponse respond(
      ResourceReport report, String title, String reportedBy) {
    return new ResourceReportResponse(
        report.getId(),
        report.getResourceId(),
        title,
        report.getReason(),
        report.getStatus().name(),
        reportedBy,
        report.getCreatedAt(),
        report.getResolvedAt());
  }
}
