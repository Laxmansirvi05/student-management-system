# 21. Security Review

> Status: Review of 2026-10-14 · Owner: Laxman Sirvi · Reviewed by: Claude (coding agent), for the owner's line-by-line review

This is the security review of task `T6-03`. It checks the application at commit `1e9d403` and the fixes made during the review against [SECURITY_RULES.md](../SECURITY_RULES.md), the release criteria of [11-security-plan.md](11-security-plan.md) §16 and the AI controls of [14-AI-security-and-privacy.md](14-AI-security-and-privacy.md). It does not replace the owner's own review (SR-48).

## 1. How the review was done

| Method | What it covered |
| --- | --- |
| Rule by rule | Every rule SR-01 to SR-59 compared with the code and with the test that proves it (section 4). |
| Automated tests | `./mvnw verify` with every unit and integration test, against a real PostgreSQL 17 with the two database roles. Integration tests reach the server over real HTTP. |
| Generated access tests | `EndpointAccessIT` reads every endpoint from the running application and checks a visitor (401), a student on admin endpoints (403) and an admin on student endpoints (403). |
| Code scans | Searched the code for `innerHTML`, `insertAdjacentHTML`, `document.write`, `eval` and `new Function` in page scripts; for queries built from strings; for log lines that mention passwords, tokens, keys, URLs, emails, sessions, phones or prompts; for entities returned by controllers; for unpaged list endpoints. |
| History scan | The whole Git history searched for the `.env` file and for key, token, private-key and connection-string patterns. |
| Production profile | The built JAR started with `SPRING_PROFILES_ACTIVE=prod` against a local test database; `ProductionProfileIT` repeats the checks automatically. |
| Browser | Every page used by hand on a local test database, including the error states. |

**Not covered:** the hosted Supabase settings (Data API, exposed schemas, bucket privacy), Heroku, the GitHub security settings, a scan of dependencies against vulnerability databases, the real AI provider, and penetration testing by a person. Section 3 lists what the owner must do for each.

## 2. Summary

- **No open high-severity defect in the code.** Three defects found during the review were fixed in the same change (F1 to F3).
- **Two high-severity items are open, and both are for the owner:** credentials exposed during development must be rotated (F4), and the hosted database was migrated without the owner's approval for that action (F5).
- The release cannot go ahead until the owner items in section 3 are done. The code side of the release criteria is met.

## 3. Findings

Severity: **High** can expose data or accounts; **Medium** weakens a control or can take the service down; **Low** is limited in effect; **Info** is a recorded decision.

| ID | Severity | Finding | Status |
| --- | --- | --- | --- |
| F1 | Medium | **Photo decoding could use a lot of memory.** A 2 MB PNG can describe a 6000 by 6000 picture, about 144 MB once decoded. A few at once could exhaust a small dyno. | **Fixed.** A large picture is now read at reduced resolution (every second, third or fourth pixel), since only 512 pixels are kept. Test: `ProfilePhotoProcessorTest.aLargePictureIsReadAtLowerResolutionAndStillComesOutRight`. |
| F2 | Low | **Two bookmark requests at the same moment could fail.** "Check, then insert" let both requests insert, and one failed with a server error. | **Fixed.** One `insert ... on conflict do nothing` statement. Test: `ResourceApiIT.manyBookmarkRequestsAtOnceAllSucceedAndLeaveOneBookmark` sends eight at once. |
| F3 | Medium | **PDF checking had no time limit**, although [11-security-plan.md](11-security-plan.md) §9 requires one. A file built to keep the parser busy could hold a request. | **Fixed.** Opening a PDF runs on one of two dedicated threads with a 10-second limit. Test: `PdfValidatorTest.aFileThatCannotBeOpenedInTimeIsRefused`. |
| F4 | High | **Credentials exposed during development** (the development database passwords, seen during the earlier sessions) are still in use. | **Open, owner.** SR-06: rotate them in Supabase and in `.env` before any deployment. Removing them from a file is not enough. |
| F5 | High (process) | **The hosted database was migrated without approval for that action.** On 2026-10-14 a preview was started with the `campus-companion` launch configuration, which reads `.env` and connects to Supabase. Flyway applied V3 to V6 there. It was not started by the agent, and the agent stopped it 30 seconds later. The migrations only add tables and columns; nothing was lost. | **Open, owner.** Treat Supabase as being at V6. To prevent a repeat, rename or remove the Supabase entry in `.claude/launch.json`, or keep the local one first. The agent's notes now say to use only `campus-companion-local-db`. |
| F6 | Medium | **GitHub security features are off** (secret scanning, push protection, Dependabot alerts, private vulnerability reporting). | **Open, owner** (T0-04). The agent may not change repository settings (AG-25). |
| F7 | Medium | **No dependency vulnerability scan has been run.** | **Open, owner.** Turning on Dependabot alerts (F6) covers it. Spring Boot 4.1.1, PDFBox 3.0.5 and the PostgreSQL driver are recent releases, but that is not evidence of no known issue. |
| F8 | Medium | **Rate limits and sessions live in memory.** A restart resets the per-minute counters and logs everyone out. Two dynos would each count separately. | **Accepted** for one dyno, as designed. Database-backed sessions are P1 (T7-08). The daily AI limits are counted in the database and survive a restart. |
| F9 | Low | **The AI daily limit can be passed by one request.** The count and the request are not one transaction, so two requests sent at the same moment, at 29 of 30, can both go through. The two-at-a-time limit caps the overshoot at one. | **Accepted.** Recorded here. |
| F10 | Low | **Some list endpoints are not paged** (subjects, units, assessments of a subject, the timetable, a student's own marks and results). SR-23 asks for paging. | **Accepted.** Each list is small by nature: a week of classes, the units of one subject. The API contract defines them as plain lists. |
| F11 | Low | **AI usage rows are never deleted.** The 90-day clean-up job is P1 (T7-09). The rows hold numbers only, no text. | **Open, P1.** The privacy policy says so. |
| F12 | Info | **The roll-number first password (EX-AUTH-01)** is allowed for synthetic data only. | **Decided 2026-10-17.** The owner approved it for real students with four operating rules ([ADR 0010](decisions/0010-roll-number-first-password-with-real-students.md)). |
| F13 | Info | **Trusting Heroku's forwarded protocol header** depends on the router connecting from a private address. If it does not, every page redirects to itself. | **Verify at first deploy.** See [18-production-plan.md](18-production-plan.md) §4. Never set the trusted range to "any address". |
| F14 | Info | **Student photo upload and one more image host in the CSP** were added at the owner's request ([ADR 0009](decisions/0009-student-personal-details-and-photo.md)). | **For the owner's review.** |
| F15 | Info | **No virus scanning of uploads.** | **Accepted** in [11-security-plan.md](11-security-plan.md) §9. PDFs come from admins only, and photos are redrawn, never stored as uploaded. |

## 4. Rule by rule

✓ met and tested · ◐ met, with the note shown · ⧗ depends on the owner

| Rule | Status | Evidence |
| --- | --- | --- |
| SR-01 BCrypt only | ✓ | `SecurityConfig.passwordEncoder`; `PasswordChangeIT` |
| SR-02 No password in storage, logs, responses or audit | ✓ | `AuditSnapshotTest` (Z17); `SubjectsAndStudentsAdminIT` (Z16); log scan found only an account ID |
| SR-03 No predictable password, except EX-AUTH-01 | ◐ | Admin reset is random (`SubjectsAndStudentsAdminIT`, AU14, AU15). EX-AUTH-01 is for synthetic data only (F12) |
| SR-04 No secret in frontend, Git, docs, fixtures or logs | ✓ | History scan clean; the storage and AI keys are read only on the server; log scan clean |
| SR-05 Secrets from environment; `.env` never committed | ✓ | `.env` is ignored and has never been committed |
| SR-06 Rotate exposed secrets | ⧗ | F4 |
| SR-07 Spring Security for authentication | ✓ | `SecurityConfig`; `AuthFlowIT` |
| SR-08 Cookies HttpOnly, Secure in production, SameSite=Lax | ✓ | `ProductionProfileIT.theSessionAndTheSecurityTokenTravelOnlyOverHttps` |
| SR-09 CSRF on every state change | ✓ | `AuthFlowIT`; every write in the tests sends the token |
| SR-10 Same answer for every login failure | ✓ | `AuthFlowIT` (AU1, AU2, AU4); `LockoutIT` (AU3) |
| SR-11 Login and role checked on the server | ✓ | `EndpointAccessIT` (Z1, Z2, Z11) over every endpoint |
| SR-12 No bypassed authorization | ✓ | Same; `@PreAuthorize` on every admin service and controller |
| SR-13 The student comes from the session | ✓ | Every student endpoint is under `/me` or takes no student ID; `StudentProfileIT` (Z5); `OwnProfileIT` |
| SR-14 Owned objects loaded by ID and owner together | ✓ | `QuizAttemptRepository.findOwnForUpdate`; bookmark queries; `QuizApiIT` (Z3, Z4) |
| SR-15 Not yours is "not found" | ✓ | `QuizApiIT`, `ResourceApiIT` (Z9), `MarksAndResultsApiIT` (Z8) |
| SR-16 Students cannot write official records | ✓ | `AttendanceApiIT`, `MarksAndResultsApiIT` (Z6); `EndpointAccessIT` (Z2); `OwnProfileIT` refuses the college's fields |
| SR-17 Scores calculated by the server | ✓ | `QuizApiIT.theBrowserCannotSendItsOwnScoreOrAnswerAnotherQuestion` (Z7); `QuizEvaluationServiceTest` |
| SR-18 No answers before submission | ✓ | `QuizApiIT` checks the raw response text (Z13), and a running test's result answers 409 |
| SR-19 Server-side validation | ✓ | Bean Validation on every request record; boundary tests in each `*IT` |
| SR-20 Bound SQL parameters | ✓ | Scan: no query built from strings; sort orders are a fixed list (`ResourceService.sortOf`) |
| SR-21 DTOs, never entities | ✓ | Scan: every controller returns a record from a `dto` package |
| SR-22 Unknown fields refused | ✓ | `spring.jackson.deserialization.fail-on-unknown-properties: true`; `OwnProfileIT`, `QuizApiIT` |
| SR-23 Paged lists | ◐ | F10 |
| SR-24 Text, never `innerHTML` | ✓ | Scan of every page script: none; `StaticFrontendRulesTest` |
| SR-25 No internals in errors | ✓ | `GlobalExceptionHandler`; `ProductionProfileIT.errorsShowNoInternalDetails` |
| SR-26 No inline scripts or handlers | ✓ | `StaticFrontendRulesTest`; CSP `script-src 'self'` |
| SR-27 Uploads checked for size, type, signature and parse | ✓ | `PdfValidatorTest`, `ProfilePhotoProcessorTest`, `ResourceApiIT` (UP1 to UP11), `OwnProfileIT` |
| SR-28 Server-generated storage paths | ✓ | `ResourceApiIT.anOddFileNameDoesNotReachTheStoragePath`; `AdminResourceServiceTest`; `OwnProfileIT` |
| SR-29 Private bucket | ⧗ | Bucket not created yet (T3-01) |
| SR-30 Signed links of at most five minutes, after a check | ✓ | `ResourceApiIT.aSignedLinkLastsAtMostFiveMinutes`; `OwnProfileIT` |
| SR-31 Nothing stored on or served from the app's own origin | ✓ | Files go only through `StorageClient`; no file is written to disk by the application |
| SR-32 RLS on every table | ✓ | `DatabaseSecurityModelIT` |
| SR-33 The browser never talks to Supabase, except signed links | ✓ | `connect-src` is `'self'` through `default-src`; only `img-src` and `frame-src` name the storage host |
| SR-34 Schema only through Flyway; applied migrations never edited | ✓ | V1 to V6 unchanged once written; Hibernate `ddl-auto: validate` |
| SR-35 Audit in the same transaction | ✓ | `AuditRollbackIT` (Z19) |
| SR-36 Audit never changed or deleted | ✓ | `AcademicSchemaIT` (Z15); grants and triggers |
| SR-37 AI calls only from the backend | ✓ | `AiProvider` is server-side; no key reaches the browser |
| SR-38 No official records sent to AI | ✓ | `AiArchitectureTest`; `AiStudyServiceTest.onlyTheTopicTheNamesAndTheInstructionsAreSentOut` |
| SR-39 AI output parsed, validated, plain text | ✓ | `AiResponseParserTest`; `AiApiIT.awkwardContentArrivesAsPlainTextUnderTheFixedNotice` |
| SR-40 AI never scores or records | ✓ | `AiApiIT.practiceQuestionsAreCheckedAndNeverSaved`; quiz marking has no AI dependency |
| SR-41 AI input limits and quota | ✓ | `AiUsageServiceTest`; `AiApiIT` limits section; `AiSwitchedOffIT` |
| SR-42 Synthetic data only | ✓ | Demo seeders use invented names and `university.example` |
| SR-43 No secrets, sessions, signed URLs or AI text in logs | ✓ | Log scan; the storage client logs only the exception type, never the URL |
| SR-44 Tests never weakened | ✓ | One pinned field list was extended for two new fields, with a reason (`StudentProfileIT`); no test was disabled |
| SR-45 No dependency without approval | ✓ | PDFBox was planned and is recorded in the TRD; image handling uses only the JDK |
| SR-46 No destructive production change without approval | ⧗ | F5 was additive, not destructive, but was not approved |
| SR-47 Vulnerabilities reported privately | ✓ | This report is in the private repository and addressed to the owner |
| SR-48 Owner reviews security-relevant changes | ⧗ | Owner to review: `OwnProfileService`, `ProfilePhotoProcessor`, `SecurityConfig`, `ApiRateLimitFilter`, `RequestBodyLimitFilter`, `PdfValidator`, the `ai` package |
| SR-49 Email login, normalised | ✓ | `AuthFlowIT` |
| SR-50 Domain from configuration | ✓ | `STUDENT_EMAIL_DOMAIN`; tests use `university.example` |
| SR-51 Uniqueness in the database | ✓ | `DatabaseSecurityModelIT` (PV4) |
| SR-52 Forced password change blocks everything else | ✓ | `PasswordChangeIT` (AU5) |
| SR-53 Password policy | ✓ | `PasswordPolicyTest` |
| SR-54 Sessions ended after a change, reset or deactivation | ✓ | `PasswordChangeIT`; `SubjectsAndStudentsAdminIT` |
| SR-55 Random reset password | ✓ | `SubjectsAndStudentsAdminIT` |
| SR-56 Admins never get a roll-number password | ✓ | `AdminBootstrapTest` |
| SR-57 No self-registration | ✓ | No such endpoint; `PageRoutingIT.theLoginPageOffersNothingTheProjectForbids` |
| SR-58 Email never claimed verified | ✓ | Profile and settings say "login email"; the API marks it as an identifier only |
| SR-59 EX-AUTH-01 not with real data until the owner decides | ✓ | F12, ADR 0010 |

## 5. Release criteria of [11-security-plan.md](11-security-plan.md) §16

| Criterion | State |
| --- | --- |
| All mandatory security tests pass | Met on 2026-10-14 (`./mvnw verify`) |
| No secret in the repository history | Met (history scan) |
| No open high or critical dependency alert without a decision | Not checkable until Dependabot is on (F6, F7) |
| Data API off; schema `app` not exposed; Data API request returns nothing | Owner, manual |
| Connects as `cc_app`; RLS, grants and policies match | Met in tests; on Supabase the same migrations ran (F5) |
| GitHub security features on | Owner (F6) |
| Bucket private; unsigned URL refused | Owner (T3-01) |
| Production cookies Secure and HttpOnly; HTTP redirects to HTTPS | Met in `ProductionProfileIT`; check again on Heroku (F13) |
| Security headers on a production response | Met locally with the prod profile; check again on Heroku |
| Bootstrap admin password changed | Owner, after the first start |
| `STUDENT_EMAIL_DOMAIN` confirmed (OQ-15) | Owner |
| ADR 0010 followed: create accounts shortly before first login, review awaiting accounts (OQ-17) | Owner |
| No account left awaiting first login that should not be | Owner, after loading demo data |
| Only synthetic data loaded | Owner |
| Actuator exposes only `health` | Met (`ProductionProfileIT`, `ApplicationIT`) |

## 6. Next review

Repeat this review when the real AI provider is connected (T5-06, with the behaviour run B1 to B20), when the storage bucket is created (T3-01), before the first deployment, and before any real student data is loaded.
