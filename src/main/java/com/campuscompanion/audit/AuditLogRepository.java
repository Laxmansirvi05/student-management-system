package com.campuscompanion.audit;

import java.util.List;
import org.springframework.data.repository.Repository;

/**
 * Access to the audit log. It offers "save a new entry" and reading, and nothing else: there is no
 * method here that could change or delete an entry.
 */
public interface AuditLogRepository extends Repository<AuditLog, Long> {

  AuditLog save(AuditLog entry);

  List<AuditLog> findByEntityTypeAndEntityIdOrderByIdDesc(String entityType, String entityId);

  List<AuditLog> findByActionOrderByIdDesc(String action);

  long count();
}
