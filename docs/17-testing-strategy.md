# 17. Testing Strategy

> Status: Draft v0.2 · Owner: Laxman Sirvi · Last updated: 2026-10-11

This document defines how the project proves that it works. A feature without the tests described here is not done.

## 1. Principles

1. **Test the rules hardest.** Attendance, CGPA, quiz scoring, progress levels and access control get the most thorough tests, because they are what the application promises.
2. **Every denied case is a test.** For each row of the [authorization matrix](10-authorization-matrix.md), the forbidden actors are tested as well as the permitted one.
3. **Real PostgreSQL.** Database tests run against PostgreSQL through Testcontainers, never H2.
4. **No real external calls.** Automated tests never call the AI provider or Supabase Storage. They use `MockAiProvider` and an in-memory `StorageClient`.
5. **Synthetic data only.**
6. **Tests are not negotiable.** A failing test is fixed or reported. It is never disabled to get a green build.

## 2. Test levels

| Level | Tool | Scope | Speed | Naming |
| --- | --- | --- | --- | --- |
| Unit | JUnit 5, Mockito, AssertJ | One class, no Spring context | Milliseconds | `*Test` |
| Web slice | `@WebMvcTest`, MockMvc, Spring Security Test | One controller with security, services mocked | Fast | `*ControllerTest` |
| Repository | `@DataJpaTest` + Testcontainers | Queries, constraints, migrations | Seconds | `*RepositoryIT` |
| Integration | `@SpringBootTest` + Testcontainers + MockMvc | Whole application, real database, fake externals | Seconds | `*IT` |
| Manual | Checklists in this document | UI, accessibility, real provider, deployed app | Minutes | |

`./mvnw test` runs unit and web-slice tests. `./mvnw verify` also runs `*IT` tests and needs Docker.

## 3. Unit tests

Required for every service with business logic. Minimum cases:

| Class | Cases |
| --- | --- |
| `AttendanceService` | No classes held (percentage is null, not 0); all present; all absent; rounding to two decimals `HALF_UP`; exactly at the required percentage is **not** below; just under is below; overall across subjects. |
| Attendance calculator (P1) | Below target; exactly at target; above target (classes that can be missed); target 100% with an absence; zero classes. |
| `ResultService` | No published semesters (CGPA null); one semester; mean weighted by credits **registered** across several; a semester with a failed course, where credits earned are fewer than credits registered (the weight is still the registered credits; the example in the database design §4.8 must give 7.12, not 7.18); unpublished rows excluded; withheld semesters excluded; rounding `HALF_UP`. |
| `MarksService` | Marks above maximum; negative; absent with marks; decimal marks; one invalid row rejects the whole sheet. |
| `AuditService` | Each audited action writes one row; a save with no real change writes none; password events have null old and new values; snapshots contain only allow-listed fields; an entity cannot be passed in. |
| `QuizEvaluationService` | All correct; none correct; partial; unanswered counts wrong; answer for a question not in the attempt; duplicate answers for one question. |
| `ProgressService` | Fewer than 5 answers (not enough data); exactly 5; 59.99% (needs revision); 60% (improving); 79.99% (improving); 80% (strong); only the latest 20 answers count; recommendations limited to three, notes first. |
| `TimetableService` | Overlapping, adjacent (allowed) and separate entries; end before start. |
| `PasswordPolicy` | 7, 8, 72 and 73 characters; equal to the roll number in any letter case; equal to the email in any letter case; equal to the part of the email before the `@`; equal to the current password; confirmation does not match; an admin account, which has no roll number. |
| Email normalisation and generation | Surrounding spaces; mixed case; a roll number in any letter case gives the same lower-case email; the domain comes from configuration; an invalid domain setting fails at startup; text that is not an email address. |
| Lockout | Counter increments; blocks on the fifth; unblocks after 15 minutes (controllable clock); resets on success; identical behaviour for an email with no account. |
| Upload validator | Valid PDF; wrong signature; wrong extension; wrong content type; oversized; zero bytes; unparseable PDF. |
| AI classes | See [15-AI-features-handoff.md](15-AI-features-handoff.md) §13. |

Time-dependent logic takes a `java.time.Clock` so tests can fix the time.

## 4. Web-slice and API contract tests

For **every** endpoint:

| Case | Expected |
| --- | --- |
| Valid request, correct role | Documented 2xx and response fields |
| Each validation rule broken | 400 `VALIDATION_FAILED` naming the field |
| No session | 401 `AUTH_REQUIRED` |
| Wrong role | 403 `ACCESS_DENIED` |
| State-changing request without CSRF token | 403 `CSRF_INVALID` |
| Unknown field in the body | 400 |
| Error response shape | Problem detail with `code` and `traceId`, no stack trace |

Responses are asserted field by field against [openapi.yaml](openapi.yaml). If a response and the contract differ, one of them is wrong and the difference is resolved before the change is committed.

## 5. Repository and PostgreSQL tests

- Flyway migrates an empty container to the latest version without error.
- Hibernate validation (`ddl-auto=validate`) passes against the migrated schema.
- Each unique constraint, check constraint and foreign key rejects bad data.
- Each custom query returns the right rows, with the right owner and status filters.
- Pagination and sorting work and are stable.
- **Roles and privileges.** The container is started with a script that creates `cc_migrator` and `cc_app`, so these tests run against the real model in the database design §11:
  - Flyway runs as `cc_migrator`; the application's pool connects as `cc_app`.
  - `cc_app` owns no object and has neither `SUPERUSER` nor `BYPASSRLS` (`pg_roles`, `pg_tables`).
  - Every application table has RLS enabled (`pg_tables`).
  - Every application table has policies for `cc_app` only, one per command it is granted (`pg_policies`).
  - The privileges of `cc_app` equal the table in the database design §11.3 exactly: nothing missing, nothing extra (`information_schema.role_table_grants`).
  - `cc_app` can insert into every table it needs, including identity columns.

## 6. Authentication and authorization tests

These are the most important tests in the project.

### Authentication

| # | Scenario | Expected |
| --- | --- | --- |
| AU1 | A provisioned student logs in with the institutional email and the temporary password (the roll number) | 200; a new session whose ID differs from any ID before login; `mustChangePassword` is true |
| AU2 | Wrong password; an email with no account; a deactivated account | The identical response for all three: same status, same body, same headers apart from the trace ID |
| AU3 | Five consecutive failures for an email that has an account, and for one that has none | Both are then blocked for 15 minutes with the identical response; a correct password during the block still fails; nothing distinguishes the two cases |
| AU4 | Logout | The session is invalidated; the old cookie no longer works |
| AU5 | With a temporary password, call every endpoint in the application directly (generated from the endpoint list) and open every protected page | Only `/auth/csrf`, `/auth/me`, `/auth/change-password` and `/auth/logout` succeed; every other endpoint returns 403 `AUTH_PASSWORD_CHANGE_REQUIRED`; pages redirect to change-password |
| AU6 | Successful password change | Needs the correct current password and the new one twice; the flag is cleared; the session ID changes; the user's other sessions stop working; the dashboard then opens |
| AU7 | Session cookie | `HttpOnly` and `SameSite=Lax`; `Secure` under the production profile |
| AU8 | Session idle for 30 minutes (controllable clock) | The next request gets 401 `AUTH_REQUIRED` |
| AU9 | After the change, log in with the old temporary password; later, try to change the password back to the roll number | The login fails with the generic response; the change is rejected |
| AU10 | A new password that is too short, too long, the roll number, the email, the part of the email before the `@` (each also in mixed case), the current password, or different from its confirmation | Each is rejected with a field error; the stored password and the flag are unchanged |
| AU11 | Email input: surrounding spaces; mixed case; text that is not an email address; an empty value; a roll number with no domain | Spaces and case are ignored and login succeeds; the others are rejected as invalid input, identically for any text |
| AU12 | Rate limits | The 21st attempt from one IP address within 10 minutes gets 429; a successful login resets the per-email counter |
| AU13 | CSRF and cookies | Login without the CSRF token gets 403 `CSRF_INVALID`; with the token from `/auth/csrf` it succeeds; the same holds for change-password and logout |
| AU14 | An admin resets a student's password while the student is logged in on two sessions | A random temporary password is returned once; both sessions stop working; any block is cleared; the roll number does not work; the student must change the password; an audit row exists with null values |
| AU15 | An admin deactivates, then reactivates, a logged-in student | Sessions stop at once; login gives the generic failure while deactivated; after reactivation the previous password works |
| AU16 | Capture application logs, API responses and audit rows during provisioning, login, failed login, password change and reset | No password and no hash anywhere except the one-time reset response; the password field of a request is never logged |
| AU17 | Admin accounts | The bootstrap admin is created from configuration, not with a roll-number password; provisioning cannot create an admin; no request can change a role |

### Provisioning and identity

| # | Scenario | Expected |
| --- | --- | --- |
| PV1 | Provision a student from valid roster details | Account and profile are created in one transaction; the email is the lower-case roll number, `@`, the configured domain; the stored hash matches the roll number; `must_change_password` is true; the response contains no password; an `ACCOUNT_CREATED` audit row exists |
| PV2 | Provision with a different configured domain | The generated email uses that domain; no domain is written in code or tests other than a reserved example |
| PV3 | Invalid records: missing roll number; a roll number with a space, a symbol or an `@`; a duplicate roll number; the same roll number in another letter case; a missing required field | Each is rejected with its reason; nothing is saved, including no half-created account |
| PV4 | Insert directly into the database: a second account with the same email; the same email in upper case; an email with surrounding spaces; a second profile with the same roll number | The database rejects every one |
| PV5 | Compare a student's academic records before and after their login email is changed by an approved data migration | The roll number and every record are unchanged; they refer to the student ID, not the email |
| PV6 | Account list | Shows awaiting first login, active, locked and deactivated correctly |

### Email-login acceptance scenarios

The owner's change request of 2026-10-11 named fourteen scenarios that must be testable. Each maps to the tests that prove it. All are part of the release criteria in section 14.

| # | Scenario | Proved by |
| --- | --- | --- |
| S1 | A provisioned student logs in with the institutional email and the temporary roll-number password | PV1, AU1 |
| S2 | A wrong email or password returns a generic authentication failure | AU2 |
| S3 | The application does not reveal whether an account exists | AU2, AU3, AU11 |
| S4 | A successful first login forces the password-change workflow | AU1, AU5 |
| S5 | Direct API requests cannot bypass the mandatory password change | AU5 |
| S6 | The student cannot use the old password after a successful change | AU9 |
| S7 | A student cannot access another student's marks or attendance | Z3, Z4, Z5 |
| S8 | Students cannot access administrator endpoints or change their role | Z2, Z7, AU17 |
| S9 | Failed logins follow the lockout and rate-limiting rules | AU3, AU12 |
| S10 | Passwords and hashes never appear in logs, API responses or audit records | AU16, Z16, Z17 |
| S11 | Email uniqueness and roll-number uniqueness are enforced by the database | PV4 |
| S12 | The login flow works with the approved cookie and CSRF configuration | AU7, AU13 |
| S13 | Sessions follow the invalidation policy after a password change or reset | AU6, AU14, AU15 |
| S14 | Administrator provisioning and reset operations leave the right audit trail | PV1, AU14, Z16 |

### Authorization: mandatory scenarios

| # | Scenario | Expected |
| --- | --- | --- |
| Z1 | Visitor calls every protected endpoint | 401 on all |
| Z2 | Student calls every `/api/v1/admin/**` endpoint | 403 on all |
| Z3 | Student A requests student B's quiz attempt by ID | 404 |
| Z4 | Student A submits answers to student B's attempt | 404; B's attempt unchanged |
| Z5 | Student sends `studentId` of another student as a query parameter or body field to `/me` endpoints | Ignored or 400; only own data returned |
| Z6 | Student tries every write path for marks, attendance and results | 403; database unchanged |
| Z7 | Student sends `role: ADMIN` or `score: 20` in a request body | Rejected or ignored; no privilege or score change |
| Z8 | Student requests an unpublished assessment's marks, a draft notice, a hidden resource | Not returned; 404 for direct ID |
| Z9 | Student requests a signed link for a hidden resource | 404 |
| Z10 | Student removes another student's bookmark | No effect on the other student |
| Z11 | Admin calls student-only endpoints (`/attendance/me`, AI, quiz) | 403 |
| Z12 | Visitor requests `/student/dashboard.html` and `/admin/dashboard.html` | Redirect to login |
| Z13 | Quiz start response | Contains no `correctOption` and no `explanation` |
| Z14 | Any actuator endpoint other than health | Not accessible |
| Z15 | The application's database role attempts `UPDATE`, `DELETE` and `TRUNCATE` on `audit_logs` | Each statement fails; the rows are unchanged |
| Z16 | After an admin password reset and a user's own password change, inspect every column of the new audit rows | `old_value` and `new_value` are NULL; no column contains the password, the temporary password or the hash. A roll number may appear in other rows as an identifier, for example inside a login email; it is never stored as a password value |
| Z17 | Inspect every audit snapshot type the application defines | None has a field for a password or a hash; `AuditService` does not accept an entity |
| Z18 | Privileges of `anon`, `authenticated` and `service_role` on schema `app` (automated where those roles exist), and the release-time Data API request | No privilege; the Data API returns no application data |
| Z19 | A marks save in which the audit insert fails | The marks change is rolled back as well |

Z1 and Z2 are generated from the application's own endpoint list, so a new endpoint is covered automatically and cannot be forgotten.

## 7. Frontend validation tests

There is no JavaScript test framework in the approved stack. Frontend behaviour is covered by:

- The API tests above, since the server never relies on the frontend to validate.
- A manual checklist per form: required fields, length limits, error placement, focus on the first invalid field, values kept after failure, button disabled during submission.

Adding a JavaScript test tool needs approval as a new dependency.

## 8. Upload and storage tests

| # | Case | Expected |
| --- | --- | --- |
| UP1 | Valid PDF with complete metadata | 201; metadata row; file in the fake storage |
| UP2 | Text file renamed `.pdf` | 415 `UPLOAD_INVALID_TYPE` |
| UP3 | PDF signature but unparseable | 415 |
| UP4 | File over 10 MB | 413 `UPLOAD_TOO_LARGE` |
| UP5 | Question paper without exam year | 400 |
| UP6 | File name containing `../` or odd characters | Accepted; storage path is server-generated and unaffected |
| UP7 | Storage fails during upload | 503; no database row |
| UP8 | Database fails after upload | Stored object is deleted |
| UP9 | Signed link for a published resource | 200 with expiry no more than five minutes ahead |
| UP10 | Storage fails when signing | 503 `STORAGE_UNAVAILABLE`; listing still works |
| UP11 | Delete | Row and object both gone |

One manual check against the real bucket confirms that an unsigned URL is refused and an expired signed URL stops working.

## 9. AI tests

Automated, with the mock: all items in [14-AI-security-and-privacy.md](14-AI-security-and-privacy.md) §12 and [15-AI-features-handoff.md](15-AI-features-handoff.md) §13.

Manual, with the real provider, before release: behaviour cases B1 to B20 in [13-AI-rules.md](13-AI-rules.md) §4. For each, record pass or fail and a one-line note. The mandatory malicious-prompt cases are B8, B11, B12, B13, B14 and B16.

AI answers vary between runs. A case passes if the behaviour matches the expectation, not if the wording matches. Equally, a reply that parses as valid JSON has not passed anything: the behaviour cases judge what the reply says.

Acceptance of the AI requirements uses the real provider. The mock provider cannot stand in for these cases (OQ-9).

## 10. End-to-end scenarios

Run manually on the deployed application before submission, and as integration tests where practical.

**E1 Student journey**

1. Log in as a newly provisioned demo student with the institutional email and the roll number. Nothing opens until the password is changed. Afterwards the roll number no longer works.
2. Dashboard shows attendance, results, today's classes, notices.
3. Attendance shows a subject flagged below required.
4. Results show SGPA per semester and CGPA.
5. Find a DBMS question paper by filters; preview it; bookmark it.
6. Ask the assistant to explain a topic from it, using the real provider.
7. Take a 5-question test on that unit; submit; see score and explanations.
8. Progress shows the unit's level; a weak unit appears in recommendations with resources.
9. Log out; the back button shows nothing protected.

**E2 Admin journey**

1. Log in as admin.
2. Provision a student from roster details: the generated email is shown and no password is shown; a duplicate roll number is rejected. Reset another student's password and see a random temporary password once.
3. Mark attendance for a class; correct one entry.
4. Create an assessment, enter marks, publish.
5. Publish a notice with a due date.
6. Upload a PDF resource; try a non-PDF and see it rejected.
7. Add and publish a quiz question.
8. Log in as the student and confirm each change is visible.
9. Confirm the marks change and the attendance correction are in the audit log, using a read-only database query (the screen for browsing the log is P1), and that no audit row contains password material.

**E3 Access control**

Logged in as a student, try to open an admin page, call an admin endpoint, and fetch another student's attempt. All are refused.

## 11. Edge cases and invalid input

Checked across features:

- Empty lists and brand-new accounts with no data.
- Longest allowed values and one character beyond.
- Unicode, emoji, right-to-left text, and HTML or script text in every free-text field (stored and shown as literal text).
- Leading and trailing spaces; roll numbers and email addresses in mixed case.
- Zero, negative and decimal numbers where integers are expected.
- Invalid dates, future dates, 29 February.
- Page beyond the last; `size` of 0 and 101; unknown sort field.
- Double-clicking submit; repeating an idempotent request.
- Two admins saving the same marks sheet (last write wins and both are audited).
- Request bodies over the size limit.

## 12. Regression

- The full automated suite is run locally before every commit and must pass. CI runs it again on every push to `main`; a red result is fixed before any other work.
- Every bug fix starts with a test that fails because of the bug.
- Before each milestone gate, re-run the manual scenario for earlier milestones.

## 13. Accessibility and responsiveness

Manual checks on every page before release:

- [ ] Usable with keyboard only; focus always visible; no keyboard trap.
- [ ] One `<h1>`; headings in order; landmarks present.
- [ ] Every input has a label; errors are announced.
- [ ] Colour is never the only signal.
- [ ] Contrast meets WCAG AA (checked with a contrast tool).
- [ ] Works at 200% zoom.
- [ ] Works at 360 px, 768 px and 1280 px widths with no sideways page scroll.
- [ ] One pass with a screen reader on login, dashboard, quiz and assistant.
- [ ] Tested in current Chrome and Firefox on desktop and Chrome on Android.

### Results of 2026-10-14 (task `T6-02`)

Run in the desktop app's built-in Chromium browser on a local test database, logged in as a student and as an admin, with a script on every page and screenshots of the new ones.

| Check | Pages | Result |
| --- | --- | --- |
| No sideways page scroll at 360 px and 375 px | All 15 student pages, all 11 admin pages, login, privacy, terms | **Passed after fixes.** Two pages failed at first: the AI Assistant tabs and the level tags on Progress pushed the page wider than the screen. Fixed by letting page headers and list rows shrink, shortening the third assistant tab label on phones, and keeping the score column of a unit row at full size |
| No sideways page scroll at 768 px | Progress, Assistant, Timetable, Profile | Passed |
| Exactly one `<h1>` | Every page | Passed |
| Every input, select and text area has a label | Every page | Passed (also enforced for the static pages by `StaticFrontendRulesTest`) |
| Every link and button has a name, including icon-only ones | Every page | Passed |
| No duplicate element IDs | Every page | Passed |
| Images have alternative text | Every page | Passed |
| Three figures on a phone leave no empty half-card | Test history | **Fixed:** the third figure now takes the whole second row |

Still to do by hand before release: keyboard-only use, a screen reader pass, 200% zoom, a contrast tool, and Firefox and Chrome on Android.

## 14. Release acceptance criteria

The release is accepted when all of these are true and recorded:

- [ ] `./mvnw verify` passes with no skipped or disabled tests.
- [ ] Every P0 requirement's acceptance test in section 16 passes. None is skipped unless a scope change for it is recorded in the roadmap §9.
- [ ] All mandatory scenarios AU1 to AU17, PV1 to PV6 and Z1 to Z19 pass.
- [ ] Upload tests pass, including the manual bucket check.
- [ ] AI automated tests pass; behaviour cases B1 to B20 are recorded against the real provider, or OQ-9 records an approved exception.
- [ ] E1, E2 and E3 pass on the deployed application.
- [ ] Accessibility and responsiveness checklist complete.
- [ ] Security release checklist complete ([11-security-plan.md](11-security-plan.md) §16).
- [ ] No open defect that breaks a P0 requirement or a security rule.

## 15. Test data

- Built by test fixtures and builders in `src/test`, or by `DevDataSeeder` for manual testing.
- Names such as "Demo Student One" and roll numbers such as `23CSE001`. Never real people.
- Tests create what they need and do not depend on leftover data or on each other's order.
- Passwords in tests are generated or fixed test strings that are used nowhere else.

## 16. Acceptance tests by requirement

Every P0 requirement has one acceptance test. It passes when all the evidence listed for it passes. The matching implementation tasks are in the traceability matrix in [16-implementation-plan.md](16-implementation-plan.md).

Notation: `AU`, `PV`, `Z` and `UP` numbers are the scenarios in sections 6 and 8. `B` numbers are the behaviour cases in [13-AI-rules.md](13-AI-rules.md). `E1.3` means step 3 of end-to-end scenario E1 in section 10. A class name such as `AttendanceServiceTest` is the unit test suite described in section 3. "Controller test" means the standard cases in section 4 for that requirement's endpoints.

| Test | Requirement | Passes when | Evidence |
| --- | --- | --- | --- |
| AT-AUTH-001 | FR-AUTH-001 | A student logs in with the institutional email and an admin with theirs; every failure is generic and reveals nothing about the account | AU1, AU2, AU3, AU11, AU13; E1.1; E2.1 |
| AT-AUTH-002 | FR-AUTH-002 | Logout and idle expiry end the session; nothing protected is shown afterwards | AU4, AU8; E1.9 |
| AT-AUTH-003 | FR-AUTH-003 | A temporary password must be changed before anything else works, even by direct API calls; the policy holds; the old password stops working; sessions follow the policy | AU5, AU6, AU9, AU10; `PasswordPolicy` unit tests; E1.1 |
| AT-AUTH-004 | FR-AUTH-004 | No access across roles or across students, on any endpoint | Z1 to Z12, Z14; E3 |
| AT-AUTH-005 | FR-AUTH-005 | An email is blocked after five failures, identically with or without an account; the per-IP limit applies; a reset clears the block | AU3, AU12, AU14; lockout unit tests |
| AT-AUTH-006 | FR-AUTH-006 | Emails are generated, normalised and unique; roll numbers stay separate and unique; both are enforced by the database; the domain is configuration | PV1 to PV5; email unit tests |
| AT-STU-001 | FR-STU-001 | All six dashboard cards show correct data, and each has an empty state | Controller test; integration test with seeded data; E1.2 |
| AT-STU-002 | FR-STU-002 | Profile fields are shown and cannot be changed by the student | Controller test; Z5 |
| AT-ATT-001 | FR-ATT-001 | Subject-wise and overall percentages are correct; history is shown | `AttendanceServiceTest`; controller test; E1.3 |
| AT-ATT-002 | FR-ATT-002 | The flag appears below the required percentage and not at it | `AttendanceServiceTest` boundary cases; E1.3 |
| AT-MRK-001 | FR-MRK-001 | Only published marks are returned; absent is shown as absent | Controller test; integration test; Z8 |
| AT-MRK-002 | FR-MRK-002 | CGPA is weighted by credits registered; unpublished and withheld semesters are left out | `ResultServiceTest`; controller test; E1.4 |
| AT-TT-001 | FR-TT-001 | The student sees the timetable of their own branch, semester and section | Controller test; integration test |
| AT-NOT-001 | FR-NOT-001 | Only published, correctly targeted notices are returned; due dates appear as deadlines | Integration test; Z8 |
| AT-NOT-002 | FR-NOT-002 | A PDF or picture is attached, replaced and removed; pictures are stored as new JPEGs; wrong files are refused with nothing stored; only students who may see the notice get a link; removing or deleting removes the file | `NoticeDocumentIT` |
| AT-LIB-001 | FR-LIB-001 | Every filter and pagination work, alone and combined | Repository tests; controller test; E1.5 |
| AT-LIB-002 | FR-LIB-002 | Search matches title and description, ignores case, and escapes wildcards | Repository tests |
| AT-LIB-003 | FR-LIB-003 | A signed link is issued only for a published resource and lasts at most five minutes | UP9, UP10; Z9; the manual bucket check in section 8; E1.5 |
| AT-LIB-004 | FR-LIB-004 | Bookmarking is idempotent and affects only the student's own list | Controller test; Z10; E1.5 |
| AT-CMP-001 | FR-CMP-001 to FR-CMP-004 | A public complaint shows its author only when chosen; private and secure ones reach only the administrator; a secure complaint never carries the student in any response or audit entry; support is once each, group from 5; rejected, withdrawn and handled states; the daily limit; validation; roles and CSRF | `ComplaintIT` |
| AT-LIB-006 | FR-LIB-006 | Past papers: search escapes wildcards; a student sees only published papers; a signed link is short-lived; saving is idempotent and private; the admin upload rejects non-PDFs and duplicates; a paper is found by its course code, and several search words match in any order; the folder path is parsed into branch, subject, year, month and exam | `PyqPathParserTest`; `PyqSearchTermsTest`; `PyqIT`; `DatabaseSecurityModelIT`; `EndpointAccessIT` |
| AT-QZ-001 | FR-QZ-001 | Questions are random and published; the response holds no answers; too few questions gives the documented error | Z13; integration test |
| AT-QZ-002 | FR-QZ-002 | The server enforces the time limit; one submission only; late submission is rejected | Integration test with a controllable clock; E1.7 |
| AT-QZ-003 | FR-QZ-003 | Java scores the attempt; the result shows answers and explanations; a score from the browser is ignored | `QuizEvaluationServiceTest`; Z7; E1.7 |
| AT-QZ-004 | FR-QZ-004 | A student sees only their own attempts | Z3, Z4; controller test |
| AT-PRG-001 | FR-PRG-001 | Levels change exactly at the documented thresholds | `ProgressServiceTest` |
| AT-PRG-002 | FR-PRG-002 | Each weak unit gets up to three resources, notes first, and a retake link | `ProgressServiceTest`; E1.8 |
| AT-AI-001 | FR-AI-001 | Explain returns a labelled answer in the requested format, and declines off-topic requests | Controller and integration tests with the mock; B1, B2, B3, B5, B7 with the real provider; E1.6 |
| AT-AI-002 | FR-AI-002 | Summarize enforces its length limits and uses only the supplied text | Controller and integration tests with the mock; B6, B11 with the real provider |
| AT-AI-003 | FR-AI-003 | Generated questions pass validation; invalid ones are discarded | `GeneratedQuestionValidatorTest`; integration test with the mock; B4 with the real provider |
| AT-AI-004 | FR-AI-004 | The per-user limit, the application-wide ceiling and the kill switch each block the call; failures do not consume quota; the rest of the application works with AI off | `AiUsageServiceTest`; items 6, 7 and 10 of the AI security document §12 |
| AT-ADM-001 | FR-ADM-001 | Provision, search, edit, deactivate, reactivate and reset work; invalid and duplicate records are rejected; the roll-number password is never returned; admins are not created this way | PV1, PV3, PV6; AU14, AU15, AU17; controller test; E2.2 |
| AT-ADM-002 | FR-ADM-002 | A sheet saves once, re-saves without duplicates, rejects future dates, and audits corrections | Integration test; E2.3 |
| AT-ADM-003 | FR-ADM-003 | Marks save all-or-nothing; students see them only when published; changes are audited | `MarksServiceTest`; integration test; E2.4 |
| AT-ADM-004 | FR-ADM-004 | A semester result is validated, stored and audited | Controller test; integration test |
| AT-ADM-005 | FR-ADM-005 | Subjects, units and timetable entries can be managed; overlapping entries are rejected | `TimetableServiceTest`; controller test |
| AT-ADM-006 | FR-ADM-006 | A notice moves between draft and published; drafts are never visible to students | Integration test; Z8; E2.5 |
| AT-ADM-007 | FR-ADM-007 | Only valid PDFs within the size limit are stored; details can be edited; a resource can be hidden and deleted | UP1 to UP8, UP11; E2.6 |
| AT-ADM-008 | FR-ADM-008 | Questions move through draft, published and retired; a used question is not deleted | Controller test; integration test; E2.7 |
| AT-ADM-009 | FR-ADM-009 (the P0 part: writing entries) | Every audited action writes an entry in the same transaction; no entry holds password material; entries cannot be changed or deleted | `AuditServiceTest`; Z15, Z16, Z17, Z19; E2.9 |
| AT-UX-001 | FR-UX-001 | Every screen has loading, empty and error states; errors are plain and placed correctly; forms keep their values | The form checklist in section 7; the checklist in section 13; the error-format cases in section 4 |

P1 requirements built so far:

| Test | Requirement | Passes when | Evidence |
| --- | --- | --- | --- |
| AT-ADM-009V | FR-ADM-009 (viewer, P1) | An admin reads entries newest first with actor and subject; filters work; password events carry no values and no password text appears; paging is bounded; no way to change an entry; students and visitors are refused | `AdminAuditIT`; `EndpointAccessIT` |
| AT-ADM-010 | FR-ADM-010 | A valid file creates every student by the FR-ADM-001 rules; a file with any wrong, repeated or existing roll number creates nothing and lists every problem by line; a failure while creating undoes the whole file; only admins may import | `CsvReaderTest`; `StudentImportIT`; `EndpointAccessIT` |

The acceptance tests for FR-AI-001 to FR-AI-003 need the real provider. All acceptance tests are run again on the deployed application before release (tasks `T6-02` and `T6-05`).
