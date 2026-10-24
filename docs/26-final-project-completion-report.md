# 26. Final project completion report

> Written 2026-10-24 at the end of the autonomous completion run ([25](25-autonomous-completion-plan.md)). Repository: `main`, local commits only (nothing pushed, nothing deployed). Baseline of the run: `1651445`.

Status words used throughout, never one for another:

| Word | Meaning |
| --- | --- |
| **Code complete** | The code exists and does what the requirement says. |
| **Locally tested** | Unit tests pass. |
| **Integration tested** | Tests pass over real HTTP against PostgreSQL 17 in Docker (Testcontainers), with the project's real roles and RLS. |
| **Browser verified** | Exercised in a browser against a locally running build with a throw-away database. |
| **Staging verified** | Exercised on a hosted test environment. **No staging environment exists.** |
| **Production verified** | Exercised on the real host with the real database, bucket and providers. **Nothing is deployed.** |

## 1. Executive summary

Every approved P0 and P1 requirement (59 requirement IDs) is code complete, locally tested and integration tested. 47 of them are also browser verified end to end. The other 12 depend on an external service that has not been signed off: the real Storage bucket (8) or an approved AI provider (4). They are built and tested against stand-ins. Nothing is staging or production verified, because nothing is deployed.

This run built the last two missing P1 features: **report a resource** (FR-LIB-005) and **flowchart generation** (FR-AI-006). It also built the **AI usage-log retention** job (T7-09, off by default), dropped the PDF summarizer (FR-AI-005) from scope, and fixed three defects found by tests and the browser pass.

**Campus Companion is not yet ready for real students.** The remaining blockers are owner actions, listed once in §18. Most of the repository work is done; §17 lists what remains.

## 2. Final project architecture

Unchanged in kind: one Spring Boot 4 application (Java 21) serving a REST API and plain HTML/CSS/JS pages, PostgreSQL on Supabase with Flyway, Supabase Storage through the server only, AI providers called from Java through the router, and React only on the landing page ([ADR 0015](decisions/0015-react-landing-page.md)). See [05-architecture.md](05-architecture.md).

One package was added: `retention`, for nightly clean-up jobs. It exists because the AI code may not use the audit log (`AiArchitectureTest`). The job reaches the AI usage log through `AiUsageService`, not through another feature's repository.

No dependency, framework, database or library was added in this run. In particular the flowchart is drawn without Mermaid (§9).

## 3. PRD requirement completion matrix

The detailed matrix with evidence per row is [25](25-autonomous-completion-plan.md) §1. Summary by requirement ID:

| Group | IDs | Code complete + integration tested | Browser verified | Waiting on an external service |
| --- | --- | --- | --- | --- |
| P0 | 41 | 41 | 33 | STU-003 photo, LIB-001…004 files, ADM-007 upload (bucket); AI-001…003 (provider decision) |
| P1 | 18 | 18 | 14 | NOT-002 document, LIB-006 papers (bucket); AI-006 (provider decision; verified on the mock) |
| **Total** | **59** | **59 (100 %)** | **47 (80 %)** | 12 |
| Dropped | FR-AI-005 | Not counted | | |
| Deferred (P2) | FR-LIB-006 analyzer, FR-AI-007, FR-ADM-011 | Not counted | | |

**Weighted completion** (the audit's method, [PROJECT_AUDIT_REPORT.md](../PROJECT_AUDIT_REPORT.md) Part B §3: P0 × 3, P1 × 1; verified 100 %, built but not fully verified 75 %): P0 = 1625 / 17 = **95.6 %**, P1 = 975 / 11 = **88.6 %**, **overall ≈ 94 %** (88 % before this run). The dropped PDF summarizer is excluded. Items that depend on the bucket or a provider decision stay at 75 %. The workings are in the audit report, Part A5.

## 4. Features completed in this run

| Feature | What a user sees | Commit |
| --- | --- | --- |
| FR-LIB-005 Report a resource | Students: "Report a problem" on a resource page (5 to 500 characters, one open report per resource). Admins: "Reported resources" on the Resources page, with resolve (audited). | `93b24f3`, fixes `8a6e44f`, `758a693` |
| FR-AI-006 Flowchart | A fourth tab, "Flowchart", on the AI assistant: a topic or a described process becomes a checked chart drawn as boxes and arrows, numbered steps, and Mermaid text to copy. | `9e965be` |
| T7-09 AI usage-log retention | Nothing visible. When the owner sets `AI_LOG_RETENTION_ENABLED=true`, rows older than 90 days are removed nightly in batches, and each run is audited. | `81f6416`, `f55c24c` |

## 5. Previously existing features verified in this run

Browser pass on 2026-10-24, on a JAR built from the commits of this run, against a throw-away PostgreSQL with demo data. The browser was the built-in one (Chromium).

- **Student, 17 pages:** dashboard, attendance, results, timetable, planner, notices, resources, a resource page, past papers, bookmarks, notes, complaints, Quick Exam, test history, progress, settings and profile. All of them loaded with no new console error and no warning or error in the server log.
- **Attendance planner (FR-ATT-003):** checked by hand. 67 of 80 classes gives 7 classes to reach 85 %, and 3 classes that can be missed at 80 %. The what-if of 18 of the next 20 gives 85.00 %.
- **Admin, 14 pages:** dashboard, students, subjects, attendance, marks, results, timetable, notices, resources, question bank, past papers, complaints, audit log and settings. Each showed its data or a clear empty state. At 375 px width, five pages were checked (students, resources, audit, timetable, marks): no horizontal scroll, and tables turn into cards.
- **First login:** the bootstrap admin is forced to change its password before reaching any page.
- **Planner:** loads and lists tasks and exams (regression).

## 6. Features intentionally dropped or deferred

| ID | Decision | Reason |
| --- | --- | --- |
| FR-AI-005 / T7-04 PDF summarizer | **Dropped** by the owner on 2026-10-24 | Out of scope; students can still summarize text they paste (FR-AI-002). Not counted anywhere. |
| FR-LIB-006 (P2) question paper analyzer, FR-AI-007 flashcards, FR-ADM-011 usage statistics | Deferred (P2) | The PRD marks them for a later release. |
| T7-09 orphaned-file clean-up | Deferred | It deletes from the real bucket. It cannot be verified without that bucket and needs the owner's approval. |
| Mermaid in the browser | Not vendored | It would be a large new third-party script needing approval. The checked graph is drawn with plain HTML instead (§9). |

## 7. Database migrations

| Migration | Change | Status |
| --- | --- | --- |
| `V16__planner.sql` (earlier run) | `study_tasks`, `student_exams` | Tested in isolation; **not applied to hosted** |
| `V17__resource_reports.sql` | `resource_reports` with RLS, a one-open-report-per-student unique index | Tested in isolation; **not applied to hosted** |
| `V18__ai_log_retention.sql` | Lets `cc_app` delete from `ai_request_logs` (one policy) | Tested in isolation; **not applied to hosted** |

The hosted database is at V15. The backup, validation, expected changes, rollback and post-checks are in [24](24-production-readiness.md) §6. The migrations run only after owner approval.

## 8. API changes

All additive; no field was renamed or removed.

- `POST /api/v1/resources/{id}/reports` (student) — [09](09-api-contract.md) §8.23.
- `GET /api/v1/admin/resource-reports`, `POST /api/v1/admin/resource-reports/{id}/resolve` (admin) — §8.23.
- `POST /api/v1/ai/flowchart` (student) — §9.3a.

Each is in [openapi.yaml](openapi.yaml) and covered by the generated access test (`EndpointAccessIT`: 401 for visitors, 403 for the wrong role).

## 9. Frontend changes

- Resource page: a "Report a problem" button and form. The header's button column is now limited to half the row, so the title stays readable beside four buttons (`758a693`).
- Admin Resources: a "Reported resources" panel.
- Admin audit log: entries of the new kinds can be filtered ("Resource report", "AI usage log", "Complaint"), and "AI" is written in capitals.
- AI assistant: the Flowchart tab. The chart is built from the checked graph with `textContent` only. No model text is parsed as markup or as diagram syntax in the browser.
- Your own uncommitted edits to `student/assistant.html` were left untouched. Only the one line of the new tab was committed, onto the committed version of the file.

## 10. Security improvements

- The flowchart graph from the model is checked in Java before anything is shown:
  - size limits: 2 to 20 nodes, 1 to 40 edges, labels up to 80 characters;
  - known IDs only, exactly one start and at least one end;
  - nothing dangling, and every node reachable from the start.
  The Mermaid text is built by the server with its own IDs and escaped labels.
- The AI package again cannot reach the audit log. The retention job was moved out of it after `AiArchitectureTest` caught the breach (`f55c24c`).
- Audit entries of resource reports now use the same entity-type convention as the rest, so they can be filtered (`8a6e44f`).
- The repository review of [24](24-production-readiness.md) §2 still holds. No new secret, endpoint without an access rule, or table without RLS was added. `DatabaseSecurityModelIT` pins the new grants.

## 11. AI provider configuration and limitations

- `AI_ROUTES` was **not changed**. The provider terms were reviewed on 2026-10-24 ([24](24-production-readiness.md) §4). The recommendation still waits for the owner: keep Groq (with Zero Data Retention on) and Ollama Cloud, and remove the Gemini free tier and OpenRouter `:free` from student traffic.
- The flowchart uses the same router, limits (5 a minute, 30 a day per student, and the global ceiling), 25-second budget and failure codes as the other tools. It counts as one request. It has been run only against the mock. No live provider call was made in this run, to avoid token use and because the providers are not yet approved.
- The mock stays the default in tests and is never part of the router. Students see "Demo mode" when it answers.

## 12. Storage verification

Unchanged from [24](24-production-readiness.md) §5: built and integration tested against an in-memory stand-in. **The real bucket has not been verified.** It needs the owner's console, and this run did not touch it. The sign-off checklist (private bucket, unsigned URL refused, MIME types and size, no anon policies, Data API off, upload, preview, download and delete, link expiry, photos, CSP) is in that section, with every box still open.

In the browser pass, the app with no bucket configured showed "This file is temporarily unavailable" for a preview, and the rest of the page kept working. That is the intended degraded behaviour.

## 13. Performance measurements

Nothing new was measured on a host. Earlier local figures stand: API calls of 5 to 73 ms, startup of about 4 to 8 s, and about 400 to 460 MB resident with `-Xmx256m`. These are not production evidence. The plan to measure memory on a 512 MB host, with its thresholds (peak ≤ 85 % is acceptable; above 95 % or any OOM restart means a bigger plan), is [24](24-production-readiness.md) §6a.

## 14. Test results

Final full run on the committed state (`git archive` of `758a693`, no `.env`, Testcontainers PostgreSQL 17): **295 unit and 305 integration tests passed, 0 failures, BUILD SUCCESS**; Node module tests 13 passed. Details in §20. New tests in this run:

- `FlowchartRendererTest` (3), `AiFlowchartIT` (4);
- `ResourceReportIT` (3);
- `AiUsageLogRetentionTest` (1), `AiUsageLogRetentionIT` (2);
- updated: `DatabaseSecurityModelIT`, `PageRoutingIT`.

No test was deleted, disabled or weakened.

## 15. Browser test evidence

§5 lists the pages. The new features were checked in the same session:

- **Flowchart:** desktop and 375 px. Five numbered boxes with arrows, the branch labels "No → step 4" and "Yes → step 5", and the Mermaid text section. No horizontal scroll; the four tabs fit.
- **Report a resource:** the form opened and the report was sent ("Report sent. Thank you."). The admin list then showed it with roll number `23CSE001` and status `OPEN`. The resource row was a synthetic one inserted into the throw-away database, because uploads need the real bucket.
- **Console:** the only errors were the login page's expected 401 (checking for a session before sign-in) and the robot scene's known "Missing property" (CC-14).

Defects the browser pass found, both fixed and committed: the cramped resource header, and the audit entity type and label.

## 16. Git commit history for this run

| Commit | Change |
| --- | --- |
| `a4471f6` | docs: FR-AI-005 dropped everywhere it still read as planned |
| `749edc8` | docs: autonomous completion plan with the requirement matrix (doc 25) |
| `93b24f3` | feat(resources): report a resource; admins resolve (FR-LIB-005) |
| `81f6416` | feat(ai): nightly AI usage-log retention, off by default (T7-09) |
| `f55c24c` | fix(retention): move the clean-up out of the ai package |
| `9e965be` | feat(ai): flowchart generation, without a new dependency (FR-AI-006) |
| `8a6e44f` | fix(audit): upper-case entity type for resource reports; readable AI label |
| `758a693` | fix(resources): keep the resource title readable beside four buttons |
| (this commit) | docs: final report, owner checklist, audit and plan updates |

All local. None is pushed.

## 17. Remaining technical issues

| Issue | Severity | Note |
| --- | --- | --- |
| Real bucket never exercised | Blocks 8 requirements from "verified" | Owner sign-off, [24](24-production-readiness.md) §5 |
| HTTPS behind the host's proxy unverified | Could loop redirects on the first deploy | Check in the first minute, [24](24-production-readiness.md) §2 |
| Memory on 512 MB unmeasured (CC-13) | Possible OOM on a small plan | Plan in [24](24-production-readiness.md) §6a |
| Robot scene console error (CC-14) | Cosmetic | A state transition in `robot-scene.splinecode`; repair in the Spline editor. Vendor code is not edited. |
| Sessions and rate limits in memory | One instance only; sign-in again after a restart | Documented design |
| `assistant` chat mode has no six-chat cap | Low; no page uses it | — |
| Orphaned-file clean-up (T7-09 second half) | Low | Deferred, §6 |

No known critical or high-severity security issue is open in the repository.

## 18. Owner-only actions (one consolidated checklist)

Nothing below has been done by the developer, and none of it may be claimed as done until the owner does it.

**Credentials** (names only; how and where: [24](24-production-readiness.md) §3):
- [ ] Rotate `DB_PASSWORD` (role `cc_app`) in the Supabase SQL editor. Required.
- [ ] Rotate `DB_MIGRATION_PASSWORD` (role `cc_migrator`). Required.
- [ ] Delete the disabled Gemini key, fingerprint `36df8d6d`, in Google AI Studio or the Cloud console. Required.
- [ ] Remove `ADMIN_BOOTSTRAP_EMAIL` and `ADMIN_BOOTSTRAP_PASSWORD` from every environment once the admin has changed its password. The app forces that change at first login, as checked in §5.
- [ ] Review or rotate `SUPABASE_SERVICE_KEY`. Recommended.
- [ ] Create separate production AI keys for the approved providers only. Recommended.
- [ ] Delete the local `.env.backup-2026-10-23`.

**AI providers:**
- [ ] Read the terms and decide which providers may receive student text. Then approve an `AI_ROUTES` value: the recommendation is Groq and Ollama only ([24](24-production-readiness.md) §4).
- [ ] If Groq is kept: turn on Zero Data Retention in its Data Controls.
- [ ] If Gemini is kept: move to a paid account and confirm every user is 18 or over.
- [ ] Update the privacy page's list of services to match the decision.

**Storage:**
- [ ] Work through the sign-off checklist of [24](24-production-readiness.md) §5 on the real project, with a developer.

**GitHub** (repository Settings; the repository is public, so these are free; not verified from here):
- [ ] Settings → Advanced Security (or Code security): turn on **Dependabot alerts** and **Dependabot security updates**.
- [ ] Same page: turn on **Secret Protection** (secret scanning) and **Push protection**.
- [ ] Same page: turn on **Private vulnerability reporting**.
- [ ] Settings → Branches (or Rules → Rulesets): protect `main` so it cannot be force-pushed or deleted.
- [ ] Then check the Security tab for any alert already raised.

**Database and deployment:**
- [ ] Back up hosted Supabase, then approve V16 to V18 ([24](24-production-readiness.md) §6). Until then, do not restart the 8080 server or deploy, because either applies them.
- [ ] Choose the host. Recommended: Render Starter. Then change `plan: free` in `render.yaml` to the paid plan; that is a deliberate owner change. Create the service, and set every variable of [18](18-production-plan.md) §5 with `SPRING_PROFILES_ACTIVE=prod` and **`DEMO_SEED_ENABLED` not set**.
- [ ] Approve the first deployment, then run the first-start checks and the memory measurement ([24](24-production-readiness.md) §6a and §8).
- [ ] Optionally set `AI_LOG_RETENTION_ENABLED=true` once the privacy page's 90-day statement should take effect.

**Institution and content:**
- [ ] The college reviews and adopts the privacy policy and terms.
- [ ] Repair the robot scene's state transition in the Spline editor and re-export it (CC-14).

## 19. Deployment readiness

| Area | State |
| --- | --- |
| Build | `Dockerfile` (Java 21, `-Xmx256m` and further limits), `render.yaml`, Heroku `Procfile` and `system.properties` exist; the JAR builds from the repository |
| Configuration | Every variable is documented in `.env.example` and [18](18-production-plan.md) §5 |
| Demo data | Seeds only when `DEMO_SEED_ENABLED=true` or the `dev` profile is active; must stay unset for students |
| Health | `/actuator/health` |
| Migrations | V16 to V18 pending on hosted; procedure ready |
| Host | Not chosen, not purchased, not deployed |

## 20. Explicit final verdict

| Status | Result |
| --- | --- |
| Code complete | **Yes**: all 59 approved P0/P1 requirements |
| Locally tested | **Yes** |
| Integration tested | **Yes**: see the final run below |
| Browser verified | **47 of 59**. The rest need the real bucket or an approved provider. |
| Staging verified | **No**: there is no staging environment |
| Production verified | **No**: not deployed |

**Final verification on the committed state:** `./mvnw -B verify` on a `git archive` of `758a693` (all code of this run; the closing commit changes documents only), with no `.env` and Testcontainers PostgreSQL 17: **295 unit tests and 305 integration tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS** (186 s). Node module tests: 13 passed.

**Is Campus Companion ready for real students? No, not yet.** The repository work that can be done safely without the owner is complete. Real students should use it only after the owner actions of §18 are done: credentials rotated, providers decided, bucket signed off, backup taken and migrations approved, host purchased and deployed, first-start and memory checks passed, and the college's approval.
