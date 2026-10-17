package com.campuscompanion.audit;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Access to the audit log. It offers "save a new entry" and reading, and nothing else: there is no
 * method here that could change or delete an entry.
 */
public interface AuditLogRepository extends Repository<AuditLog, Long> {

  AuditLog save(AuditLog entry);

  List<AuditLog> findByEntityTypeAndEntityIdOrderByIdDesc(String entityType, String entityId);

  List<AuditLog> findByActionOrderByIdDesc(String action);

  /** Newest first. Both filters are optional. */
  @Query(
      value =
          """
          select a from AuditLog a
          where (:action is null or a.action = :action)
            and (:entityType is null or a.entityType = :entityType)
          order by a.id desc
          """,
      countQuery =
          """
          select count(a) from AuditLog a
          where (:action is null or a.action = :action)
            and (:entityType is null or a.entityType = :entityType)
          """)
  Page<AuditLog> search(
      @Param("action") String action, @Param("entityType") String entityType, Pageable pageable);

  long count();
}
