# 16. Implementation Plan

> Status: Draft v0.4 · Owner: Laxman Sirvi · Last updated: 2026-10-14

This plan breaks the work into ordered, verifiable tasks. One task is one small change, verified before the next begins. Milestones are defined in [03-scope-and-roadmap.md](03-scope-and-roadmap.md).

## How to use this plan

- Task IDs start with `T` followed by the phase number (`T2-05` is the fifth task of Phase 2). They are unrelated to the priority labels P0, P1 and P2 used in the PRD.
- Work top to bottom. Do not start a task until everything in its "Depends on" column is done.
- 👤 marks a task or gate that needs the owner (a decision, an approval, or an action only they can take).
- Each task ends with the report format in [AGENTS.md](../AGENTS.md#13-how-to-report).
- Track status in GitHub Issues, one issue per task, titled with the task ID. This file defines the tasks; the issue tracker records progress.
- Every task implicitly requires: tests written and passing, `./mvnw verify` green, documents updated, security rules respected.

## Build order: student side first

Decided by the owner on 2026-10-13: every student-facing feature is built before the admin screens. The task definitions below are unchanged; only the order is.

**How it works without the admin screens.** Student pages only read data. The data they read (accounts, attendance, marks, results, timetable, notices, resources, questions) is put in place by the synthetic seeder, which is extended as each feature arrives. Each migration, service and student endpoint is built and tested with seeded data; the matching admin endpoint and screen come later and write to the same tables.

**Design pass before the features (owner decision, 2026-10-13).** The owner asked for the user interface of every student page to be designed and built first, from Google Stitch designs, and for the features to follow. The pages were designed first on sample data; every one of them is now connected to its endpoint, and the sample file is gone ([07-ui-ux-spec.md](07-ui-ux-spec.md) section 18).

| Stage | Tasks | Result |
| --- | --- | --- |
| A. Finish the foundation | T1-09, T1-10, T1-11 | Login and change-password pages, page layout, student provisioning and seeded accounts |
| B. Student academics | T2-01, then the student parts of T2-05, T2-07, T2-09, T2-10, T2-11, T2-12, T2-13, T2-14 | Profile, attendance, marks, results, timetable, notices and dashboard, on seeded data |
| C. Student resources | T3-01 to T3-03, T3-05, T3-06, T3-07 | Browse, search, preview and bookmark, with seeded resources |
| D. Student Quick Exam and progress | T4-01, T4-03 to T4-08 | Tests, scoring, history, progress, on a seeded question bank |
| E. Student AI assistant | T5-01 to T5-08 | Explain, summarize, practice questions |
| F. Admin side | T2-02, T2-03, T2-04, T2-06, T2-08, the admin parts of T2-10 to T2-12, T3-04, T3-08, T4-02 | Every admin screen, plus the audit log |
| G. Access-control suites and release | T2-15, T3-09, T4-09, Phase 6 | The full denied-access tests, deployment |

Consequences to keep in mind:

- **Gates G2 to G4 cannot be passed until stage F**, because each needs an admin edit to appear for the student. Stages B to E are checked against their student-side acceptance tests only.
- **The audit log (T2-02) arrives in stage F.** Until then, password changes are not audited. Nothing is deployed before stage G.
- **The seeder becomes more important.** Its data must stay obviously synthetic, and it must never run in production unless the owner enables it for one start.
- **Access control is not postponed.** Every student endpoint gets its own "not logged in" and "someone else's record" tests when it is built; only the generated all-endpoint suites wait for stage G.

## Progress on 2026-10-14

| State | Tasks |
| --- | --- |
| **Built and tested** | T1-01 to T1-11; T2-01 to T2-15; T3-02 to T3-09; T4-01 to T4-09; T5-01 to T5-05, T5-07, and the automated part of T5-08; T6-01, T6-02 (automated and scripted checks), T6-03, T6-07 (as far as possible before deployment) |
| **Added at the owner's request** | Students edit their phone, text and photo; Settings pages; privacy policy and terms (FR-STU-003, FR-STU-004, FR-LEG-001, [ADR 0009](decisions/0009-student-personal-details-and-photo.md)) |
| **Waiting for the owner** 👤 | T0-01, T0-02, T0-04, T0-05 (reviews, open decisions, GitHub security settings); T3-01 (storage bucket and service key); T5-06 and the manual half of T5-08 (AI provider, key, behaviour run B1 to B20); T6-04 to T6-06 (Heroku app, first deployment, demo data) |
| **Also built** | The public landing page (FR-UX-002); from the P1 backlog, T7-07 CSV student import (FR-ADM-010), T7-03 audit log viewer (FR-ADM-009) and T7-10 past papers library (FR-LIB-006) |
| **Not started** | The rest of the P1 backlog of Phase 7 |

The last full run of `./mvnw verify` passed with 220 unit tests and 221 integration tests. What is open for the owner is listed in [21-security-review.md](21-security-review.md) §3.

## Phase 0: Decisions

| ID | Task | Depends on | Done when |
| --- | --- | --- | --- |
| T0-01 👤 | Review and approve documents 01 to 11, the root rule files, and the decision records marked Proposed (OQ-12) | | The README status table and the decision index show them Approved or Accepted, or list revisions |
| T0-02 👤 | Answer each open decision in the charter §11 no later than the task named in its "Needed by" column. Needed before Phase 1 starts: OQ-4, OQ-10, OQ-12 (for 0006), OQ-14 | | Charter updated; `TBD` and "Proposed" markers removed where answered |
| T0-03 👤 | Create the Supabase project. Switch the Data API off. Create the roles `cc_migrator` and `cc_app` and the schema `app` as in the database design §11.7. Put the session pooler details and both passwords in a local `.env`. Record the results of the checks in the database design §11.8 | OQ-10 | Both roles connect through the session pooler with a SQL client; `cc_app` cannot create a table; a Data API request returns nothing |
| T0-04 👤 | Enable GitHub secret scanning, push protection, Dependabot alerts and private vulnerability reporting | | Settings enabled. Checked on 2026-10-11: all are currently off, so the private reporting link in the issue templates does not work yet |
| T0-05 👤 | Review the email-login change: the revised PRD §4, [ADR 0008](decisions/0008-institutional-email-login.md) and security exception EX-AUTH-01. Choose a development value for `STUDENT_EMAIL_DOMAIN` | | ADR 0008 is confirmed as Accepted; OQ-16 is answered or its default accepted; a reserved example domain is set for development |

**Gate G0:** T0-01 and T0-05 complete. T0-02 to T0-04 may run alongside Phase 1 but must finish before the task that needs them.

## Phase 1: Foundation (M1)

| ID | Task | Main files | Depends on | Acceptance |
| --- | --- | --- | --- | --- |
| T1-01 | Create the Maven Spring Boot project on Java 21 with the wrapper, Spotless and the approved starters. Verify and record versions in the TRD. | `pom.xml`, `mvnw`, `CampusCompanionApplication` | G0 | `./mvnw verify` passes on an empty application; TRD version table has no TBD for core items |
| T1-02 | Configuration: profiles, `@ConfigurationProperties`, datasource through the session pooler as `cc_app`, Flyway set to connect as `cc_migrator` with schema `app`, Hikari limits, Actuator health only | `application*.yml`, `common.config` | T1-01, T0-03 | App starts locally against Supabase as `cc_app`; `/actuator/health` is UP; no other actuator endpoint is reachable; every variable in `.env.example` is bound |
| T1-03 | Flyway `V1__core.sql` in schema `app`: users (identified by `login_email`), student_profiles (roll number, no email column), subjects, subject_units, each with RLS enabled, grants and policies for `cc_app`. A Testcontainers start-up script that creates both roles. The role and privilege tests | `db/migration`, test resources | T1-02 | The migration applies cleanly as `cc_migrator` on an empty database; the role and privilege tests in the testing strategy §5 pass; PV4 passes (the database rejects a duplicate or non-normalised email and a duplicate roll number) |
| T1-04 | Entities and repositories for V1 | `auth`, `student`, `subject` | T1-03 | Repository tests pass against PostgreSQL; `ddl-auto=validate` passes |
| T1-05 | Error handling: error codes, exceptions, global handler producing problem details, request ID filter | `common.error`, `common.web` | T1-01 | Tests show each documented status and code; no stack trace in any response |
| T1-06 | Security: session login by institutional email (normalised), BCrypt, CSRF cookie-to-header, security headers, URL rules, `CurrentUser`, `STUDENT_EMAIL_DOMAIN` validated at startup | `common.security`, `auth` | T1-04, T1-05 | Login, logout, `/auth/me`, `/auth/csrf` work; AU1, AU2, AU4, AU7, AU11, AU13 pass; a visitor gets 401 |
| T1-07 | Lockout and login rate limit, applied to any email whether or not an account exists | `auth`, `RateLimitFilter` | T1-06 | AU3 and AU12 pass, with a controllable clock |
| T1-08 | `PasswordPolicy`; password change with the current password and the new one entered twice; the must-change-password filter; session renewal; admin bootstrap from `ADMIN_BOOTSTRAP_EMAIL` | `auth` | T1-06 | AU5, AU6, AU8, AU9, AU10 pass; the bootstrap creates one admin once, never with a roll-number password (AU17) |
| T1-09 | Frontend shell: tokens and base CSS, `api.js`, login page with institutional email and password fields, change-password page with the new password entered twice, student and admin layouts with navigation, standard states | `static/` | T1-06, T1-08 | Both roles log in and land on their own dashboard shell; expired session redirects to login |
| T1-10 | `StudentProvisioningService` (generates the email, sets the roll-number temporary password, links the profile, rejects invalid and duplicate records) and a `DevDataSeeder` that uses it | `student`, `common.config` | T1-08 | PV1 to PV5 pass; the seeder runs only in `dev` or when enabled; all data is clearly fictional and uses the example domain |
| T1-11 | CI: GitHub Actions workflow running `./mvnw verify` on every push to `main` 👤 approve the workflow | `.github/workflows` | T1-01 | A push to `main` shows a passing check |

**Gate G1 (M1 complete):** a seeded student logs in with an institutional email and the roll number, must change the password before anything else works, and is separated from the admin by role. Scenarios AU1 to AU13, AU16, AU17 and PV1 to PV5 pass.

## Phase 2: Academic management (M2)

| ID | Task | Depends on | Acceptance |
| --- | --- | --- | --- |
| T2-01 | Flyway `V2__academic.sql`: attendance_records, assessments, marks, semester_results, timetable_entries, notices, and audit_logs with its protection triggers, all with RLS, grants and policies | G1 | Migration and privilege tests pass; Z15 passes |
| T2-02 | `AuditService` with allow-listed snapshot records; wire it into the password change, bootstrap and provisioning code from `T1-08` and `T1-10` | T2-01 | The audit row is written in the same transaction and rolls back with it (Z19); password events store no values (Z16, Z17) |
| T2-03 | Admin: student accounts with UI. Provision through `StudentProvisioningService`; search by roll number, name or email; edit; deactivate and reactivate with session invalidation; reset with a random temporary password | T2-02, T1-10 | FR-ADM-001 criteria; AU14, AU15 and PV6 pass; the roll-number password is never returned; actions audited |
| T2-04 | Admin: subjects and units with UI | G1 | FR-ADM-005 (subjects) |
| T2-05 | `AttendanceService` (percentage, below-required flag) as plain Java with unit tests | T2-01 | Unit tests cover zero classes, rounding, threshold boundary |
| T2-06 | Admin attendance sheet (read and save) with UI | T2-05, T2-03 | FR-ADM-002; idempotent save; future date rejected; corrections audited |
| T2-07 | Student attendance summary and history with UI | T2-05 | FR-ATT-001, FR-ATT-002 |
| T2-08 | Admin assessments and marks sheet with UI | T2-02, T2-04 | FR-ADM-003; all-or-nothing save; changes audited with old and new values |
| T2-09 | Student marks view with UI | T2-08 | FR-MRK-001; unpublished assessments never returned |
| T2-10 | `ResultService` (CGPA weighted by credits registered) as plain Java; admin semester results; student results view. 👤 OQ-3 should be confirmed first; if it is not, build the documented default and keep its `TBD` marker | T2-02 | FR-ADM-004, FR-MRK-002; unit tests for the CGPA rule, including a semester with a failed course |
| T2-11 | Timetable: admin CRUD with overlap check; student view | T2-04 | FR-TT-001; overlap rejected |
| T2-12 | Notices: admin CRUD, publish, unpublish; student list and detail with targeting | G1 | FR-ADM-006, FR-NOT-001; drafts never visible to students |
| T2-13 | Student profile page | G1 | FR-STU-002 |
| T2-14 | Student dashboard endpoint and page (recommendations card empty for now) | T2-07, T2-10, T2-11, T2-12 | FR-STU-001 except recommendations |
| T2-15 | Authorization test suite for every endpoint so far, driven by the authorization matrix | T2-03 to T2-14 | Visitor 401, wrong role 403, foreign record 404, for every endpoint |

**Gate G2 (M2 complete):** a student sees only their own data; an admin edit appears for the student and in the audit log; T2-15 passes.

## Phase 3: Resource hub (M3)

| ID | Task | Depends on | Acceptance |
| --- | --- | --- | --- |
| T3-01 👤 | Create a private Supabase Storage bucket; set `SUPABASE_*` variables locally | G2 | An unsigned URL to the bucket is refused |
| T3-02 | Flyway `V3__resources.sql`: learning_resources, question_papers, bookmarks | G2 | Migration test passes |
| T3-03 | `StorageClient` interface, Supabase implementation, in-memory test implementation. Choose and record the PDFBox version. | T3-01 | Upload, signed URL and delete work against the real bucket in a manual check; unit tests use the in-memory client |
| T3-04 | Upload validation (size, type, signature, parse) and admin upload endpoint with UI | T3-02, T3-03 | FR-ADM-007; each invalid case returns its documented code; storage failure leaves no database row |
| T3-05 | Resource search, filter, pagination, detail; question paper search | T3-02 | FR-LIB-001, FR-LIB-002; hidden resources never returned to students |
| T3-06 | Signed download link, resource page with PDF preview and download | T3-03, T3-05 | FR-LIB-003; link lifetime at most five minutes; CSP allows only the storage host in frames |
| T3-07 | Bookmarks | T3-05 | FR-LIB-004; idempotent add and remove |
| T3-08 | Admin resource management: edit details, hide, delete | T3-04 | Delete removes the file and the row |
| T3-09 | Extend the authorization suite to resource endpoints | T3-04 to T3-08 | Suite passes |

**Gate G3 (M3 complete):** an admin uploads a PDF and a student finds, previews and bookmarks it; invalid uploads are rejected.

## Phase 4: Quick Exam (M4)

| ID | Task | Depends on | Acceptance |
| --- | --- | --- | --- |
| T4-01 | Flyway `V4__quiz.sql`: quiz_questions, quiz_attempts, quiz_attempt_answers | G2 | Migration test passes |
| T4-02 | Admin question bank CRUD with draft, published and retired states, with UI | T4-01 | FR-ADM-008 |
| T4-03 | Start an attempt: random selection, shuffled options, server-side expiry | T4-02 | FR-QZ-001; response has no correct answers (tested by inspecting the JSON) |
| T4-04 | `QuizEvaluationService` as plain Java with unit tests | T4-01 | All correct, none, partial, unanswered, unknown question ID |
| T4-05 | Submit an attempt: expiry and grace, single submission, scoring, result | T4-03, T4-04 | FR-QZ-002, FR-QZ-003; documented 409 codes |
| T4-06 | Quiz page: set-up, timer, navigation, auto-submit, result | T4-05 | Full flow works on a phone-width screen; refresh does not reset the timer |
| T4-07 | Attempt history and reopening a result | T4-05 | FR-QZ-004; foreign attempt ID returns 404 |
| T4-08 | `ProgressService` (levels and recommendations) as plain Java; progress page; dashboard card | T4-05, G3 | FR-PRG-001, FR-PRG-002; boundary tests at 5 answers, 60% and 80% |
| T4-09 | Extend the authorization suite to quiz and progress endpoints | T4-02 to T4-08 | Suite passes |

**Gate G4 (R1 complete):** the student success flow works locally without the AI step.

## Phase 5: AI study assistant (M5)

Follow [15-AI-features-handoff.md](15-AI-features-handoff.md).

| ID | Task | Depends on | Acceptance |
| --- | --- | --- | --- |
| T5-01 | `ai` package skeleton, `AiProvider`, `MockAiProvider`, `AiProperties`, `V5__ai.sql` | G4 | App starts with `AI_PROVIDER=mock` and with `AI_ENABLED=false` |
| T5-02 | `AiUsageService`: per-user daily, per-minute and concurrency limits, the application-wide ceiling (`AI_DAILY_LIMIT_GLOBAL`), kill switch, usage endpoint | T5-01 | Each limit blocks before any provider call, in the documented order; failures do not consume quota; the ceiling blocks every user |
| T5-03 | Explain: template, prompt builder, parser, service, endpoint | T5-02 | Spec §3 criteria against the mock |
| T5-04 | Summarize | T5-03 | Spec §4 criteria against the mock |
| T5-05 | Generate practice questions with `GeneratedQuestionValidator` | T5-03 | Spec §5 criteria against the mock |
| T5-06 👤 | Real provider adapter. Needs OQ-1 decided, provider terms reviewed, the key set by the owner. Required for acceptance of FR-AI-001 to FR-AI-003 unless OQ-9 records an approved exception | T5-03 | One manual request per feature succeeds; ADR 0005 updated |
| T5-07 | Assistant page with three tabs; "Ask AI to explain" from quiz results | T5-03 to T5-05 | All states in the UI spec §14 |
| T5-08 | Automated adversarial tests and architecture test; 👤 manual behaviour run B1 to B20 against the real provider, judged on content | T5-06, T5-07 | [14](14-AI-security-and-privacy.md) §13 checklist complete |

**Gate G5 (R2 complete):** the full student success flow works locally with the real AI provider, and the AI acceptance tests pass.

## Phase 6: Test and deploy (M6)

| ID | Task | Depends on | Acceptance |
| --- | --- | --- | --- |
| T6-01 | Production profile, `Procfile`, `system.properties`, forwarded-header and HTTPS settings | G4 | JAR starts locally with the `prod` profile against a test database |
| T6-02 | Full regression: all automated suites, accessibility and responsive checks | G5 | Results recorded per [17-testing-strategy.md](17-testing-strategy.md) §14 |
| T6-03 | Security release checklist | T6-02 | Every box in [11-security-plan.md](11-security-plan.md) §16 ticked or explained |
| T6-04 👤 | Create the Heroku app, choose the plan, set config vars, including the confirmed `STUDENT_EMAIL_DOMAIN` (OQ-15) | T6-01 | `heroku config` shows every required name |
| T6-05 👤 | First deployment and smoke test | T6-03, T6-04 | Health is UP; both success flows run on the live URL |
| T6-06 👤 | Load synthetic demo data by setting `DEMO_SEED_ENABLED=true` for one start, then removing it; change the bootstrap admin password; confirm the deployment holds synthetic data only (OQ-17) | T6-05 | Demo accounts work; the admin's bootstrap password is gone; the flag is removed; one student is left awaiting first login for the demonstration and no others |
| T6-07 | Complete the runbook and the demo and viva guide with real values | T6-05 | Owner rehearses the demo from the guide |

**Gate G6:** submission ready.

## After submission: P1 backlog

| ID | Task | Requirement |
| --- | --- | --- |
| T7-01 ✅ | Attendance calculator, on the total attendance (targets 70 to 85%). Built on 2026-10-22 | FR-ATT-003 |
| T7-02 | `V6__planner.sql`, study tasks, exam countdown | FR-PLN-001, FR-PLN-002 |
| T7-03 ✅ | Audit log viewer. Built on 2026-10-17 | FR-ADM-009 |
| T7-04 | PDF summarization, including the indirect-injection review required by the AI security document §3.3 | FR-AI-005 |
| T7-05 | Flowchart generation (vendor Mermaid 👤) | FR-AI-006 |
| T7-06 | Report a resource | FR-LIB-005 |
| T7-07 ✅ | CSV student import, through `StudentProvisioningService` so the same rules apply. Built on 2026-10-14 | FR-ADM-010 |
| T7-08 | Database-backed sessions 👤 new dependency | |
| T7-09 | Orphaned-file cleanup and AI log retention jobs | |
| T7-12 ✅ | Notes workspace, Quick Note, Quick Assistant and floating robot, ported from Opportunity Radar ([ADR 0014](decisions/0014-notes-quick-assistant-and-robot.md)), V14 and V15. Built on 2026-10-23 | — |
| T7-11 ✅ | Student complaints (public, private, secure), V13. Built on 2026-10-23 | FR-CMP-001 to FR-CMP-004 |
| T7-10 ✅ | Past papers (PYQ) library: `V10__pyq.sql`, student search, filter, save and signed viewing, admin upload and folder import. Built on 2026-10-18 | FR-LIB-006 |

## Tasks that always need the owner

- Any task marked 👤.
- Creating accounts or projects on Supabase, Heroku, GitHub or an AI provider.
- Entering or rotating any secret.
- Approving a dependency, a destructive migration, a deployment, or spend.
- Answering an open decision.
- Judging the manual AI behaviour run.
- Approving any scope change to a P0 requirement (roadmap §9).

## Requirement traceability

Every P0 requirement in the [PRD](02-PRD.md) maps to the tasks that build it and to one acceptance test defined in [17-testing-strategy.md](17-testing-strategy.md) §16. A P0 requirement is finished only when its acceptance test passes at the gate shown.

If a row is missing here, the requirement is not planned. Add the row before starting work.

| Requirement | Milestone | Tasks | Accepted at | Acceptance test |
| --- | --- | --- | --- | --- |
| FR-AUTH-001 | M1 | T1-06, T1-09 | G1 | AT-AUTH-001 |
| FR-AUTH-002 | M1 | T1-06, T1-09 | G1 | AT-AUTH-002 |
| FR-AUTH-003 | M1 | T1-08, T1-09 | G1 | AT-AUTH-003 |
| FR-AUTH-004 | M1 to M5 | T1-06, T2-15, T3-09, T4-09, T5-08 | G1, and again at every later gate | AT-AUTH-004 |
| FR-AUTH-005 | M1 | T1-07; the reset that unlocks an account comes with T2-03 | G1; criterion 4 at G2 | AT-AUTH-005 |
| FR-AUTH-006 | M1 | T1-03, T1-06, T1-10 | G1 | AT-AUTH-006 |
| FR-STU-001 | M2 | T2-14; the recommendations card is filled by T4-08 | G2; criterion 6 at G4 | AT-STU-001 |
| FR-STU-002 | M2 | T2-13 | G2 | AT-STU-002 |
| FR-ATT-001 | M2 | T2-05, T2-07 | G2 | AT-ATT-001 |
| FR-ATT-002 | M2 | T2-05, T2-07 | G2 | AT-ATT-002 |
| FR-MRK-001 | M2 | T2-09 | G2 | AT-MRK-001 |
| FR-MRK-002 | M2 | T2-10 | G2 | AT-MRK-002 |
| FR-TT-001 | M2 | T2-11 | G2 | AT-TT-001 |
| FR-NOT-001 | M2 | T2-12 | G2 | AT-NOT-001 |
| FR-LIB-001 | M3 | T3-05 | G3 | AT-LIB-001 |
| FR-LIB-002 | M3 | T3-05 | G3 | AT-LIB-002 |
| FR-LIB-003 | M3 | T3-03, T3-06 | G3 | AT-LIB-003 |
| FR-LIB-004 | M3 | T3-07 | G3 | AT-LIB-004 |
| FR-QZ-001 | M4 | T4-03, T4-06 | G4 | AT-QZ-001 |
| FR-QZ-002 | M4 | T4-05, T4-06 | G4 | AT-QZ-002 |
| FR-QZ-003 | M4 | T4-04, T4-05, T4-06; "Ask AI to explain" comes with T5-07 | G4; criterion 3 in full at G5 | AT-QZ-003 |
| FR-QZ-004 | M4 | T4-07 | G4 | AT-QZ-004 |
| FR-PRG-001 | M4 | T4-08 | G4 | AT-PRG-001 |
| FR-PRG-002 | M4 | T4-08 | G4 | AT-PRG-002 |
| FR-AI-001 | M5 | T5-01, T5-03, T5-06, T5-07 | G5 | AT-AI-001 |
| FR-AI-002 | M5 | T5-04, T5-06, T5-07 | G5 | AT-AI-002 |
| FR-AI-003 | M5 | T5-05, T5-06, T5-07 | G5 | AT-AI-003 |
| FR-AI-004 | M5 | T5-02, T5-07, T5-08 | G5 | AT-AI-004 |
| FR-ADM-001 | M1 to M2 | T1-10, T2-03 | G2 | AT-ADM-001 |
| FR-ADM-002 | M2 | T2-06 | G2 | AT-ADM-002 |
| FR-ADM-003 | M2 | T2-08 | G2 | AT-ADM-003 |
| FR-ADM-004 | M2 | T2-10 | G2 | AT-ADM-004 |
| FR-ADM-005 | M2 | T2-04, T2-11 | G2 | AT-ADM-005 |
| FR-ADM-006 | M2 | T2-12 | G2 | AT-ADM-006 |
| FR-ADM-007 | M3 | T3-04, T3-08 | G3 | AT-ADM-007 |
| FR-ADM-008 | M4 | T4-02 | G4 | AT-ADM-008 |
| FR-ADM-009 (P0 part) | M2 | T2-01, T2-02, then used by T2-03, T2-06, T2-08, T2-10 | G2 | AT-ADM-009 |
| FR-UX-001 | M1 onward | T1-09, then every task that adds a page | G1, and again at G6 in T6-02 | AT-UX-001 |

P1 requirements map to the backlog tasks `T7-01` to `T7-07` in the table above. They have no acceptance test yet; one is added to the testing strategy §16 when the task is scheduled.
