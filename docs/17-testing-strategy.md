# 17. Testing Strategy

> Status: Draft v0.1 · Owner: Laxman Sirvi · Last updated: 2026-10-10

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
| `ResultService` | No published semesters (CGPA null); one semester; credit-weighted mean across several; unpublished rows excluded; zero total credits. |
| `MarksService` | Marks above maximum; negative; absent with marks; decimal marks; one invalid row rejects the whole sheet. |
| `QuizEvaluationService` | All correct; none correct; partial; unanswered counts wrong; answer for a question not in the attempt; duplicate answers for one question. |
| `ProgressService` | Fewer than 5 answers (not enough data); exactly 5; 59.99% (needs revision); 60% (improving); 79.99% (improving); 80% (strong); only the latest 20 answers count; recommendations limited to three, notes first. |
| `TimetableService` | Overlapping, adjacent (allowed) and separate entries; end before start. |
| Password policy | 7, 8, 72 and 73 characters; equal to username; equal to current. |
| Lockout | Counter increments; locks on the fifth; unlocks after 15 minutes (controllable clock); resets on success. |
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

Responses are asserted field by field against [openapi.yaml](openapi.yaml). If a response and the contract differ, one of them is wrong and the difference is resolved before merge.

## 5. Repository and PostgreSQL tests

- Flyway migrates an empty container to the latest version without error.
- Hibernate validation (`ddl-auto=validate`) passes against the migrated schema.
- Each unique constraint, check constraint and foreign key rejects bad data.
- Each custom query returns the right rows, with the right owner and status filters.
- Pagination and sorting work and are stable.
- Row Level Security is enabled on every table (query `pg_tables`).

## 6. Authentication and authorization tests

These are the most important tests in the project.

### Authentication

1. Correct login creates a session; the session ID changes at login.
2. Wrong password, unknown user and inactive user return the identical response.
3. Fifth failure locks; a correct password during the lock still fails.
4. Logout invalidates the session; the old cookie no longer works.
5. A must-change-password account gets 403 `AUTH_PASSWORD_CHANGE_REQUIRED` everywhere except change-password and logout.
6. Password change invalidates other sessions.
7. The session cookie is `HttpOnly`, and `Secure` under the production profile.

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

Z1 and Z2 are generated from the application's own endpoint list, so a new endpoint is covered automatically and cannot be forgotten.

## 7. Frontend validation tests

There is no JavaScript test framework in the approved stack. Frontend behaviour is covered by:

- The API tests above, since the server never relies on the frontend to validate.
- A manual checklist per form: required fields, length limits, error placement, focus on the first invalid field, values kept after failure, button disabled during submission.

Adding a JavaScript test tool needs approval as a new dependency.

## 8. Upload and storage tests

| Case | Expected |
| --- | --- |
| Valid PDF with complete metadata | 201; metadata row; file in the fake storage |
| Text file renamed `.pdf` | 415 `UPLOAD_INVALID_TYPE` |
| PDF signature but unparseable | 415 |
| File over 10 MB | 413 `UPLOAD_TOO_LARGE` |
| Question paper without exam year | 400 |
| File name containing `../` or odd characters | Accepted; storage path is server-generated and unaffected |
| Storage fails during upload | 503; no database row |
| Database fails after upload | Stored object is deleted |
| Signed link for a published resource | 200 with expiry no more than five minutes ahead |
| Storage fails when signing | 503 `STORAGE_UNAVAILABLE`; listing still works |
| Delete | Row and object both gone |

One manual check against the real bucket confirms that an unsigned URL is refused and an expired signed URL stops working.

## 9. AI tests

Automated, with the mock: all items in [14-AI-security-and-privacy.md](14-AI-security-and-privacy.md) §12 and [15-AI-features-handoff.md](15-AI-features-handoff.md) §13.

Manual, with the real provider, before release: behaviour cases B1 to B20 in [13-AI-rules.md](13-AI-rules.md) §4. For each, record pass or fail and a one-line note. The mandatory malicious-prompt cases are B8, B11, B12, B13, B14 and B16.

AI answers vary between runs. A case passes if the behaviour matches the expectation, not if the wording matches.

## 10. End-to-end scenarios

Run manually on the deployed application before submission, and as integration tests where practical.

**E1 Student journey**

1. Log in as a demo student; forced password change on first login.
2. Dashboard shows attendance, results, today's classes, notices.
3. Attendance shows a subject flagged below required.
4. Results show SGPA per semester and CGPA.
5. Find a DBMS question paper by filters; preview it; bookmark it.
6. Ask the assistant to explain a topic from it.
7. Take a 5-question test on that unit; submit; see score and explanations.
8. Progress shows the unit's level; a weak unit appears in recommendations with resources.
9. Log out; the back button shows nothing protected.

**E2 Admin journey**

1. Log in as admin.
2. Create a student; note the temporary password shown once.
3. Mark attendance for a class; correct one entry.
4. Create an assessment, enter marks, publish.
5. Publish a notice with a due date.
6. Upload a PDF resource; try a non-PDF and see it rejected.
7. Add and publish a quiz question.
8. Log in as the student and confirm each change is visible.
9. Confirm the marks change and attendance correction are in the audit log.

**E3 Access control**

Logged in as a student, try to open an admin page, call an admin endpoint, and fetch another student's attempt. All are refused.

## 11. Edge cases and invalid input

Checked across features:

- Empty lists and brand-new accounts with no data.
- Longest allowed values and one character beyond.
- Unicode, emoji, right-to-left text, and HTML or script text in every free-text field (stored and shown as literal text).
- Leading and trailing spaces; roll numbers and usernames in mixed case.
- Zero, negative and decimal numbers where integers are expected.
- Invalid dates, future dates, 29 February.
- Page beyond the last; `size` of 0 and 101; unknown sort field.
- Double-clicking submit; repeating an idempotent request.
- Two admins saving the same marks sheet (last write wins and both are audited).
- Request bodies over the size limit.

## 12. Regression

- The full automated suite runs on every pull request through CI and must pass to merge.
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

## 14. Release acceptance criteria

The release is accepted when all of these are true and recorded:

- [ ] `./mvnw verify` passes with no skipped or disabled tests.
- [ ] Every P0 requirement's acceptance criteria are demonstrated.
- [ ] All mandatory authorization scenarios Z1 to Z14 pass.
- [ ] Upload tests pass, including the manual bucket check.
- [ ] AI automated tests pass; behaviour cases recorded.
- [ ] E1, E2 and E3 pass on the deployed application.
- [ ] Accessibility and responsiveness checklist complete.
- [ ] Security release checklist complete ([11-security-plan.md](11-security-plan.md) §16).
- [ ] No open defect that breaks a P0 requirement or a security rule.

## 15. Test data

- Built by test fixtures and builders in `src/test`, or by `DevDataSeeder` for manual testing.
- Names such as "Demo Student One" and roll numbers such as `23CSE001`. Never real people.
- Tests create what they need and do not depend on leftover data or on each other's order.
- Passwords in tests are generated or fixed test strings that are used nowhere else.
