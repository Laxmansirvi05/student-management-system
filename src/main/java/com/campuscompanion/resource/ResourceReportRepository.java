package com.campuscompanion.resource;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceReportRepository extends JpaRepository<ResourceReport, Long> {

  boolean existsByResourceIdAndStudentIdAndStatus(
      Long resourceId, Long studentId, ResourceReportStatus status);

  Page<ResourceReport> findByStatusOrderByCreatedAtDesc(
      ResourceReportStatus status, Pageable pageable);

  Page<ResourceReport> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
