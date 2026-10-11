---
name: security-review
description: Review Campus Companion changes for authorization, session security, data exposure, Supabase permissions, AI safety, uploads, secrets, and audit integrity.
---

# Security Review

Read `SECURITY_RULES.md`, `AI_AGENT_SECURITY_RULES.md`, `docs/10-authorization-matrix.md`, `docs/11-security-plan.md`, and `docs/14-AI-security-and-privacy.md` before reviewing.

## Review checklist
- Deny by default; role must come from the server-side session, never request JSON or query parameters.
- For student-owned data, derive ownership from the authenticated server-side identity. Use owner-constrained queries for owned resources and attempts.
- Students must not write official marks, attendance, results, users, roles, published notices or question-bank answers.
- Validate authorization in Java services and Spring Security; hidden frontend controls are not security.
- Check session rotation, cookie flags, expiry, logout, CSRF on state-changing requests, and generic login failures.
- Check BCrypt password handling and that password/temporary-password/hash values are never logged or audited.
- Flag institutional email plus roll-number temporary password as unsafe for real students; restrict it to synthetic demo data until an approved alternative exists.
- Check private Storage buckets, signed URL expiry, file signature/size validation, generated paths, and no public URL exposure.
- Check JDBC/JPA bound parameters, mass assignment, DTO exposure, XSS-safe text rendering, and safe error responses.
- Check Supabase service keys and database credentials remain server-only. Verify the actual runtime DB role, grants, RLS behavior and Data API restrictions against approved docs.
- AI: provider calls only from Java; no model database/tools access; minimize prompt data; enforce quota/timeout; validate structured outputs; render untrusted output as text; never assert JSON validation defeats prompt injection.
- Audit records must not include secrets/password material and routine runtime access must not update/delete them.

## Output
Report reproducible findings with severity, file/line, impact, and fix. Identify assumptions that require actual Supabase/platform verification. Never run destructive tests or production writes during a review.
