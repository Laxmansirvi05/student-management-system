# 11. Security Plan

> Status: Draft v0.1 · Owner: Laxman Sirvi · Last updated: 2026-10-10

This plan explains how the application is secured through development and production. The short, mandatory rules that code must obey are in [SECURITY_RULES.md](../SECURITY_RULES.md). AI-specific threats are in [14-AI-security-and-privacy.md](14-AI-security-and-privacy.md).

## 1. Assets

| Asset | Why it matters |
| --- | --- |
| Student records (marks, attendance, results, contact details) | Personal and academically sensitive. Integrity matters as much as confidentiality. |
| Credentials (password hashes, sessions) | Give access to everything above. |
| Secrets (database password, Supabase service key, AI key) | Full control of data, files and spend. |
| Question bank answers | Leaking them makes tests meaningless. |
| Learning resources | College material; must not be publicly crawlable. |
| Audit log | Evidence of who changed what. |
| AI budget | Real money; can be drained by abuse. |

## 2. Threat model

| # | Threat | Example | Main controls |
| --- | --- | --- | --- |
| T1 | Reading another student's records | Changing an ID in a request | `/me` endpoints, ownership in queries (§5) |
| T2 | Student changes official data | Posting to a marks endpoint | No student write endpoints; role checks (§5) |
| T3 | Password guessing | Trying common passwords | BCrypt, lockout, rate limit (§3, §8) |
| T4 | Session theft or fixation | Stolen cookie; shared computer | Cookie flags, session rotation, timeout (§4) |
| T5 | Cross-site request forgery | Hidden form on another site | CSRF tokens, `SameSite` (§7) |
| T6 | Cross-site scripting | Script in a notice or AI answer | Text-only rendering, CSP (§7) |
| T7 | SQL injection | Crafted search text | Bound parameters only (§6) |
| T8 | Malicious upload | Executable renamed `.pdf` | Upload validation (§9) |
| T9 | Leaked secrets | Key committed to GitHub | Secrets management (§11) |
| T10 | Public exposure through Supabase | Data API or public bucket | RLS, private bucket (§10) |
| T11 | Abuse and cost | Scripted AI calls | Quotas and rate limits (§8) |
| T12 | Vulnerable dependency | Known CVE in a library | Dependency management (§12) |
| T13 | Information leakage | Stack trace in an error | Global handler, logging rules (§13) |
| T14 | Tampering with evidence | Editing the audit log | Append-only design (§13) |
| T15 | Careless or over-permissioned coding agent | Destructive migration, leaked key | [AI_AGENT_SECURITY_RULES.md](../AI_AGENT_SECURITY_RULES.md) |

Out of scope for a student project: denial-of-service at the network level, physical security, and compromise of Heroku or Supabase themselves.

## 3. Authentication and passwords

- Login is handled by Spring Security through a JSON endpoint. No custom cryptography is written.
- Passwords are hashed with BCrypt (cost 10 or higher) via `PasswordEncoder`. Plaintext passwords are never stored or logged.
- Password rules: 8 to 72 characters, not equal to the username, not equal to the current password. BCrypt ignores input beyond 72 bytes, so longer input is rejected rather than silently truncated.
- Temporary passwords are generated with `SecureRandom` (12 characters or more), shown once to the admin, and force a change at first login.
- The roll number is never used as a default password.
- Login failures return one generic message for unknown user, wrong password and inactive account.
- Five consecutive failures lock the account for 15 minutes. Lock state is stored in the database.

### Account recovery

There is no self-service or email reset in the first release. A student who forgets a password asks an administrator, who uses "Reset password". The reset is audited. This avoids running an email system and its risks.

## 4. Session management

| Setting | Value |
| --- | --- |
| Storage | Server memory (database-backed sessions are P1) |
| Cookie | `JSESSIONID`; `HttpOnly`, `Secure`, `SameSite=Lax`, path `/` |
| Idle timeout | 30 minutes |
| Fixation | Session ID is replaced at login |
| Logout | Session invalidated on the server; cookie cleared |
| Password change | All other sessions of the user are invalidated |
| Concurrent sessions | Allowed (phone and lab computer) |
| Caching | `Cache-Control: no-store` on all API responses and protected pages |

The session holds only the user ID and role.

## 5. Access control

Defined in [10-authorization-matrix.md](10-authorization-matrix.md). Principles:

1. **Deny by default.** Any request not matched by an explicit rule requires authentication.
2. **Roles from the server.** The role comes from the session, never from the request.
3. **Ownership in the query.** Student data is fetched by the session's student ID. Owned objects are fetched by "ID and owner".
4. **Same answer for missing and forbidden** (`404`), so IDs cannot be probed.
5. **Two layers for admin functions:** URL rules and method-level checks.
6. **Least data.** DTOs contain only the fields a screen needs. Entities are never serialized directly.

## 6. Input validation and injection

- Every request DTO is validated with Bean Validation: required fields, lengths, ranges, patterns and enums. Limits are in the API contract.
- Unknown JSON fields are rejected, which blocks mass-assignment of fields such as `role` or `studentId`.
- Database access uses Spring Data, JPQL or native queries with bound parameters. No SQL is built by concatenation, including `ORDER BY`: sort fields are checked against an allow-list.
- Search text is escaped for `LIKE` wildcards.
- File names from users are never used in paths.

## 7. Browser-side protections

### CSRF

Enabled for all state-changing requests, using a cookie-to-header token (`XSRF-TOKEN` cookie, `X-XSRF-TOKEN` header). CSRF protection is never disabled, including on login.

### XSS

- All dynamic text is inserted with `textContent`. `innerHTML` never receives server or AI data.
- Notices are plain text. No HTML or Markdown is accepted from admins or from the AI in the first release.
- No inline scripts or event-handler attributes.

### Security headers

| Header | Value |
| --- | --- |
| `Content-Security-Policy` | `default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; frame-src https://<project>.supabase.co; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'` |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` (production) |
| `X-Content-Type-Options` | `nosniff` |
| `Referrer-Policy` | `no-referrer` |
| `Permissions-Policy` | camera, microphone and geolocation disabled |

`frame-src` allows only the project's storage host, for the PDF preview. No CORS configuration is needed because the frontend and API share one origin; cross-origin requests are not allowed.

### Transport

HTTPS only. Heroku terminates TLS; the application trusts forwarded headers only from the platform and redirects HTTP to HTTPS in production.

## 8. Rate limiting and abuse

Implemented as a small in-memory limiter in Java (`RateLimitFilter`), which is enough for one dyno.

| Scope | Limit |
| --- | --- |
| Login, per IP address | 20 attempts per 10 minutes |
| Login, per account | 5 consecutive failures, then 15-minute lock |
| AI, per user | 5 per minute and 30 per day (the daily count is stored in the database) |
| AI, whole application | A daily ceiling; when reached, AI returns `503 AI_UNAVAILABLE` until the next day |
| Other API calls, per session | 120 per minute |
| Request body | 64 KB for JSON; 10 MB for uploads |

`AI_ENABLED=false` switches the assistant off immediately without a deployment.

## 9. Uploads

Only administrators upload. Even so, each file is treated as hostile:

1. Size limit of 10 MB, enforced by the server before the file is read into memory in full.
2. The declared content type and the extension must be PDF.
3. The first bytes must be the PDF signature (`%PDF-`).
4. The file is opened with PDFBox to confirm it parses; encrypted or unparseable files are rejected. Parsing has a page and time limit.
5. A SHA-256 checksum is stored.
6. The storage path is generated by the server from the subject code and a random UUID.
7. Files are stored in Supabase Storage, never on the dyno's disk and never under the application's own origin.
8. Files are served with `Content-Type: application/pdf` from the storage host, so a malicious file cannot run script in the application's origin.

There is no virus scanning in the first release. This is an accepted risk because uploaders are trusted staff and only PDFs are accepted.

## 10. Supabase controls

### Database

- The backend connects with one database user over TLS through the session pooler.
- **Row Level Security is enabled on every table with no policies.** The application's database user owns the tables and is unaffected; the public Data API can read nothing. Verify by calling the Data API with the anon key and confirming it returns no rows.
- The Supabase anon key and service key are never sent to the browser. The frontend has no Supabase client.
- Network restrictions on the database are enabled if the plan supports them.

### Storage

- One **private** bucket. Public access is off.
- No storage policies grant access to `anon` or `authenticated` roles. Only the service key, held by the backend, can read or write.
- Signed URLs last at most five minutes and are issued only after the resource is confirmed published.
- Signed URLs are never logged or stored.

## 11. Secrets management

| Secret | Where it lives |
| --- | --- |
| Database password, Supabase service key, AI key, bootstrap admin password | Heroku config vars in production; an untracked `.env` file locally |

- `.env` is in `.gitignore`. `.env.example` contains names only.
- Secrets never appear in source code, tests, documents, commit messages, issue text or logs.
- Enable GitHub secret scanning and push protection on the repository.
- If a secret is exposed anywhere, it is rotated immediately (see the [runbook](19-operations-runbook.md)). Deleting the commit is not enough.
- Each environment has its own secrets. Production secrets are never used locally.

## 12. Dependency management

- All dependencies come through Maven with versions managed by the Spring Boot parent.
- Enable Dependabot alerts and security updates on GitHub.
- Before each release, review open alerts; any high or critical finding in a runtime dependency blocks the release until updated or explicitly accepted by the owner.
- New dependencies need approval (see [04-TRD.md](04-TRD.md) §14).
- Frontend libraries are vendored at a pinned version; none are loaded from a CDN.

## 13. Logging and audit

### Security logging

Logged with user ID, IP address and request ID: login success and failure, lockouts, password changes and resets, access-denied events, rate-limit hits, upload rejections, AI declines and failures.

Never logged: passwords, session IDs, CSRF tokens, API keys, signed URLs, or AI prompt and response text.

### Audit trail

Changes to marks, attendance, semester results, account status and passwords write an `audit_logs` row in the same transaction as the change, with actor, action, entity, old and new values, IP address and time. The application has no update or delete path for this table. Password values are never recorded.

### Errors

One global handler returns problem details with a `traceId`. Stack traces, SQL, class names and file paths stay in the server log.

## 14. Backup, incident response and recovery

- Backups: see [08-database-design.md](08-database-design.md) §10.
- Incident steps (what to do when a key leaks, an account is compromised, or data is wrong) are in [19-operations-runbook.md](19-operations-runbook.md).
- General order in an incident: **contain** (disable AI, rotate keys, deactivate accounts), **assess** (audit log, application logs), **recover** (restore or correct data), **record** (write a short note of what happened and what changed).

## 15. Security testing

Defined in detail in [17-testing-strategy.md](17-testing-strategy.md). Mandatory automated scenarios:

1. Every protected endpoint rejects a visitor (401).
2. Every admin endpoint rejects a student (403).
3. A student requesting another student's attempt, or any record by a foreign ID, gets 404.
4. A student cannot change marks or attendance through any endpoint.
5. State-changing requests without a CSRF token are rejected.
6. Login locks after five failures.
7. Uploads that are oversized, mis-typed or not real PDFs are rejected.
8. Quiz start responses contain no correct answers.
9. Error responses contain no stack traces.
10. Adversarial prompts do not reveal secrets, system instructions or other users' data.

## 16. Release criteria

A release is blocked unless all of these are true:

- [ ] All mandatory security tests pass.
- [ ] No secret is present in the repository history (secret scanning clean).
- [ ] No open high or critical dependency alert without a recorded decision.
- [ ] RLS is enabled on every table and the Data API check returns nothing.
- [ ] The storage bucket is private and an unsigned URL is refused.
- [ ] Production cookies are `Secure` and `HttpOnly`; HTTP redirects to HTTPS.
- [ ] Security headers are present on a production response.
- [ ] The bootstrap admin password has been changed.
- [ ] Only synthetic data is loaded.
- [ ] Actuator exposes only `health`.
