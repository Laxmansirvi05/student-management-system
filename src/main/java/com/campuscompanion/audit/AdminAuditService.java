package com.campuscompanion.audit;

import com.campuscompanion.audit.dto.AuditEntryResponse;
import com.campuscompanion.auth.AccountDirectory;
import com.campuscompanion.common.web.PageResponse;
import com.campuscompanion.student.StudentDirectory;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Admin: read the audit log (FR-ADM-009, the viewer). Nothing here can change an entry. */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditService {

  private final AuditLogRepository auditLogs;
  private final AccountDirectory accounts;
  private final StudentDirectory students;
  private final ObjectMapper json;

  public AdminAuditService(
      AuditLogRepository auditLogs,
      AccountDirectory accounts,
      StudentDirectory students,
      ObjectMapper json) {
    this.auditLogs = auditLogs;
    this.accounts = accounts;
    this.students = students;
    this.json = json;
  }

  /** One page of entries, newest first. */
  @Transactional(readOnly = true)
  public PageResponse<AuditEntryResponse> search(
      String action, String entityType, int page, int size) {
    var found =
        auditLogs.search(blankToNull(action), blankToNull(entityType), PageRequest.of(page, size));
    Names names = namesFor(found.getContent());
    return PageResponse.of(found.map(entry -> respond(entry, names)));
  }

  private AuditEntryResponse respond(AuditLog entry, Names names) {
    return new AuditEntryResponse(
        entry.getId(),
        entry.getCreatedAt(),
        entry.getAction(),
        entry.getActorUserId() == null
            ? "System"
            : names.accounts.getOrDefault(
                entry.getActorUserId(), "Account " + entry.getActorUserId()),
        entry.getEntityType(),
        entry.getEntityId(),
        names.subject(entry),
        parse(entry.getOldValue()),
        parse(entry.getNewValue()),
        entry.getIpAddress());
  }

  /** The emails and roll numbers of everything on the page, found with one query each. */
  private Names namesFor(Iterable<AuditLog> entries) {
    Set<Long> accountIds = new HashSet<>();
    Set<Long> studentIds = new HashSet<>();
    for (AuditLog entry : entries) {
      if (entry.getActorUserId() != null) {
        accountIds.add(entry.getActorUserId());
      }
      Long id = idOf(entry.getEntityId());
      if (id != null && Names.isAccount(entry)) {
        accountIds.add(id);
      } else if (id != null && Names.isStudent(entry)) {
        studentIds.add(id);
      }
    }
    return new Names(accounts.emailsOf(accountIds), students.rollNumbersOf(studentIds));
  }

  private record Names(Map<Long, String> accounts, Map<Long, String> rollNumbers) {

    static boolean isAccount(AuditLog entry) {
      return entry.getEntityType().equals("USER");
    }

    static boolean isStudent(AuditLog entry) {
      return entry.getEntityType().equals("STUDENT")
          || entry.getEntityType().equals("STUDENT_PROFILE");
    }

    /** A readable name for what the entry is about, or null if none is known. */
    String subject(AuditLog entry) {
      Long id = idOf(entry.getEntityId());
      if (id == null) {
        return null;
      }
      if (isAccount(entry)) {
        return accounts.get(id);
      }
      return isStudent(entry) ? rollNumbers.get(id) : null;
    }
  }

  private static Long idOf(String entityId) {
    try {
      return Long.valueOf(entityId);
    } catch (NumberFormatException notANumber) {
      return null;
    }
  }

  private JsonNode parse(String value) {
    return value == null ? null : json.readTree(value);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
