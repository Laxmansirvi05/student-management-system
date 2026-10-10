# 16. Implementation Plan

> Status: Draft v0.1 · Owner: Laxman Sirvi · Last updated: 2026-10-10

This plan breaks the work into ordered, verifiable tasks. One task is one small change, verified before the next begins. Milestones are defined in [03-scope-and-roadmap.md](03-scope-and-roadmap.md).

## How to use this plan

- Task IDs start with `T` followed by the phase number (`T2-05` is the fifth task of Phase 2). They are unrelated to the priority labels P0, P1 and P2 used in the PRD.
- Work top to bottom. Do not start a task until everything in its "Depends on" column is done.
- 👤 marks a task or gate that needs the owner (a decision, an approval, or an action only they can take).
- Each task ends with the report format in [AGENTS.md](../AGENTS.md#13-how-to-report).
- Track status in GitHub Issues, one issue per task, titled with the task ID. This file defines the tasks; the issue tracker records progress.
- Every task implicitly requires: tests written and passing, `./mvnw verify` green, documents updated, security rules respected.

## Phase 0: Decisions

| ID | Task | Depends on | Done when |
| --- | --- | --- | --- |
| T0-01 👤 | Review and approve documents 01 to 11 and the root rule files | | README status table shows them Approved or lists revisions |
| T0-02 👤 | Answer open decisions OQ-2, OQ-3, OQ-5, OQ-8 | | Charter updated; `TBD` markers removed where answered |
| T0-03 👤 | Create the Supabase project; note the session pooler connection details in a local `.env` | | Owner can connect with a SQL client |
| T0-04 👤 | Enable GitHub secret scanning, push protection, Dependabot alerts and private vulnerability reporting | | Settings enabled |

**Gate G0:** T0-01 complete. T0-02 to T0-04 may run alongside Phase 1 but must finish before the task that needs them.

## Phase 1: Foundation (M1)

| ID | Task | Main files | Depends on | Acceptance |
| --- | --- | --- | --- | --- |
| T1-01 | Create the Maven Spring Boot project on Java 21 with the wrapper, Spotless and the approved starters. Verify and record versions in the TRD. | `pom.xml`, `mvnw`, `CampusCompanionApplication` | G0 | `./mvnw verify` passes on an empty application; TRD version table has no TBD for core items |
| T1-02 | Configuration: profiles, `@ConfigurationProperties`, datasource through the session pooler, Hikari limits, Actuator health only | `application*.yml`, `common.config` | T1-01, T0-03 | App starts locally against Supabase; `/actuator/health` is UP; no other actuator endpoint is reachable |
| T1-03 | Flyway `V1__core.sql`: users, student_profiles, subjects, subject_units, with RLS enabled | `db/migration` | T1-02 | Migration applies cleanly on an empty database; Testcontainers migration test passes |
| T1-04 | Entities and repositories for V1 | `auth`, `student`, `subject` | T1-03 | Repository tests pass against PostgreSQL; `ddl-auto=validate` passes |
| T1-05 | Error handling: error codes, exceptions, global handler producing problem details, request ID filter | `common.error`, `common.web` | T1-01 | Tests show each documented status and code; no stack trace in any response |
| T1-06 | Security: session login, BCrypt, CSRF cookie-to-header, security headers, URL rules, `CurrentUser` | `common.security`, `auth` | T1-04, T1-05 | Login, logout, `/auth/me`, `/auth/csrf` work; visitor gets 401; CSRF missing gets 403 |
| T1-07 | Lockout and login rate limit | `auth`, `RateLimitFilter` | T1-06 | Fifth failure locks for 15 minutes; test with a controllable clock |
| T1-08 | Password change, must-change-password restriction, admin bootstrap at startup | `auth` | T1-06 | Bootstrap creates one admin once; restricted account can reach only change-password and logout |
| T1-09 | Frontend shell: tokens and base CSS, `api.js`, login page, change-password page, student and admin layouts with navigation, standard states | `static/` | T1-06, T1-08 | Both roles log in and land on their own dashboard shell; expired session redirects to login |
| T1-10 | `DevDataSeeder` for synthetic users and subjects (dev profile only) | `common.config` | T1-04 | Seeder runs only in `dev`; all data is clearly fictional |
| T1-11 | CI: GitHub Actions workflow running `./mvnw verify` on pull requests 👤 approve the workflow | `.github/workflows` | T1-01 | A pull request shows a passing check |

**Gate G1 (M1 complete):** a student and an admin can log in, are separated by role, and are forced through password change. Authentication tests pass.

## Phase 2: Academic management (M2)

| ID | Task | Depends on | Acceptance |
| --- | --- | --- | --- |
| T2-01 | Flyway `V2__academic.sql`: attendance_records, assessments, marks, semester_results, timetable_entries, notices, audit_logs | G1 | Migration test passes |
| T2-02 | `AuditService` and its use from a sample write | T2-01 | Audit row is written in the same transaction and rolls back with it |
| T2-03 | Admin: student accounts (create, search, edit, deactivate, activate, reset password) with UI | T2-02 | FR-ADM-001 criteria; temporary password shown once; actions audited |
| T2-04 | Admin: subjects and units with UI | G1 | FR-ADM-005 (subjects) |
| T2-05 | `AttendanceService` (percentage, below-required flag) as plain Java with unit tests | T2-01 | Unit tests cover zero classes, rounding, threshold boundary |
| T2-06 | Admin attendance sheet (read and save) with UI | T2-05, T2-03 | FR-ADM-002; idempotent save; future date rejected; corrections audited |
| T2-07 | Student attendance summary and history with UI | T2-05 | FR-ATT-001, FR-ATT-002 |
| T2-08 | Admin assessments and marks sheet with UI | T2-02, T2-04 | FR-ADM-003; all-or-nothing save; changes audited with old and new values |
| T2-09 | Student marks view with UI | T2-08 | FR-MRK-001; unpublished assessments never returned |
| T2-10 | `ResultService` (CGPA) as plain Java; admin semester results; student results view 👤 needs OQ-3 | T2-02 | FR-ADM-004, FR-MRK-002; unit tests for the CGPA rule |
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
| T5-02 | `AiUsageService`: daily and per-minute limits, application ceiling, kill switch, usage endpoint | T5-01 | Each limit blocks before any provider call; failures do not consume quota |
| T5-03 | Explain: template, prompt builder, parser, service, endpoint | T5-02 | Spec §3 criteria against the mock |
| T5-04 | Summarize | T5-03 | Spec §4 criteria against the mock |
| T5-05 | Generate practice questions with `GeneratedQuestionValidator` | T5-03 | Spec §5 criteria against the mock |
| T5-06 👤 | Real provider adapter. Needs OQ-1 decided, provider terms reviewed, the key set by the owner. | T5-03 | One manual request per feature succeeds; ADR 0005 updated |
| T5-07 | Assistant page with three tabs; "Ask AI to explain" from quiz results | T5-03 to T5-05 | All states in the UI spec §14 |
| T5-08 | Automated adversarial tests and architecture test; 👤 manual behaviour run B1 to B20 | T5-06, T5-07 | [14](14-AI-security-and-privacy.md) §13 checklist complete |

**Gate G5 (R2 complete):** the full student success flow works locally.

## Phase 6: Test and deploy (M6)

| ID | Task | Depends on | Acceptance |
| --- | --- | --- | --- |
| T6-01 | Production profile, `Procfile`, `system.properties`, forwarded-header and HTTPS settings | G4 | JAR starts locally with the `prod` profile against a test database |
| T6-02 | Full regression: all automated suites, accessibility and responsive checks | G5 | Results recorded per [17-testing-strategy.md](17-testing-strategy.md) §14 |
| T6-03 | Security release checklist | T6-02 | Every box in [11-security-plan.md](11-security-plan.md) §16 ticked or explained |
| T6-04 👤 | Create the Heroku app, choose the plan, set config vars | T6-01 | `heroku config` shows every required name |
| T6-05 👤 | First deployment and smoke test | T6-03, T6-04 | Health is UP; both success flows run on the live URL |
| T6-06 👤 | Load synthetic demo data; change the bootstrap admin password | T6-05 | Demo accounts work; no default password remains |
| T6-07 | Complete the runbook and the demo and viva guide with real values | T6-05 | Owner rehearses the demo from the guide |

**Gate G6:** submission ready.

## After submission: P1 backlog

| ID | Task | Requirement |
| --- | --- | --- |
| T7-01 | Attendance calculator | FR-ATT-003 |
| T7-02 | `V6__planner.sql`, study tasks, exam countdown | FR-PLN-001, FR-PLN-002 |
| T7-03 | Audit log viewer | FR-ADM-009 |
| T7-04 | PDF summarization | FR-AI-005 |
| T7-05 | Flowchart generation (vendor Mermaid 👤) | FR-AI-006 |
| T7-06 | Report a resource | FR-LIB-005 |
| T7-07 | CSV student import | FR-ADM-010 |
| T7-08 | Database-backed sessions 👤 new dependency | |
| T7-09 | Orphaned-file cleanup and AI log retention jobs | |

## Tasks that always need the owner

- Any task marked 👤.
- Creating accounts or projects on Supabase, Heroku, GitHub or an AI provider.
- Entering or rotating any secret.
- Approving a dependency, a destructive migration, a deployment, or spend.
- Answering an open decision.
- Judging the manual AI behaviour run.
