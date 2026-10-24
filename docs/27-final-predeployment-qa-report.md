# 27. Final pre-deployment QA report

> 2026-10-24. Branch `main`. Repository state before this pass: `31cc2ed` (42 commits ahead of `origin/main`, 23 uncommitted files). Nothing was deployed. Hosted Supabase was only read, never written, during this pass.

## 1. Executive summary

**Verdict: CONDITIONAL PASS.** The engineering checks passed, but the external owner actions in section 16 remain before real students use the application.

- **Tests:** every automated test passes on the committed state: 296 unit, 307 integration and 13 Node tests.
- **Browser:** every student and admin page loads and renders at five screen sizes, with no overflow, broken images or unnamed controls.
- **Workflows:** the main student workflows were run through the user interface in Playwright, and they work.
- **Uncommitted work:** the 23 files of the owner's frontend work were reviewed one by one, corrected where needed, and committed. Nothing was discarded.
- **No Critical or High issue** is open. The remaining Low items are listed in section 15.

## 2. Repository inspection

| Area | Tracked files |
| --- | --- |
| Whole repository | 771 |
| Java sources (`src/main/java`) | 404 |
| Tests (`src/test`) | 96 |
| Static frontend (`src/main/resources/static`) | 138 |
| Documentation (`docs`) | 48 |

What this pass read and checked:

- **Documents:** AGENTS.md, README, the PRD, TRD, architecture, API contract and OpenAPI, the database design, the security review, the audit report, and docs 24 to 26.
- **Backend:**
  - no `System.out` and no `printStackTrace`;
  - no SQL built from strings, and no swallowed exceptions;
  - no TODO/FIXME in Java;
  - no `innerHTML` assignment in the app scripts (the notes editor uses its sanitiser).
- **Earlier work:** the planner (`c5c1c03`) and the production-readiness docs (`1651445`) are present, and their tests run in the suite.

## 3. Uncommitted change audit

All 23 files had the same author and purpose: the owner's frontend work on the sidebar, navigation and page shell. None was generated, temporary, duplicate or sensitive. Classification: **verified and necessary**, with five corrections. Committed as `8afc38d`.

| Files | Change | Review result |
| --- | --- | --- |
| 18 `student/*.html` | Sidebar "Home" heading; labels wrapped in `nav__label` | Correct; consistent on every student page, including `planner.html` |
| `admin/complaints.html` | Sidebar gains Past papers | Correct. **Fixed:** its phone menu lacked the link and used a different order from the other 13 admin pages |
| `student/notes.html` | Visible page header removed so the workspace is taller | **Fixed:** the page had no `<h1>`; a screen-reader-only one was added |
| `student/profile.html` | User card marked active on Profile | Correct |
| `js/shell.js` | Active item set by script for every page, sub-page and back/forward; name and photo link cached in `sessionStorage` | Correct. **Fixed:** a session that timed out left the cached name for the next login in that tab, so the login page now clears it (`e6c5e77`). **Fixed:** interrupted view-transition promises are now handled |
| `css/app.css`, `base.css`, `notes.css` | Sidebar sizing, focus outlines, transition tuning | Correct. **Cleanup:** seven unused rules removed (section 11). Hard-coded light colours are acceptable because the app has no dark theme |

Also committed:

- `85cac4f`: `.playwright-mcp/` (browser-test evidence) added to `.gitignore`.

Noticed outside the repository:

- The local `.env.backup-2026-10-23` no longer exists. This looks like the owner's checklist item, done.
- `.env` is present, ignored, and was never staged.

## 4. Feature verification matrix

"UI" means the workflow was driven through the page in Playwright. "IT" means an integration test over HTTP against PostgreSQL. "Bucket" means the feature depends on the real storage bucket, which is not configured in tests.

| Requirement | Evidence | Status |
| --- | --- | --- |
| FR-AUTH-001…006 login, logout, roles, lockout, email identity | UI: wrong password message, valid login, reload keeps the session, logout clears the session and cache, a visitor is redirected, a student on an admin page is sent back (API 403); IT: `AuthFlowIT`, `LockoutIT`, `PasswordChangeIT`, `EndpointAccessIT` | Verified |
| FR-STU-001 dashboard (as changed 2026-10-24) | UI: CGPA on top with every semester, deadlines over 30 days, upcoming exams, next exam | Verified |
| FR-STU-002/003 profile and photo | UI: page renders; IT: `OwnProfileIT` | Photo upload needs the bucket |
| FR-STU-004 settings, FR-LEG-001 privacy and terms | UI: pages load, links resolve | Verified |
| FR-ATT-001…003 attendance and calculator | UI: page and planner figures (earlier pass, checked by hand); IT | Verified |
| FR-MRK-001/002 marks, results, CGPA | UI: Results page; IT: `MarksAndResultsApiIT`, `CgpaCalculatorTest` | Verified |
| FR-TT-001, FR-NOT-001/002 timetable, notices, exam papers | UI: pages, exam papers on the notice and dashboard; IT: `PlannerIT`, `TimetableNoticesDashboardIT` | Verified; the notice document needs the bucket |
| FR-LIB-001…005 resources, search, bookmarks, report | UI: search finds the resource, bookmark then "Saved", listed then removed, report sent and seen by admin (earlier pass); IT | Verified; preview and download need the bucket (503 without it, page degrades cleanly) |
| FR-LIB-006 past papers | UI: page loads; IT: `PyqIT` | File viewing needs the bucket |
| FR-CMP-001…004 complaints | UI: complaint submitted, listed under My complaints, seen by admin; IT: `ComplaintIT` | Verified |
| FR-NTE/AST/ROB notes, Quick Assistant, robot | UI: note created, autosaved ("Saved"), kept after reload; another student gets 404 for it; IT: `NotesIT`, `AssistantIT` | Verified; attachments need the bucket; robot console error, see 15 |
| FR-QZ-001…004 Quick Exam (by subject) | UI: 20-question Java test started, answered and submitted, result shown; IT: `CourseQuestionBankIT`, `QuizApiIT` | Verified |
| FR-PRG-001/002 progress | UI: whole-subject row and recommendation; IT | Verified |
| FR-PLN-001/002 planner | UI: task added, done, reopened, edited, deleted; exam added; countdown; dashboard next exam | Verified |
| FR-AI-001…004, 006 AI tools | UI on the mock: explain, flowchart (5 boxes), 5 practice questions, quota 27 of 30; IT: `AiApiIT`, `AiFlowchartIT`, router tests | Verified on the mock; a live provider waits for the owner's decision |
| FR-ADM-001…010 admin | UI: all 14 admin pages at 3 sizes; question bank filter (45 Java questions, "Whole subject"); complaints list; IT per feature | Verified; resource upload needs the bucket |
| FR-UX-001/002 states, landing page | UI: loading, empty and "Too many requests" states seen; landing hero, globe (2 canvases), FAQ and footer at 1440 and 390 px | Verified |

## 5. Backend audit

- **Findings:** no defects in this pass.
- **Rate limit:** the per-account API limit (120 requests a minute) was confirmed. A fast scripted sweep triggered it, and every page showed its "Too many requests… Retry" state.
- **Authorization:** checked through the API. A student gets 403 on admin endpoints; a visitor gets 401. Another student's note, its attachments and its listing are not reachable (404, absent).
- **Remaining:** none new. Sessions and rate limits are held in memory, which allows one instance only; this is a documented design.

## 6. Frontend audit

- **Fixes:** the five corrections in section 3, and dead CSS removed.
- **Checked:**
  - every page has one `<h1>`;
  - every visible control has an accessible name;
  - Material-symbol icon words in Notes are `aria-hidden` (18 of 18);
  - focus outlines exist on the navigation items;
  - every local link loads (82 links, earlier today).
- **False alarms ruled out:**
  - The timetable's day pills extend past 390 px inside their own horizontal scroller, as designed.
  - Profile has a hidden placeholder `<img>` with no source until a photo exists.

## 7. Playwright results

- **Browser:** Chromium, through the Playwright MCP tools. The app was a local JAR on port 8099 with a throw-away PostgreSQL 17 container and demo data; port 8080, which is tied to hosted data, was not touched.
- **Page sweep:**
  - 54 student loads across 5 viewports (all 18 pages at 1440 and 390 px; six key pages at 1280, 768 and 360 px);
  - 42 admin loads (all 14 pages at 1440, 390 and 768 px);
  - the landing page at 1440 and 390 px.
- **Workflows:** planner, notes with autosave and isolation, resources with search and bookmarks, complaints, the AI tools, Quick Exam (earlier today), authentication, the admin question bank and complaints.
- **Console:**
  - the login page's expected 401 (session check before sign-in);
  - "Missing property" from the Spline robot scene (known, section 15);
  - a 503 for a resource download link (no bucket configured);
  - one `InvalidStateError` from the browser's view transition on the first page after login (section 15).
- **Network:** no other failed request.

## 8. Responsive verification

- **Student pages:** at 1440, 1280, 768, 390 and 360 px there is no horizontal page scroll, and the sidebar becomes the tab bar on phones.
- **Admin pages:** tables turn into cards on phones; checked at 1440, 768 and 390 px.
- **Landing page:** screenshots at 1440 and 390 px were inspected; the hero, buttons and globe are laid out correctly.

## 9. Database and API verification

- **Migrations:** V1 to V19 apply in order to an empty database in every integration run. `DatabaseSecurityModelIT` pins every table's RLS and the `cc_app` privileges, including `notice_exam_papers`.
- **Hosted database:** at V19 (applied on 2026-10-24 with the owner's approval before this pass). This pass only read it.
- **Isolation:** `EndpointAccessIT` covers every endpoint for 401 and 403, and the feature integration tests cover the wrong owner (404).

## 10. Security review

- **Secret scan:**
  - the staged diff of every commit in this pass was scanned for key patterns and for each of the 25 secret-like values in `.env` (values never printed);
  - the outgoing commits were scanned before the push (section 14);
  - nothing was found.
- **Dependencies:**
  - the landing page's npm lockfile has **0 known vulnerabilities** (`npm audit`, production and development);
  - no Maven dependency scanner is configured, and adding one needs owner approval (AGENTS.md section 11);
  - GitHub Dependabot will cover the Java dependencies once switched on (section 16).
- **Session:** the in-tab cache of the user's name and photo link is cleared on logout and on the login page; the photo link is a 5-minute signed URL.
- **Credentials:** no new credential exposure. Owner rotation is still required; see section 16.

## 11. File cleanup

| Removed | Why unused | How verified |
| --- | --- | --- |
| CSS rules `.chip--action` (3 rules), `.chip__hint`, `.inset--link` (2 rules), `.figure--danger` in `css/app.css` | No page, script or template uses these classes. `inset--link` lost its last user when the dashboard's "Revise next" card was removed | Searched every HTML and JS file, including dynamically built class names (`"chip--" +`, `"figure--" +`): none |

**Kept after checking:**

- `vendor/spline/PACKAGE.json`: a provenance and licence note for the vendored runtime.
- Every Spline chunk: each is referenced by `runtime.js`.
- All 138 static files: every other one is referenced.

**Not touched:** `stitch_file_based_generator.zip` (local, ignored by Git, owner's reference), `notes/`, `notices/`, `private-data/`.

## 12. Performance

- **No new measurements in this pass.** Earlier local figures still stand: API calls of 5 to 73 ms, gzip and caching on static files, and about 400 to 460 MB resident memory with `-Xmx256m`.
- **Not production evidence:** memory on the 512 MB host still has to be measured (docs/24 section 6a).

## 13. Git history (this pass)

| Commit | Purpose | Evidence |
| --- | --- | --- |
| `85cac4f` | `.playwright-mcp/` ignored | `git status` clean of evidence |
| `e6c5e77` | Login page clears the cached user | Playwright: cache empty after logout and on the login page |
| `8afc38d` | Owner's frontend work plus QA fixes (23 files) | Full verify; Playwright sweep |
| this commit | This report | — |

The commits from earlier today are listed in [26](26-final-project-completion-report.md).

**Final verify on the committed HEAD:** `./mvnw -B verify` on a `git archive` of `8afc38d` (all code; this report's commit adds documents only), with no `.env` and Testcontainers PostgreSQL 17: **296 unit and 307 integration tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS** (195 s). Node module tests: 13 passed. Formatting (`spotless:check`) clean.

## 14. GitHub push

Remote `origin` = `github.com/Laxmansirvi05/student-management-system`, branch `main` (upstream `origin/main`). After `git fetch`: remote 0 commits ahead, local 46 ahead, so a plain fast-forward push (no force).

**Outgoing scan** (all 46 commits, 222 files):
- No file under `.env`, `private-data/`, `notes/`, `notices/` or `.playwright-mcp/`.
- No value from `.env` and no test password appears in the outgoing changes.
- One key-shaped string matched: `AiRouterWorkflowIT.SECOND_KEY`. It is a made-up test key (`…SECOND_made_up_key_0002`) that matches no real key.
- The bucket name in `.env.example` is the documented default, not a secret.

Push result: recorded in the commit that follows, and in the owner's summary.

## 15. Remaining issues

| Severity | Issue |
| --- | --- |
| Critical | None |
| High | None |
| Medium | None |
| Low | The Spline robot scene logs "Missing property" from a state transition in `robot-scene.splinecode`. The fix belongs in the Spline editor; vendor code is not edited |
| Low | On the first page after login, Chrome reports an aborted cross-page view transition (`InvalidStateError`) before the page's scripts run. Console only, no visible effect |
| Low | Sessions and rate limits live in memory (one instance; users sign in again after a restart). Documented design |
| Low | Not run in this pass: the landing build (`npm run build`; its committed bundle was checked in the browser), live session expiry (30 minutes), file uploads (bucket) |

## 16. Production blockers (owner actions)

1. **Rotate credentials:** `DB_PASSWORD`, `DB_MIGRATION_PASSWORD`, the disabled Gemini key `36df8d6d`, a review of `SUPABASE_SERVICE_KEY`, and separate production AI keys (docs/24 section 3).
2. **Decide the AI providers and approve `AI_ROUTES`.** Recommended: Groq with Zero Data Retention, plus Ollama. Then update the privacy page's list of services.
3. **Sign off the storage bucket** (docs/24 section 5). Eight requirements are verified only against a stand-in until then.
4. **GitHub settings:** Dependabot alerts and security updates, secret scanning with push protection, private vulnerability reporting, and protection for `main`.
5. **Hosting:** choose the host (Render Starter recommended) and change `plan: free` in `render.yaml`. Create the service with `SPRING_PROFILES_ACTIVE=prod` and `DEMO_SEED_ENABLED` unset. Run the first-deploy checks and the memory measurement.
6. **After the first admin login:** remove `ADMIN_BOOTSTRAP_*`.
7. **College approval** of the privacy policy and terms.
8. **A teacher's read** of the 135 bundled Quick Exam questions.
9. **Already done on 2026-10-24:** hosted backup, V16 to V19 applied, exam papers loaded, `.env.backup-2026-10-23` deleted.

## 17. Final verdict

**CONDITIONAL PASS: the engineering checks passed, but the external actions in section 16 remain.**

| Status | Result |
| --- | --- |
| Code complete | Yes |
| Locally and integration tested | Yes |
| Browser verified | Yes, except features needing the real bucket or a live AI provider |
| Staging verified | No staging environment |
| Production verified | No: not deployed |

The application should not be deployed for real students until the blockers in section 16 are cleared.
