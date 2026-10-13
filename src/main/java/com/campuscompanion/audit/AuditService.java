package com.campuscompanion.audit;

import com.campuscompanion.common.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes the audit log (FR-ADM-009). An entry is always written inside the transaction of the
 * change it describes, so the change and its entry are saved together or not at all. That is why
 * both methods need an existing transaction and refuse to run without one.
 *
 * <p>Old and new values are accepted only as {@link AuditSnapshot} records, never as entities.
 */
@Service
public class AuditService {

  private final AuditLogRepository auditLogs;
  private final ObjectMapper json;

  public AuditService(AuditLogRepository auditLogs, ObjectMapper json) {
    this.auditLogs = auditLogs;
    this.json = json;
  }

  /** Records a change to data, with the old and the new values of the fields that matter. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void record(
      AuditAction action,
      String entityType,
      Long entityId,
      AuditSnapshot oldValue,
      AuditSnapshot newValue) {
    if (action.isPasswordEvent()) {
      throw new IllegalArgumentException("A password event never carries values: " + action);
    }
    save(action, entityType, entityId, toJson(oldValue), toJson(newValue));
  }

  /** Records that a password was changed or reset. Nothing about the password is stored. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void recordPasswordEvent(AuditAction action, Long userId) {
    if (!action.isPasswordEvent()) {
      throw new IllegalArgumentException("Not a password event: " + action);
    }
    save(action, "USER", userId, null, null);
  }

  private void save(
      AuditAction action, String entityType, Long entityId, String oldValue, String newValue) {
    auditLogs.save(
        new AuditLog(
            actorUserId(),
            action.name(),
            entityType,
            String.valueOf(entityId),
            oldValue,
            newValue,
            ipAddress()));
  }

  private String toJson(AuditSnapshot snapshot) {
    return snapshot == null ? null : json.writeValueAsString(snapshot);
  }

  /** The logged-in user, or null when the system itself acts (startup tasks). */
  private static Long actorUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof CurrentUser user) {
      return user.id();
    }
    return null;
  }

  private static String ipAddress() {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes web) {
      HttpServletRequest request = web.getRequest();
      return request.getRemoteAddr();
    }
    return null;
  }
}
