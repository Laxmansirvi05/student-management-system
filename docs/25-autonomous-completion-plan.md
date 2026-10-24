# 25. Autonomous completion plan

> Written 2026-10-24 from the repository at `a4471f6` (branch `main`). The audit report's newest Part A sections are the baseline. Older findings in its Part B are history, not current work. Commits and results are filled in as each work package lands.

Status words: **Complete** = built, with automated tests passing and checked through the running application. **Built, unverified** = built and tested, but not exercised end to end, or it depends on an external service not yet signed off. **Partial** = some of the acceptance criteria are missing. **Missing** = not built. **Dropped** = out of scope, not counted.

## 1. Requirement matrix

| ID | Feature | Pri. | Status | Evidence | Remaining work | Next action |
| --- | --- | --- | --- | --- | --- | --- |
| FR-AUTH-001…006 | Login, logout, first password, roles, lockout, email identity | P0 | Complete | `AuthFlowIT`, `LockoutIT`, `PasswordChangeIT`, `LoginRateLimitIT`, `EndpointAccessIT`; browser login | — | Regression only |
| FR-STU-001 | Dashboard | P0 | Complete | `TimetableNoticesDashboardIT`, `PlannerIT`; browser | — | Regression |
| FR-STU-002/003 | Profile, own details and photo | P0 | Built, unverified | `OwnProfileIT`, `ProfilePhotoProcessorTest` (in-memory storage) | Photo with the real bucket | Owner: Storage sign-off |
| FR-STU-004 | Settings | P0 | Complete | Browser; CC-20 fix | — | — |
| FR-LEG-001 | Privacy and terms | P0 | Complete (text) | `PageRoutingIT` | College adoption | Owner |
| FR-ATT-001/002 | Attendance, low flag | P0 | Complete | `AttendanceApiIT`, `AttendanceCalculatorTest`; browser | — | — |
| FR-ATT-003 | Attendance calculator | P1 | Built, unverified in browser | `AttendanceReportIT` | Browser check | WP5 |
| FR-MRK-001/002 | Marks, results, CGPA | P0 | Complete | `MarksAndResultsApiIT`, `CgpaCalculatorTest`; browser | — | — |
| FR-TT-001 | Timetable | P0 | Complete | IT; browser | — | — |
| FR-NOT-001 | Notices | P0 | Complete | IT; browser | — | — |
| FR-NOT-002 | Notice document | P1 | Built, unverified | `NoticeDocumentIT` (in-memory storage) | Real bucket | Owner: Storage |
| FR-LIB-001…004 | Resources, search, preview, bookmarks | P0 | Built, unverified | `ResourceApiIT` (in-memory storage) | Preview and download with the real bucket | Owner: Storage |
| **FR-LIB-005** | **Report a resource** | P1 | **Missing** | Table designed (doc 08 §4.20) | Build it | **WP2** |
| FR-LIB-006 (P1) | Past papers library | P1 | Built, unverified | `PyqIT` | Real bucket | Owner: Storage |
| FR-LIB-006 (P2) | Question paper analyzer | P2 | Deferred | — | Later release | — |
| FR-CMP-001…004 | Complaints | P1 | Complete | `ComplaintIT`; browser | — | — |
| FR-NTE/AST/ROB | Notes, Quick Assistant, robot | P1 | Complete | `NotesIT`, `AssistantIT`, `AiRouterWorkflowIT`; browser | Robot scene console error (Spline editor) | Owner |
| FR-QZ-001…004 | Quick Exam | P0 | Complete | `QuizApiIT`; browser | — | — |
| FR-PRG-001/002 | Progress, recommendations | P0 | Complete | Tests; browser | — | — |
| FR-PLN-001/002 | Study tasks, exam countdown | P1 | Complete | `PlannerIT`, `PlannerRulesTest`; browser (`c5c1c03`) | V16 on hosted DB | Owner: backup + approval |
| FR-AI-001…003 | Explain, summarize text, practice questions | P0 | Built, live-tested locally | `AiApiIT`, `AiRouterWorkflowIT`; 30 live requests | Provider terms decision; behaviour run B1–B20 | Owner |
| FR-AI-004 | Usage limits and failures | P0 | Complete | `AiUsageServiceTest`, `AiApiIT`, router tests | — | — |
| FR-AI-005 | Summarize a library PDF | — | **Dropped** | PRD, plan, `a4471f6` | None | — |
| **FR-AI-006** | **Flowchart generation** | P1 | **Missing** | — | Build it, bounded, no new dependency | **WP4** |
| FR-AI-007 | Flashcards | P2 | Deferred | — | Later release | — |
| FR-ADM-001…008 | Admin: accounts, attendance, marks, results, subjects, timetable, notices, resources, question bank | P0 | Built, browser-unverified | Many ITs | Browser pass over admin pages | **WP5** |
| FR-ADM-009 | Audit log (write P0, viewer P1) | P0/P1 | Complete / built | `AuditRollbackIT`, `AdminAuditIT` | Browser check of the viewer | WP5 |
| FR-ADM-010 | CSV import | P1 | Built | `StudentImportIT` | Browser check | WP5 |
| FR-ADM-011 | Usage statistics | P2 | Deferred | — | Later release | — |
| FR-UX-001 | Error, empty, loading states | P0 | Built, partly verified | Smoke test; browser on student pages | Admin pages | WP5 |
| FR-UX-002 | Landing page | P1 | Complete | `PageRoutingIT`, `StaticFrontendRulesTest`; browser | — | — |

**Backlog task without a PRD row:** **T7-09** covers AI usage-log retention and orphaned-file cleanup. The privacy page and docs 08/14 say the 90-day clean-up "is not built". The log retention is **WP3**: off by default, so nothing is deleted from hosted data until the owner turns it on. Orphaned-file cleanup deletes from the real bucket. It is deferred: it cannot be verified without that bucket, and it needs owner approval.

## 2. Technical debt and open issues (from the audit's Part A)

- CC-13: memory on a 512 MB host is unmeasured (deployment).
- CC-14: the robot scene's console error comes from a state transition in `robot-scene.splinecode`. Fixing it means repairing the scene in the Spline editor (owner). Vendor code is not edited.
- In-memory sessions and rate limits: these restrict deployment to one instance (documented).
- The `assistant` chat mode stores chats without the six-chat cap; no page uses that mode (low).

## 3. Security blockers

None in the repository: [24-production-readiness.md](24-production-readiness.md) §2 found no secret in Git and no code defect. The open blockers are owner actions: credential rotation, the provider data-terms decision, the Storage sign-off and the GitHub security settings.

## 4. Owner-only tasks and deployment blockers

See [26-final-project-completion-report.md](26-final-project-completion-report.md) §18 (consolidated checklist) and doc 24 §1.

## 5. Execution order

| WP | Work | Commit | Result |
| --- | --- | --- | --- |
| 1 | FR-AI-005 dropped everywhere in the docs | `a4471f6` | Done |
| 2 | FR-LIB-005 Report a resource | | |
| 3 | T7-09 AI usage-log retention (off by default) | | |
| 4 | FR-AI-006 Flowchart, bounded, plain HTML rendering | | |
| 5 | Browser pass: admin pages, attendance calculator, states, mobile | | |
| 6 | Owner checklist, GitHub security instructions, final report (doc 26), audit update | | |
| 7 | Final full verification on the committed state | | |
