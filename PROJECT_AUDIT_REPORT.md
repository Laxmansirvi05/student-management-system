# Campus Companion — Project Audit Report

> **Part A** (this first section) is the remediation status of 2026-10-23, written after the fixes; Parts A2 to A5 add the later phases, the newest being the autonomous completion run of 2026-10-24 (Part A5, full report in [docs/26](docs/26-final-project-completion-report.md)). **Part B** (sections 1 to 14 and Appendix A) is the original audit. Its findings, measurements and evidence are kept as found, so the "before" stays readable, but the issue register (§7), readiness (§3.4), roadmap (§12), deployment (§13) and verdict (§14) now also show the current status. Original audit: `main` at `4fd019e`. Remediation: commits `82bd52b` to `c00a4f9` on `main`, local only (not pushed, not deployed). The owner's 23 uncommitted frontend files were never staged or changed (checked byte for byte against a backup taken before the work).

## Part A — Remediation status (2026-10-23)

### A.1 At a glance

| | Before (Part B) | After remediation |
| --- | --- | --- |
| **Findings** | 0 Critical · 4 High · 8 Medium · 8 Low (20) + CC-21 unverified | **Fixed 18** · **Open 2** (CC-13 deployment memory, CC-14 robot scene) · CC-21 still owner-only |
| **High findings** | CC-01, CC-02, CC-03, CC-04 open | **All 4 fixed** |
| **Tests** | 263 unit + 263 integration | **265 unit + 282 integration** (Java), **13** browser-module tests (Node), all passing. On 2026-10-24: **295 unit + 305 integration**, 13 Node, all passing (Part A5) |
| **`./mvnw verify`** | passed | **passed** on the working tree and, separately, on the committed `HEAD` alone (section A.4) |
| **Student page weight** (local, cold → later page) | 3.1 MB on every page, no cache, no gzip | **1.2 MB first page → 15–41 KB on later pages** |
| **Landing page** | 1.32 MB every visit | **370 KB first visit, 1 KB after** |
| **Product completion** | ≈ 81 % | ≈ 81 % after remediation (no PRD features added); ≈ 88 % after the planner and readiness phase; **≈ 94 %** after the completion run (Part A5.1) |
| **AI assistants** (Phase 1, Part A2) | Mock only | **Real AI through a 19-route router** (Groq, Ollama, Gemini, OpenRouter) with automatic fallback; live-tested; data-terms review open |
| **Planner and production readiness** (Parts A3, A4) | — | Planner built (`c5c1c03`); readiness reviewed: **not ready for real students yet**, blockers in docs/24 §1; completion ≈ 88 % with FR-AI-005 dropped |
| **Completion run** (Part A5) | — | Report a resource (FR-LIB-005), flowchart (FR-AI-006), AI usage-log retention (T7-09, off by default); all 31 pages browser-checked; every approved P0/P1 requirement built and integration tested, 47 of 59 browser verified |

**What is left for the owner** (one checklist: [docs/26](docs/26-final-project-completion-report.md) §18): rotate the credentials in F4/CC-21; decide the AI providers and `AI_ROUTES` (T5-06, T5-08); sign off the storage bucket (T3-01); turn on the GitHub security settings (T0-04); back up hosted Supabase and approve V16 to V18; choose the host (Render Starter recommended), deploy (T6-04 to T6-06) and measure memory there (CC-13); get the college's approval of the privacy policy and terms; and fix the robot scene in the Spline editor (CC-14).

### A.2 Finding by finding

| ID | Sev. | Status | What was done | Commit | Files |
| --- | --- | --- | --- | --- | --- |
| Cleanup | — | Done | Removed 7 stale landing JS bundles, 6 stale CSS bundles, 2 old landing scripts, `index.original.html` and `ui/demo.tsx` (17 files, 9.4 MB). Kept `css/landing.css` because `PageRoutingIT` requests it. | `82bd52b` | 17 deletions |
| **CC-01** | High | **Fixed** | An attachment of someone else's note opens only for the uploader, a named recipient, or a student who sends the note's share slug (`?share=`, compared in constant time) while link sharing is on; never by number alone, and for nobody but the owner while the note is in the Trash. The shared-note page adds the slug to every attachment address. | `68e48ff` | `NoteAttachmentService`, `NoteAttachmentController`, `js/note-shared.js`, `NotesIT`, docs 09, 10, openapi |
| **CC-02** | High | **Fixed** | The privacy page now says Quick Assistant chats are stored (newest six, student only, deletable, nothing on failure) and that the Explain, Summarize and practice tools store no text. | `f7a4f41` | `privacy.html`, docs 08, 14, `PageRoutingIT` |
| **CC-03** | High | **Fixed** (owner's decision recorded) | ADR 0015: React and TypeScript for the isolated landing page only, built ahead of time, unchanged CSP; the backend and every other page keep the stack. TRD and decision index updated; a test checks that only `index.html` loads the bundle and no TypeScript is served. | `502b70c` | ADR 0015, `docs/04-TRD.md`, `docs/decisions/README.md`, `StaticFrontendRulesTest` |
| **CC-04** | High | **Fixed** | gzip for HTML, CSS, JS, SVG and wasm (never JSON, because of BREACH); hash-named landing files cached a year; other static files cached with revalidation (an unchanged file costs a 304); pages and API answers stay `no-store`; the robot's 3D scene starts when the browser is idle after load, or at once on hover, touch or focus. | `af614a6` | `application.yml`, `StaticResourceCaching`, `js/robot/robot.js`, `StaticResourceCachingIT` |
| CC-05 | Medium | **Fixed** | Share recipients are returned and shown by roll number only, never by name. | `bef4ee6` | `NoteShareService`, `ShareRecipientResponse`, `share-dialog.js`, `NotesIT`, doc 09 |
| CC-06 | Medium | **Fixed** | A shared editor's own uploads join the note when saved; the note's owner may open any file of their note; destroying a note removes every file in it. The uploader is unchanged, and nobody can pull another student's upload into a note. | `933f06d` | `NoteAttachmentService`, `NoteAttachmentRepository`, `NoteService`, `NotesIT`, docs 09, 10 |
| CC-07 | Medium | **Fixed** | Autosave retries offline, 408, 429 and 5xx failures with backoff (1 s to 30 s, six tries) or `Retry-After`; a create is repeated only for 429/503 (no duplicate notes); revisions stop stale or duplicate writes; unsaved text is sent with `keepalive` when the page is hidden or closed; the panes show "Not saved yet, trying again…". | `31b7ccc` | `notes/autosave.js`, `api.js`, `notes-api.js`, `note-editor-pane.js`, `robot/quick-note.js`, `src/test/js/autosave.test.mjs` |
| CC-08 | Low | **Fixed** | See Cleanup. | `82bd52b` | — |
| CC-09 | Medium | **Fixed, with a known limit** | `FrontendSmokeIT` (existing JUnit tooling) opens every public, student and admin page and follows every script, style sheet, font and module import; checks the robot's files, the landing bundle and saving a note. Shown to fail when a deep import, a CSS font or the robot decoder is missing. CI also runs the Node tests. Pixel rendering is not automated: that would need a browser tool, a new dependency that needs approval (docs/17 §7). | `c00a4f9` | `FrontendSmokeIT`, `.github/workflows/ci.yml`, docs 17 |
| CC-10 | Medium | **Fixed** | AGENTS.md (state, V1–V15, Gemini adapter not yet accepted, stack exception, layout, commands), README, PRD §5c (Notes, Quick Assistant, robot), ADR 0005 update note, plan progress. | `1d8c180` | AGENTS.md, README.md, docs 02, 16, ADR 0005 |
| CC-11 | Medium | **Fixed** | The test base class pins the mock AI, empty AI key, Supabase settings and bootstrap admin, overriding `.env`. Proved by running 51 AI, assistant, auth and page tests against a deliberately hostile dummy `.env`. | `b92a2e3` | `PostgresIntegrationTest`, `HermeticTestsIT` |
| CC-12 | Medium | **Fixed** | Emptying a saved note moves it to the Trash (restorable with its files, links and sharing) instead of destroying it. | `ce27473` | `NoteService`, `NoteSaved`, `autosave.js` comment, `NotesIT`, doc 09 |
| CC-13 | Medium | **Open** (owner/deployment) | Measured again locally: 401 MB RSS after start with `-Xmx256m`. Only measurable on the chosen host. In-memory sessions remain by design (ADR 0004). | — | — |
| CC-14 | Low | **Open, cause found** | The "Missing property" error is thrown in the Spline runtime by a state-transition animation of `robot-scene.splinecode` whose from/to state does not exist on its object (`new dp` from `buildTimeline`, `runtime.js:8118`). The runtime catches it; the robot still draws and works. The fix is to repair the transition in the Spline editor and export the scene again. Vendor code was not edited and the error was not hidden. | — | — |
| CC-15 | Low | **Fixed** | The privacy page and docs 08 and 14 no longer promise a 90-day removal of AI usage numbers that does not exist (T7-09 remains backlog). | `f7a4f41` | as CC-02 |
| CC-16 | Low | **Fixed** | See Cleanup. `css/landing.css` remains (a test requests it). | `82bd52b` | — |
| CC-17 | Low | **Fixed** | The AI usage limits take their counted outcomes from `AiRequestStatus.countsTowardLimits()`. Entity getters were left alone (JPA, Jackson or tests may read them). | `448c84e` | `AiUsageService`, `AiUsageServiceTest` |
| CC-18 | Low | **Fixed** | The API limit is counted per account across all its sessions once logged in, so logging in again does not reset it. | `f4c14da` | `ApiRateLimitFilter`, `application.yml`, `RequestLimitsIT`, docs 09, 11 |
| CC-19 | Low | **Fixed** | AGENTS.md no longer lists `application-dev.yml`; it explains that the `dev` profile only switches on the demo seeder. | `1d8c180` | AGENTS.md |
| CC-20 | Low | **Fixed** | Settings and first-login password forms get a hidden `autocomplete="username"` field. Done in `settings.js` and `change-password.js`, so `settings.html` (owner's uncommitted work) was not touched. The browser warning is gone. | `7826b40` | `settings.js`, `change-password.js` |
| CC-21 | (High if open) | **Owner-only** | Credential rotation (security review F4) cannot be done or checked from the code. | — | — |

### A.3 Remaining risks and owner actions

| Item | Why it matters | Who |
| --- | --- | --- |
| Rotate exposed credentials (F4/CC-21) | Old credentials may still work | Owner |
| Choose Heroku or Render, deploy, then measure memory (T6-04 to T6-06, CC-13) | Nothing is live; 401–461 MB RSS locally against a 512 MB plan | Owner |
| Storage bucket sign-off (T3-01) | Real uploads, previews and signed links were never exercised in this work, by design | Owner |
| Live AI acceptance (T5-06, T5-08) | The Gemini adapter is tested only against a fake server | Owner |
| Repair the robot scene (CC-14) | Console error on every student page; no functional effect seen | Owner (Spline editor) |
| Browser automation (CC-09 limit) | Rendering of the landing grid, globe, carousel and robot is checked by hand only | Owner approval of a test dependency |
| AI usage-log clean-up job (T7-09) | Rows stay until an administrator removes them; the policy now says so | Backlog |
| `.env` sets `SPRING_PROFILES_ACTIVE=dev` | If that profile is honoured from `.env`, the demo seeder can run against whatever database `.env` points at. **Not verified**; worth checking before the next local run against hosted Supabase | Owner |
| `assistant` mode chats | The chat API also stores chats with `mode` other than `quick`, without the six-chat limit; no page uses that mode today | Later (small fix) |
| Rebuild of the landing page | A fresh Vite build gives the same JS bytes but new file names; ADR 0015 says how to replace them | Whoever rebuilds |

### A.4 Verification actually run (final state)

All runs used isolated copies under the session's scratch folder, without `.env`, private data or `target/`, against throw-away PostgreSQL containers (Testcontainers, or a `postgres:17-alpine` container removed afterwards). The running server on port 8080 and the hosted database were not touched. No real AI or storage was called.

| Check | Result |
| --- | --- |
| `./mvnw -B verify` on the working tree (commit `c00a4f9` plus the owner's uncommitted frontend files) | **BUILD SUCCESS**: 265 unit, 282 integration, 0 failures, Spotless clean, 94.8 s |
| `./mvnw -B verify` on `git archive HEAD` only (committed state, without the owner's uncommitted files) | **BUILD SUCCESS**: 265 unit, 282 integration, 0 failures, 1 min 37 s |
| `node --test "src/test/js/**/*.test.mjs"` | **13 pass, 0 fail** |
| Landing production build: `tsc` and `vite build` (output to a scratch folder) | **Passed**; the JS is byte-identical to the committed `main-BSEaXpJF.js` |
| `git diff --check` before each commit | Clean |
| Targeted tests per task | Run and passed before each commit (NotesIT 32→37 tests, FrontendSmokeIT 6, StaticResourceCachingIT 5, RequestLimitsIT 5, HermeticTestsIT 2, PageRoutingIT, AssistantIT, AiApiIT, RequestLimitsIT, StaticResourceCachingIT, FrontendSmokeIT, StaticFrontendRulesTest, AiUsageServiceTest and others) |
| CC-11 hostile `.env` run | 51 AI, assistant, auth and page tests pass with a dummy `.env` set to `gemini`, a fake key, a fake storage host and AI switched off |
| CC-09 failure check | With a deep import, a CSS font and the robot decoder deleted, `FrontendSmokeIT` failed 3 of 6 tests, naming each missing file |
| Browser smoke (Playwright, headless Chromium, JAR from the verified copy on port 8099, throw-away DB) | Landing: animated grid and globe canvases, all sections and the six-card carousel present. Student pages: no missing file (the only failed request is the expected logged-out 401 on the landing page). Robot: 3D runtime **not** requested at page load, scene and canvas present later; Quick Note opened with Enter, autosaved ("✓ Saved") and appeared on the Notes page; Quick Assistant opened with A. Settings: hidden username field present, browser warning gone. Console: only the known CC-14 "Missing property". |

### A.5 Performance after remediation (local)

Same conditions as Part B section 6: MacBook (Apple Silicon), JAR with `-Xmx256m -XX:+UseSerialGC`, throw-away DB with demo data, headless Chromium at 1280×800, one run each. Cold = empty browser cache; a "later page" is the next page in the same visit.

| Measure | Before | After |
| --- | --- | --- |
| Landing, first visit, transferred | 1,323 KB | **370 KB** (gzip) |
| Landing, repeat visit, transferred | 1,323 KB | **1 KB** (cached a year; only the page itself) |
| Student dashboard, first page of a visit | 3,113 KB | **1,200 KB** |
| Attendance, Notes, Assistant as later pages | 3,111–3,154 KB each | **17 KB, 41 KB, 23 KB** (304 revalidations) |
| Dashboard again | 3,125 KB | **15 KB** |
| Robot runtime at page load | requested at once | **not requested until idle or interaction** |
| Long tasks recorded within 6 s of a cold dashboard load | 1,442 ms | **none ≥ 50 ms recorded** (one run; treat as indicative) |
| FCP, cold dashboard | 556 ms | 68 ms |
| Spline runtime transferred | 2,049 KB | 582 KB (gzip) |
| App start / RSS after start | 4.25 s / 461 MB | 4.31 s / 401 MB (one run each; not a meaningful change) |

**Not measured:** production or hosted numbers, real storage and AI latency, low-end phones, CPU under load.


## Part A2 — Phase 1: several AI providers with automatic fallback (2026-10-23)

Both assistants now use real AI models through a router with automatic fallback. The mock remains the default for every automated test. Local commits `406c309` to `d5be57f`; not pushed or deployed. Keys were never printed, logged, sent to the browser or committed. Keys are identified below only by variable name and the first 8 hex characters of their SHA-256 fingerprint.

### A2.1 Configurations found and tested

Found in the local `.env`: **15 AI keys** for 4 providers. Before this phase, only Gemini had an adapter; Groq, OpenRouter and Ollama now share one new adapter. Each key had one minimal request ("Reply with the single word OK"); model lists came from the providers' list endpoints (no generation cost).

| Provider | Keys (fingerprint) | Result | Models that worked | Notes |
| --- | --- | --- | --- | --- |
| Groq | `GROQ_API_KEY` (7e3a641c), `_2` (d4d16a8e), `_3` (187d0aa3), `_4` (3e2ab1a7), `_5` (ee7e6b21) | **All 5 valid** | `openai/gpt-oss-120b` (698 ms), `openai/gpt-oss-20b` (482–615 ms), `qwen/qwen3.8-27b` (162 ms) | Groq's firewall refuses Python's and Java's default client name ("error code 1010"); the adapters now send a `User-Agent`. |
| Ollama (cloud) | `OLLAMA_API_KEY` (4e8ecf67), `_2` (f006d80e) | **Both valid** | `gpt-oss:20b` (1.3–2.1 s), `gemma4:31b` (1.6 s) | — |
| Gemini | `GEMINI_API_KEY` (36df8d6d) | **Unusable: disabled** | none | 403 "Your project has been denied access" on every model tried (3 models). Commented out in `.env` with a note; the original is kept in `.env.backup-2026-10-23` (git-ignored, mode 600). Owner: ask Google support or delete the project's key. |
| Gemini | `_2` (68dfeb08), `_3` (f95c7d59), `_4` (050ac3b4) | **Valid** | `gemini-3.5-flash-lite` (1.3–8.1 s) | `gemini-2.5-flash` and `gemini-2.5-flash-lite` answer 404 "no longer available to new users" (unsupported model, not a bad key). |
| OpenRouter | `OPENROUTER_API_KEY` (ac555b89), `_2` (99e6265d), `_3` (0dd98dd5), `_4` (0e6c8f75) | **All 4 valid** (key endpoint: free tier, no limit set) | `nvidia/nemotron-3.5-lightning:free` (2.5 s for "OK") | `google/gemma-4-31b-it:free`, `gemma-4-26b-a4b-it:free`, `poolside/laguna-xs-2.1:free`: 429 "temporarily rate-limited upstream" (temporary; keys kept). `nemotron-3-super-120b-a12b:free`: 404. `inkling-small:free`: 403 "only on agentic harnesses". |

No key was removed for a rate limit or quota. Only the Gemini key refused for every model was set aside.

### A2.2 Routing and fallback (what was built)

Full rules: [docs/12](docs/12-AI-features-spec.md) §2b and [ADR 0016](docs/decisions/0016-several-ai-providers-with-fallback.md).

- `AI_PROVIDER=router` with `AI_ROUTES` in priority order. The local `.env` now holds: `groq:openai/gpt-oss-120b, groq:qwen/qwen3.8-27b, ollama:gpt-oss:20b, gemini:gemini-3.5-flash-lite, openrouter:nvidia/nemotron-3.5-lightning:free`. That gives 19 routes (each entry × each key of its provider). Groq and Ollama come first; the free tiers with weaker data terms come last.
- A request goes to the first route that is not paused; the next route is tried only after a failure, or after a reply that the feature's own parser/validator rejects.
- **A 429 pauses the whole provider** (its `Retry-After`, capped at 10 min, else 60 s). A provider's other keys are tried only when a key itself is refused (401/403, 30-min pause for that key), never to get round a limit. A 404/400 pauses that model for 30 min; a 5xx, network failure or timeout pauses it for 30 s. An unusable reply moves on to the next *model* without a pause.
- Bounds: at most 3 calls per request, 15 s each, within the 25 s budget; no call starts with under 2 s left. The time limit covers the whole answer, body included. When everything is paused, the request fails at once (measured: 17–28 ms) and calls nobody. The mock is never a route, so a failure is never disguised as an answer.
- Both assistants (Explain, Summarize, practice questions, and the robot's Quick Assistant) go through the same router. One usage row is recorded per student request. Quotas, concurrency limits, history, deletion and privacy between students are unchanged.

### A2.3 Real-provider acceptance (live, through the full application)

The built JAR ran on port 8099 with a throw-away PostgreSQL. Each line is one real request through the router, the adapter and the feature's parser/validator; times are end-to-end HTTP times measured with curl. 30 feature requests in all, plus 2 in the browser.

| Route(s) | Explain | Summarize | Practice questions (3 MCQ) | Quick Assistant |
| --- | --- | --- | --- | --- |
| Groq `gpt-oss-120b` only | 200, 1.66 s | 200, 1.57 s | 200, 2.00 s, 3×4 options | 200, 0.64 s |
| Ollama `gpt-oss:20b` only | 200, 3.53 s | 200, 22.4 s (key 1's reply failed the checks; at the time, key 2 of the same model answered: fixed, see below) | 200, 6.07 s | 200, 1.72 s |
| Gemini `gemini-3.5-flash-lite` only | 200, 1.71 s | 200, 1.48 s | 200, 2.97 s, 3×4 options | 200, 0.99 s |
| OpenRouter `nemotron-3.5-lightning:free` only, first run | 504 after 38.7 s | 504 after 32.3 s | 504 after 30.8 s | 200, 13.3 s |
| Same, after the fix | 504 at the 15 s limit | 503 in 18 ms (paused) | 503 in 18 ms (paused) | 503 in 29 ms (paused) |
| **Fallback**: retired `gemini-2.5-flash` first, then Groq | 200, **2.68 s** (404, then Groq) | 200, 1.58 s (retired model skipped while paused) | 200, 2.11 s | 200, 0.79 s |
| **Recommended order** (as in `.env`) | 200, 2.00 s | 200, 1.13 s | 200, 2.32 s, 3×4 options | 200, 0.85 s |
| Browser (Playwright, recommended order) | Answer shown in the page, 3.1 s; no "Demo mode" notice | — | — | Real reply through the robot (A key); listed in Quick chat history; Quick Note still opens (Enter) |

The OpenRouter free model is too slow, and its JSON replies failed the parser, for the three study tools; it worked for the Quick Assistant. It stays last in the order as a final fallback. Fallback overhead measured: about +1 s when the first route answers 404.

**Two defects found by the live run and fixed** (`5d8dc6c`):
1. Java's request timeout stops at the response headers, and OpenRouter sends its headers at once, so requests ran up to 39 s against the 25 s budget. Now a hard limit covers the whole answer.
2. After an unusable reply, the router retried the same model with the provider's next key. Now a model whose reply failed is skipped for the rest of the request.

### A2.4 Commits (Phase 1)

| Commit | What |
| --- | --- |
| `406c309` | `OpenAiCompatibleAiProvider` (Groq, OpenRouter, Ollama), shared `ProviderHttp` classification (401/403, 404, 408, 429 + Retry-After, 5xx), User-Agent; tests |
| `909f295` | `RoutingAiProvider` (order, pauses, bounds), `AI_PROVIDER=router`, `AI_ROUTES`, `AiRoutingProperties`, docs/12 §2b, `.env.example`; 11 router tests, 8 configuration tests |
| `86571ab` | Explain, Summarize, practice questions and Quick Assistant pass their parser/validator as the "usable reply" check; all-invalid gives 502 |
| `a2cc66e` | `AiRouterWorkflowIT`: both assistants end to end through real adapters and two local fake providers (fallback, 429 pause, invalid replies, all-fail, fail-fast, recovery, history privacy, usage counting, key redaction in responses and logs) |
| `5d8dc6c` | Whole-answer time limit; skip a model's other keys after an unusable reply; regression tests |
| `d5be57f` | Privacy page names the AI services and the fallback; ADR 0016; ADR 0005 note |

### A2.5 Verification

| Check | Result |
| --- | --- |
| AI unit and integration tests during the work | All passing (adapters, router, configuration, services, `AiApiIT`, `AssistantIT`, `AiRouterWorkflowIT`, `HermeticTestsIT`, `PageRoutingIT`) |
| `./mvnw -B verify` on the committed `HEAD` alone (`git archive`) | **BUILD SUCCESS**: 287 unit, 289 integration, 0 failures, Spotless clean, 1 min 42 s |
| Node browser-module tests | 13 pass, 0 fail |
| `git diff --check` | Clean |
| Secret scan | No key (in full, nor its first 12 or last 8 characters) in any Phase 1 commit or in any of the 8 live server logs; the disabled key is not in Git |
| Landing page | Not affected by this phase; not rebuilt |

### A2.6 Remaining blockers and owner actions

| Item | Why |
| --- | --- |
| **Review each provider's data terms (OQ-1, ADR 0016)** before real students use the assistant | Student text may now go to Groq, Ollama, Google (free tier) or OpenRouter (free models). Free tiers may keep or train on prompts. Remove any provider you do not accept from `AI_ROUTES`. |
| Gemini key `36df8d6d` | Project denied by Google; ask support or delete it. |
| Several keys of one provider | Use them only within each provider's terms. The router never cycles through them to get round a limit. If they belong to separate accounts made only to multiply free quota, that may itself break the provider's terms. |
| Restart the 8080 server to use the router | `.env` now has `AI_PROVIDER=router`; the running server still uses the mock until it is restarted (it was not restarted here). |
| Models change | Free models appear and disappear; check `AI_ROUTES` when the logs show `MODEL_UNAVAILABLE`. |
| Usage log has no provider column | Which model answered is in the application log only; storing it would need a migration. |
| Deployment | Separate task: set `AI_PROVIDER`, `AI_ROUTES` and the keys as host secrets. |



## Part A3 — Planner, T7-02 (2026-10-24)

Commit `c5c1c03`, local. Study tasks (FR-PLN-001) and the exam countdown (FR-PLN-002: the student's own exam dates plus the college's published Exam notices) on a new Planner page and a "Next exam" dashboard card. `V16__planner.sql` adds `study_tasks` and `student_exams` with RLS. It is **not yet applied to hosted Supabase**. Tested: `PlannerRulesTest` (4) and `PlannerIT` (8), plus the database-security, access and smoke tests; full `verify` passed (291 unit, 297 integration), and a browser check against a throw-away database. Details: [docs/02-PRD.md](docs/02-PRD.md) §8 and [docs/09-api-contract.md](docs/09-api-contract.md) §8.22.

## Part A4 — Production readiness, phase 1 (2026-10-24)

Full write-up: [docs/24-production-readiness.md](docs/24-production-readiness.md). **Verdict: not ready for real students yet.** The code is built and tested; the open blockers are mostly owner actions.

| Item | Status |
| --- | --- |
| Secrets in Git | None. All 19 secret values in the local `.env` and its backup were searched for in tracked files and the full history; common key patterns too. |
| Repository security defects needing a code change | None found. Sessions, CSRF, CORS (none), headers, logging, roles and RLS, and authorization were reviewed (doc 24 §2). |
| Deliberate settings kept | `DEMO_SEED_ENABLED=true` also seeds under `prod` (the documented demonstration workflow, T6-06); `AI_PROVIDER=mock` works under `prod` (shown to students as demo mode). Both are deploy-checklist items. |
| Unverified until first deploy | HTTPS behind the host's proxy (forwarded headers), memory on a 512 MB plan, real Storage bucket, V16 on the hosted database |
| Credentials to rotate (owner) | **Required:** `DB_PASSWORD` (cc_app), `DB_MIGRATION_PASSWORD` (cc_migrator) (F4); remove `ADMIN_BOOTSTRAP_PASSWORD` after first login; delete Gemini key `36df8d6d`. **Recommended:** `SUPABASE_SERVICE_KEY`, and separate production AI keys. Then delete the local `.env.backup-2026-10-23`. Names only; where to rotate is in doc 24 §3. |
| AI provider terms (checked 2026-10-24, official pages) | Groq: no training by contract, not retained by default, Zero Data Retention available. Ollama Cloud: transient, no training. **Gemini free tier: used to improve Google's products, human review, "do not submit personal information", users must be 18+.** **OpenRouter `:free`: upstream providers may train; the current model states no policy.** Recommendation (not applied): keep Groq and Ollama, drop Gemini free and OpenRouter free from student traffic. Doc 24 §4. |
| Host | Not chosen. Recommendation (not applied): **Render Starter** ($7/month, 512 MB): `render.yaml` and the Dockerfile exist, deploys are health-checked, there is no daily restart, and there is no 30 s router limit. Heroku Basic ($7/month, 512 MB) is the alternative. No free or sleeping plan for students. Doc 24 §7. |
| PDF Summarizer (FR-AI-005, T7-04) | **Dropped / out of scope** (PRD, plan). Not counted as outstanding. |

### A4.1 Completion, recalculated (same method as Part B §3)

| Change since the original audit | Effect |
| --- | --- |
| FR-LEG-001 privacy and terms: accurate now (CC-02, CC-15) | P0 item 50 % → 100 % |
| FR-AI-001…003: real providers through the router, live-tested; the owner's behaviour run (T5-08) and the data-terms review are still open | P0 item 50 % → 75 % |
| FR-PLN-001, FR-PLN-002: built, tested, browser-checked | P1 items 0 % → 100 % each |
| FR-AI-005: dropped | Removed from the count (P1 now has 11 items) |

P0 = 1575 / 17 = **92.6 %**. P1 = 750 / 11 = **68.2 %**. Overall = (1575 × 3 + 750) / (17 × 3 + 11) = 5475 / 62 ≈ **88 %** (was ≈ 81 %). Production readiness is a separate question: see the blockers above.

### A4.2 Verification in this phase

`./mvnw -B verify` in an isolated copy; Node tests; secret scan; documentation-only changes otherwise. Results are recorded in the commit message of this phase and in the final report to the owner.

## Part A5 — Autonomous completion run (2026-10-24)

Full write-up: [docs/26-final-project-completion-report.md](docs/26-final-project-completion-report.md); plan and matrix: [docs/25-autonomous-completion-plan.md](docs/25-autonomous-completion-plan.md). Commits `a4471f6` to `758a693` plus the closing docs commit, local only.

| Item | Result |
| --- | --- |
| FR-LIB-005 report a resource | Built (V17), integration tested, browser verified (`93b24f3`) |
| FR-AI-006 flowchart | Built, graph checked in Java, drawn without Mermaid, integration tested, browser verified on the mock (`9e965be`) |
| T7-09 AI usage-log retention | Built (V18), off by default (`81f6416`); moved to its own `retention` package after `AiArchitectureTest` failed (`f55c24c`). Orphaned-file clean-up deferred |
| FR-AI-005 PDF summarizer | Dropped; excluded everywhere (`a4471f6`) |
| Browser pass | 17 student and 14 admin pages, desktop and 375 px; attendance planner figures checked by hand. Two defects found and fixed (`8a6e44f`, `758a693`) |
| Hosted database | Still at V15; V16 to V18 wait for backup and approval (doc 24 §6) |

### A5.1 Completion, recalculated (same method as Part B §3, from A4.1)

| Change | Effect |
| --- | --- |
| P0 #15 admin pages: every page now browser verified (resource upload still depends on the bucket, which item 10 already discounts) | 75 % → 100 % |
| P0 #17 error, empty and loading states: checked page by page | 75 % → 100 % |
| FR-ATT-003: browser verified, figures checked | 75 % → 100 % |
| FR-LIB-005: built and browser verified | 0 % → 100 % |
| FR-AI-006: built, verified on the mock only (no live provider run) | 0 % → 75 % |
| FR-ADM-009 viewer: browser verified | 75 % → 100 % |

P0 = 1625 / 17 = **95.6 %**. P1 = 975 / 11 = **88.6 %**. Overall = (1625 × 3 + 975) / 62 = 5850 / 62 ≈ **94 %** (was ≈ 88 %). Production readiness is still separate: **not ready for real students** until the owner actions of doc 26 §18 are done.

## Part A6 — Owner-requested changes (2026-10-24, later)

Details: [docs/26](docs/26-final-project-completion-report.md) §0. Commits `cb62d6f`, `63191bc`, `a33f8f3`, local only.

| Item | Result |
| --- | --- |
| Dashboard results | CGPA on top and every semester's SGPA; browser verified |
| "Revise next" card | Removed from the dashboard |
| Fee deadline | Was hidden by the 14-day window and filed as an Exam; window now 30 days (up to six shown); data step moves it to General |
| Semester exam dates | Exam notices carry papers (V19); "Upcoming exams" lists them; the six December papers wait in `private-data/load-exam-papers.sql` for V19 |
| Quick Exam | Whole-subject tests, random draw; 135 bundled questions for ADS, Java and DBMS; owner review advised |
| Links | 82 page links and every relative doc link checked: none broken |
| Verification | 296 unit + 307 integration tests pass on the committed state (`63191bc`) |

Completion is unchanged at about 94 %: these change existing P0 requirements at the owner's request rather than add new ones, and they are verified the same way. Since then the owner took the backup and approved: V16 to V19 are applied on hosted, and the exam papers are loaded.

## Part A7 — Final QA and deployment (2026-10-24)

- **Final QA** ([docs/27](docs/27-final-predeployment-qa-report.md)): the owner's 23 uncommitted frontend files were reviewed, corrected and committed; Playwright covered 18 student and 14 admin pages at five screen sizes plus the main workflows; verdict CONDITIONAL PASS.
- **Deployed:** Azure App Service B1, Central India, https://campus-companion.azurewebsites.net (primary, student credit, one month; scale-down reminder set for 2026-11-24), and Render Free, Singapore, https://campus-companion-go4c.onrender.com (backup). On both: schema at V19 with no migration, `prod` profile, AI router with Groq and Ollama only, health UP, HTTPS without a redirect loop, security headers, every API 401 when signed out ([docs/24](docs/24-production-readiness.md) sections 8a and 8b).
- **Production verified:** partly. All signed-out checks pass on the live hosts; a signed-in check on the real data is the owner's.
- **Remaining owner actions:** credential rotation, Groq Zero Data Retention and the privacy page's provider list, storage-bucket sign-off, GitHub security settings, college approval of the privacy policy and terms, a teacher's review of the Quick Exam questions, the Spline scene fix.

---

# Part B — Original audit (before remediation)

> Audit date: 2026-10-23 · Branch: `main` at `4fd019e` plus 23 uncommitted frontend changes (left untouched) · Audit only: no project file was changed except this report. Findings and measurements in Part B are as found **before** the fixes. The "Now" column of §7 and the updates in §3.4, §12, §13 and §14 give the status after remediation; Part A has the details.

## At a glance (original)

| | |
| --- | --- |
| **Approximate completion** | **≈ 81 %** of the documented product scope (P0 features ≈ 88 %, P1 features ≈ 52 %). See section 3. |
| **Tests** | `./mvnw verify` **passed**: 263 unit tests + 263 integration tests, 0 failures, Spotless clean, 90.6 s. Run in an isolated copy (section 11). |
| **Unique findings** | **0 Critical · 4 High · 8 Medium · 8 Low** (20 total), plus 1 owner item that could not be verified (CC-21). |
| **Confirmed by experiment** | 3 notes defects reproduced with a throw-away probe test (CC-01, CC-05, CC-06). |

**Most important next actions (as of the original audit; see Part A for what was done)**

1. Close the note-attachment access hole (CC-01): any signed-in student can open the pictures and files of any link-shared note without the link.
2. Correct the privacy policy (CC-02): it says assistant text is never stored, but V15 stores Quick Assistant chats.
3. Get the owner's decision on the React/Vite/Tailwind landing page (CC-03): it breaks the approved stack in `AGENTS.md` and `docs/04-TRD.md`, and has no decision record.
4. Cut the page weight (CC-04): every student page downloads about 3.1 MB, uncompressed and never cached, mostly for the 3D robot.
5. Finish the owner-dependent work: real AI provider acceptance, the storage bucket check, and deployment (section 13).

---

## 1. Executive summary (plain English)

Campus Companion is a student portal written mainly in Java (Spring Boot) with plain HTML/CSS/JavaScript pages. Students see attendance, marks, timetable, notices, study resources, past papers, quizzes, progress, complaints, notes and an AI helper. Admins manage all of that.

**What is good.** The backend is well built and well tested. All 526 automated tests pass. Each layer has a clear job (controller → service → repository). Every database table has Row Level Security. The Content Security Policy is strict. Passwords use BCrypt, CSRF protection is on, and there are rate limits and lockouts. No secret is committed to Git. Locally, every student API answers in about 5–35 ms.

**What is weak.**
- Three new problems sit in the newest feature, Notes. One of them is a real access-control hole (CC-01).
- The privacy page promises something the code no longer does (CC-02).
- The new landing page uses React, Vite and Tailwind, which the project's own rules forbid without an approved decision (CC-03).
- Pages are heavy. Every student page pulls about 3 MB of 3D-robot code and never caches it (CC-04).
- The project is **not deployed**. The real AI provider has not been accepted in a live test, and some documents are out of date.

**Facts, inferences and recommendations.** In this report, **Confirmed** means I reproduced the problem or read it directly in code or output. **Suspected** means the code strongly suggests it but I did not reproduce it. **Recommendation** marks advice. Effort numbers are estimates.

---

## 2. Project objective and documented requirements

| Item | Source | What it says |
| --- | --- | --- |
| Objective | `docs/01-project-charter.md`, `AGENTS.md` §1 | A Java-first student management and learning platform for a Java assignment. The owner must be able to explain the Java in a viva. |
| Stack that must stay | `AGENTS.md` §2, `docs/04-TRD.md` | Java 21, Spring Boot, Maven, Supabase PostgreSQL + Storage, Flyway, JUnit/Testcontainers, plain HTML/CSS/JS. **Not approved:** Node/Express, frontend frameworks, bundlers, CSS frameworks, CDN libraries (`docs/04-TRD.md:40-41`). |
| Architecture | ADR 0001, 0003, 0004, 0005 | One Spring Boot deployable, server-side session cookies, AI behind a Java `AiProvider` interface. |
| Requirements | `docs/02-PRD.md` | 55 requirement rows: P0 (core), P1 (backlog), P2 (later). Listed in section 3. |
| Plan and progress | `docs/16-implementation-plan.md` | Phases 0–7. Owner tasks still open: T0-01/02/04/05, T3-01 (bucket), T5-06/T5-08 manual (real AI), T6-04 to T6-06 (deployment). |
| Later additions | ADR 0012 (PYQ), 0013 (complaints), 0014 (notes, quick assistant, robot) | Notes, robot and Quick Assistant come only from ADR 0014. They have **no PRD requirement** (finding CC-10). |
| Previous audit | `docs/21-security-review.md` | Owner items open as of that review: F4 (rotate credentials exposed during development) and F5 (unapproved hosted migration). |

---

## 3. Overall completion and how it was calculated

### 3.1 Method

- **Weights.** P0 = 3 and P1 = 1, the PRD's own priorities. P2 items are left out because the PRD marks them "later". Notes, robot and Quick Assistant are **not scored**, because no PRD requirement exists for them (they are listed in section 4).
- **Status scores** are the ones you asked for: verified 100 %, implemented but not fully verified 75 %, partial 50 %, stub/mock/broken 25 %, missing 0 %.
- **"Verified"** means it passed integration tests in this session *and* I exercised it (API call or page load) against a local server. Admin pages were not loaded in a browser this session, so they score 75 %.

### 3.2 P0 checklist (weight 3 each)

| # | Feature (PRD) | Evidence | Score |
| --- | --- | --- | --- |
| 1 | Login, logout, first password, roles, lockout, email identity (FR-AUTH-001…006) | `AuthFlowIT`, `LockoutIT`, `PasswordChangeIT`, `EndpointAccessIT` pass; live login and password change done locally | 100 % |
| 2 | Student dashboard (FR-STU-001) | IT pass; page loaded, API 25–73 ms | 100 % |
| 3 | Profile + own details and photo (FR-STU-002/003) | IT pass with in-memory storage; photo with real storage not exercised | 75 % |
| 4 | Settings (FR-STU-004) | Page loaded | 100 % |
| 5 | Privacy policy and terms (FR-LEG-001) | Pages exist, but the privacy text is wrong about AI storage (CC-02) | 50 % |
| 6 | Attendance + low-attendance flag (FR-ATT-001/002) | IT + calculator unit tests; page loaded | 100 % |
| 7 | Marks, results, CGPA (FR-MRK-001/002) | `MarksAndResultsApiIT`, `CgpaCalculatorTest`; page loaded | 100 % |
| 8 | Timetable (FR-TT-001) | IT; page loaded | 100 % |
| 9 | Notices (FR-NOT-001) | IT; page loaded | 100 % |
| 10 | Resource hub: browse, search, preview, bookmarks (FR-LIB-001…004) | `ResourceApiIT` with in-memory storage; T3-01 real-bucket check open | 75 % |
| 11 | Quick Exam (FR-QZ-001…004) | `QuizApiIT`; live start and submit (25 ms / 34 ms); no correct answers in the start response | 100 % |
| 12 | Progress and recommendations (FR-PRG-001/002) | `ProgressCalculatorTest`, IT; page loaded | 100 % |
| 13 | AI explain, summarise, generate questions (FR-AI-001…003) | Full pipeline works on the **mock**; Gemini adapter is unit-tested only; acceptance needs the real provider (T5-06) | 50 % |
| 14 | AI usage limits and failures (FR-AI-004) | `AiUsageServiceTest`, `AiApiIT`, `AiSwitchedOffIT` | 100 % |
| 15 | Admin: accounts, attendance, marks, results, subjects, timetable, notices, resources, question bank (FR-ADM-001…008) | Many ITs pass; admin pages not browser-tested this session | 75 % |
| 16 | Audit log writes (FR-ADM-009 P0 part) | `AuditRollbackIT`, `AdminAuditIT` | 100 % |
| 17 | Errors, empty and loading states (FR-UX-001) | Not checked page by page | 75 % |

P0 average = 1500 / 17 = **88.2 %**.

### 3.3 P1 checklist (weight 1 each)

| Feature | Status | Score |
| --- | --- | --- |
| FR-ATT-003 attendance calculator | Built (T7-01), not separately verified | 75 % |
| FR-NOT-002 notice document | Built, `NoticeDocumentIT` passes, real storage not exercised | 75 % |
| FR-LIB-005 report a resource | Not built (T7-06) | 0 % |
| FR-LIB-006 PYQ library | Built, `PyqIT` passes, page loaded | 75 % |
| FR-CMP-001…004 complaints | `ComplaintIT` passes, page loaded, API timed | 100 % |
| FR-PLN-001 study tasks | Not built (T7-02) | 0 % |
| FR-PLN-002 exam countdown | Not built (T7-02) | 0 % |
| FR-AI-005 summarise a library PDF | Not built (T7-04) | 0 % |
| FR-AI-006 flowcharts | Not built (T7-05) | 0 % |
| FR-ADM-009 audit viewer | Built, `AdminAuditIT` | 75 % |
| FR-ADM-010 CSV import | Built, `StudentImportIT` | 75 % |
| FR-UX-002 public landing page | Built, but with an unapproved stack (CC-03) | 75 % |

P1 average = 625 / 12 = **52.1 %**.

**Overall product completion = (1500 × 3 + 625 × 1) / (17 × 3 + 12) = 5125 / 63 ≈ 81 %.**

Uncertainty: about ±5 points. Admin UI, real storage and real AI were not exercised. They could move the score either way.

### 3.4 Readiness scores (judgement checklists)

| Area | Criteria (✓ met, ✗ not met) | Score |
| --- | --- | --- |
| Integration and testing | ✓ 526 automated tests pass · ✓ access tests cover 401/403/404 · ✓ DB role and RLS tests · ✗ no browser end-to-end tests · ✗ no JavaScript unit tests · ✗ real storage and real AI never exercised in tests · ✗ tests read `.env` (CC-11) | **≈ 65 %** |
| Performance and reliability | ✓ fast APIs (5–35 ms) · ✓ 4.3 s startup · ✗ ~3.1 MB per page, uncached and uncompressed · ✗ in-memory sessions and limits lost on restart · ✗ 1.4 s main-thread block on the first dashboard load · ✗ autosave never retries | **≈ 55 %** |
| Security and privacy | ✓ strict CSP, CSRF, BCrypt, lockout, rate limits, RLS everywhere, allow-list HTML sanitiser, no committed secrets · ✗ attachment access hole (CC-01) · ✗ roll-number-to-name lookup (CC-05) · ✗ privacy text wrong (CC-02) · ? credential rotation F4 unverified | **≈ 70 %** |
| Documentation and deployment | ✓ very complete specs, ADRs, runbook · ✗ several documents out of date (CC-10) · ✗ not deployed; Heroku vs Render unresolved · ✗ PRD has no Notes/robot requirement | **≈ 45 %** |

**After remediation (2026-10-23).** The same checklists, ticked again from the results in Part A (judgement, not measurement):

| Area | What changed | Score now |
| --- | --- | --- |
| Integration and testing | ✓ 547 Java tests + 13 Node tests pass · ✓ a smoke test follows every page's files and imports · ✓ tests ignore `.env` · ✓ JS unit tests exist and run in CI · ✗ rendering is still checked by hand · ✗ real storage and AI still never exercised | **≈ 80 %** |
| Performance and reliability | ✓ gzip and caching; later student pages 15–41 KB instead of 3.1 MB · ✓ robot starts after the page is usable · ✓ autosave retries and saves on page hide · ✗ in-memory sessions still lost on restart · ✗ memory on the real host not measured | **≈ 75 %** |
| Security and privacy | ✓ CC-01, CC-05, CC-18 fixed · ✓ privacy page accurate (CC-02, CC-15) · ✗ credential rotation (F4/CC-21) unverified · ✗ no automatic AI-log clean-up (stated honestly) | **≈ 85 %** |
| Documentation and deployment | ✓ docs match the code (CC-10, CC-19) · ✓ landing exception recorded (ADR 0015) · ✗ host not chosen, not deployed | **≈ 60 %** |

**On 2026-10-24** (judgement): integration and testing ≈ 85 % (every page browser-checked; real storage and live AI still not exercised in tests); security ≈ 85 % (unchanged: rotation still unconfirmed; the AI log clean-up now exists but is off); documentation and deployment ≈ 70 % (migration, memory and deployment plans written; host not chosen, not deployed). Performance unchanged.

---

## 4. Feature-by-feature status and integration map

How to read **Flow**: UI page → JS module → controller → service → repository/table or external service. "Connected" means every step exists and was exercised by tests or by me.

| Feature | Status | Implementation (main parts) | Flow | Dependencies | Tests (this run) | Performance (local) | Problems | Next step |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Login and accounts | Complete | `login.html`, `js/login.js`, `auth/AuthController`, `AuthService`, `LoginAttemptService`, `PasswordChangeRequiredFilter`, `common/security/*` | Connected | PostgreSQL `users` | `AuthFlowIT`, `LockoutIT`, `LoginRateLimitIT`, `PasswordChangeIT`, unit tests: pass | `/auth/me` 5 ms; password change 142 ms (BCrypt) | Per-session API limit resets on a new login (CC-18, low) | — |
| Landing page | Partial (stack issue) | `landing-frontend/` (React 19, Vite 6, Tailwind 4, three, framer-motion) → `static/index.html` + `js/landing/main-BSEaXpJF.js` | Connected: `/api/v1/auth/me` redirect in `App.tsx:23` | Node toolchain for builds | `PageRoutingIT` checks `/`; `StaticFrontendRulesTest` **exempts** `js/landing` (line 50) | 1.27 MB JS; FCP 804 ms cold, 92 ms warm; 553 ms long task on first load | CC-03, CC-04, CC-08 | Owner decision |
| Dashboard | Complete | `student/dashboard.html`, `student-dashboard.js`, `StudentController` `/me/dashboard`, `DashboardService` | Connected | attendance, results, timetable, notices, progress | `TimetableNoticesDashboardIT` | API 25–73 ms; page 3.1 MB; 1442 ms long task on first load (robot) | CC-04 | Lazy-load the robot |
| Profile and photo | Implemented, photo storage not verified | `profile.html`, `student-profile.js`, `StudentController`, `OwnProfileService`, `ProfilePhotoProcessor`, `SupabaseStorageClient` | Connected; photo goes to Supabase Storage | Storage | `OwnProfileIT`, `ProfilePhotoProcessorTest` | `/students/me` 8–10 ms | Real bucket not exercised | T3-01 check |
| Attendance | Complete | `attendance.html`, `student-attendance.js`, `AttendanceController`, `AttendanceService`, `AttendanceCalculator`; admin sheet and report import | Connected | — | `AttendanceApiIT`, `AttendanceReportIT`, `AttendanceCalculatorTest` | 11–16 ms | — | — |
| Marks and results | Complete | `results.html`, `MarksController`, `MarksService`, `ResultService`, `CgpaCalculator`, `GradeScale` | Connected | — | `MarksAndResultsApiIT`, `SubjectResultsIT`, `CgpaCalculatorTest`, `GradeScaleTest` | 8–16 ms | — | — |
| Timetable | Complete | `timetable.html`, `TimetableController`, `TimetableService`; admin CRUD with overlap check | Connected | — | `TimetableNoticesDashboardIT` | 10–12 ms | — | — |
| Notices | Complete | `notices.html`, `NoticeController`, `NoticeService`, `NoticeDocumentChecker`; admin publish/unpublish/document | Connected | Storage for documents | `NoticeDocumentIT` | 7–13 ms; LCP ~820–912 ms (largest element paints late) | — | Check what paints late |
| Resource hub and bookmarks | Implemented | `resources.html`, `resource.html`, `bookmarks.html`, `ResourceController`, `ResourceService`, `BookmarkService`, `PdfValidator`, `AdminResourceService` | Connected; downloads via 5-minute signed URLs | Storage | `ResourceApiIT`, `AdminResourceServiceTest`, `PdfValidatorTest` | 6–18 ms | Real bucket not exercised | T3-01 |
| Past papers (PYQ) | Implemented | `pyqs.html`, `student-pyqs.js`, `PyqController`, `PyqService`, `PyqPathParser`, `AdminPyqService` | Connected | Storage | `PyqIT`, parser tests | 8–17 ms | — | — |
| Complaints | Complete | `complaints.html` (student/admin), `ComplaintController`, `ComplaintService`, `AdminComplaintService`; owner kept in `complaint_owners` | Connected; "secure" complaints hide the owner from admins | — | `ComplaintIT` | 8–13 ms | The secure owner is still in the DB (by design, ADR 0013) | — |
| Quick Exam | Complete | `quiz.html`, `student-quiz.js`, `QuizController`, `QuizService`, `QuizEvaluationService`; admin question bank | Connected; server-side expiry | — | `QuizApiIT`, `QuizEvaluationServiceTest` | start 25 ms, submit 34 ms | — | — |
| Quiz history and progress | Complete | `quiz-history.html`, `progress.html`, `ProgressService`, `ProgressCalculator` | Connected | — | `ProgressCalculatorTest`, `QuizApiIT` | 9–21 ms | — | — |
| AI study assistant | Mock only (partial) | `assistant.html`, `student-assistant.js`, `AiController`, `AiStudyService`, `AiUsageService`, `PromptBuilder`, `AiResponseParser`, `GeneratedQuestionValidator`, `MockAiProvider`, `GeminiAiProvider` | **Simulated**: real provider never called | Gemini (when enabled) | `AiApiIT`, `AiStudyServiceTest`, `GeminiAiProviderTest` (fake HTTP), `AiArchitectureTest` | explain (mock) 15–24 ms; real latency not measured | ADR 0005 not updated for Gemini (CC-10) | T5-06 + B1–B20 behaviour run |
| Quick Assistant chat (robot) | Implemented (not in PRD) | `js/robot/quick-assistant.js`, `AssistantController`, `AssistantService`, tables `assistant_conversations`, `assistant_messages` (V15) | Connected on the mock | AI provider | `AssistantIT` | chat 20–24 ms (mock) | **Stores chat text**, which contradicts the privacy page (CC-02) | Fix the privacy text |
| Notes, folders, editor | Implemented, defects found | `notes.html`, `student-notes.js`, `js/notes/*` (20 modules, `rich-editor.js` 894 lines), `NoteController`, `NoteService`, `NoteHtmlSanitizer` (508 lines), `NoteFolderService`, `NoteLinkService` | Connected | — | `NotesIT` (32 tests), `NoteHtmlSanitizerTest`, `NoteTextTest` | list 8–17 ms; create 26 ms; save (PATCH) 13–22 ms | CC-07 autosave no retry; CC-12 emptying a note destroys it | Fix CC-06/07/12 |
| Note attachments | Implemented, **access defect** | `NoteAttachmentController`, `NoteAttachmentService`, `NoteImageProcessor`, `NoteFileChecker` | Connected; 302 to signed URL | Storage | `NotesIT`; probe confirmed CC-01, CC-06 | not measured (storage) | CC-01, CC-06 | Fix CC-01 first |
| Note sharing | Implemented, privacy defect | `NoteShareController`, `NoteShareService`, `note-shared.html`, `share-dialog.js` | Connected | — | `NotesIT`; probe confirmed CC-05 | — | CC-05 | Hide names until confirmed |
| Floating 3D robot | Implemented (not in PRD) | `js/robot/*`, `vendor/spline/*` (≈4.8 MB on disk), `robot/robot-scene.splinecode` | Loaded on every student page (`shell.js:128`) | WebAssembly decoder; CSP allows `wasm-unsafe-eval` | No automated test | 2.0 MB runtime + 368 KB scene + 275 KB wasm per page view; console error every page (CC-14) | CC-04, CC-14 | Load on demand and cache |
| Settings | Complete | `settings.html`, `settings.js`, `preferences.js` | Connected | — | Page loaded | 39–44 ms DCL | Password form lacks a username field (CC-20) | — |
| Admin area (14 pages) | Implemented, not browser-verified | `admin/*.html`, `js/admin-*.js`, `admin-kit.js`, `Admin*Controller`/`Admin*Service` | Connected (by tests) | — | `SubjectsAndStudentsAdminIT`, `StudentImportIT`, `AdminAuditIT`, many others | Not measured | — | Browser smoke test |
| Privacy and terms | Partial | `privacy.html`, `terms.html` | Static | — | `PageRoutingIT` | — | CC-02, CC-15 | Update text |

---

## 5. Source-code inventory

### 5.1 Files by type (tracked files only)

| Type | Files | Lines | Notes |
| --- | --- | --- | --- |
| Java (main) | 362 | ≈ 26,500 | 18 feature packages + `common`; 121 records (DTOs and small values) |
| Java (test) | 78 | ≈ 8,900 | 33 integration test classes (`*IT`), the rest unit tests and helpers |
| JavaScript (project-owned) | 71 | ≈ 9,000 | `js/` (45), `js/notes/` (20), `js/robot/` (6) |
| JavaScript (generated or vendor) | 8 landing bundles + 11 Spline files | ≈ 45,000 | **Not reviewed line by line.** 7 of the 8 bundles are stale (CC-08) |
| TypeScript/TSX (landing source) | 17 | ≈ 2,400 | `landing-frontend/src` |
| HTML | 37 | ≈ 4,400 | 18 student, 14 admin, 5 public pages |
| CSS | 12 project + 7 generated | ≈ 8,600 | `notes.css` alone is 2,351 lines |
| SQL migrations | 15 (V1–V15) | ≈ 1,200 | 37 tables, all with RLS |
| Config and deploy | `pom.xml`, `application.yml`, `application-prod.yml`, `Dockerfile`, `Procfile`, `render.yaml`, `system.properties`, `.github/workflows/ci.yml` | — | No `application-dev.yml`, although `.env` sets the `dev` profile |
| Documentation | 28 docs + 15 ADRs + root rule files | — | — |

### 5.2 Java packages

| Package | Classes | Responsibility |
| --- | --- | --- |
| `auth` | 17 | Login, password policy and change, lockout, bootstrap admin |
| `common.security` | 9 | Filter chain, CSP, rate limits, body limits, sessions |
| `common.config` / `error` / `web` / `storage` / `csv` / `image` | 13 / 7 / 3 / 3 / 1 / 1 | Settings, seeders, error format, request ID, storage client |
| `student` | 17 + 9 DTOs | Profiles, dashboard, provisioning, CSV import |
| `attendance` | 12 + 4 DTOs | Attendance sheets, reports, calculator |
| `assessment` | 18 + 9 DTOs | Assessments, marks, results, CGPA |
| `subject`, `timetable`, `notice` | 8 / 6 / 9 | Academic catalogue |
| `resource`, `pyq` | 16 / 13 | Study material and past papers |
| `quiz`, `progress` | 19 / 4 | Quick Exam and progress rules |
| `ai`, `assistant` | 12 + 11 DTOs + 7 provider + parse/prompt / 7 + 6 DTOs | AI features, Quick Assistant |
| `notes` | 28 + 25 DTOs | Notes, folders, attachments, links, sharing, sanitiser |
| `complaint`, `audit` | 13 / 7 | Complaints, audit log |

### 5.3 Function and method inventory

A **full** list of every Java method (1,174 declarations) and every browser JavaScript function (689), each with file and line, is in **Appendix A**. Record accessors (the automatic getters of Java records) are implicit and not listed. The tables below explain the business-critical ones.

| Function | File:line | Purpose | Inputs → output | Error handling | Tests | Status |
| --- | --- | --- | --- | --- | --- | --- |
| `SecurityConfig.securityFilterChain` | `common/security/SecurityConfig.java:57` | Deny-by-default URL rules, CSRF (cookie to header), sessions, headers | `HttpSecurity` → filter chain | 401/403 through `ApiSecurityErrorHandler` | `EndpointAccessIT`, `PageRoutingIT` | OK |
| `SecurityConfig.contentSecurityPolicy` | `SecurityConfig.java:175` | Builds the CSP; adds the storage host only if it is a valid `https://` host | `StorageProperties` → header string | Rejects malformed URLs | `ContentSecurityPolicyTest` | OK |
| `NoteHtmlSanitizer.sanitize` | `notes/NoteHtmlSanitizer.java` | Rebuilds note HTML from allow-lists of tags, attributes, styles and links | raw HTML → safe HTML | Drops unknown items; depth limit 100 | `NoteHtmlSanitizerTest` | OK (hand-written, so keep tests strong) |
| `NoteService.update` | `notes/NoteService.java:168` | Autosave and every toggle; shared editors may change only title and content | user, id, request → `NoteSaved` | 404 when not owner or editor; **deletes the note when it becomes empty** (`:225`) | `NotesIT` | Risk CC-12 |
| `NoteAttachmentService.linkFor` | `notes/NoteAttachmentService.java:135` | Signed link for an attachment | user, id → URL | 404 when not owner and not shared | `NotesIT` | **Defect CC-01** |
| `NoteAttachmentService.isSharedWith` | `NoteAttachmentService.java:210` | Decides "shared": a named recipient **or link sharing switched on** | attachment, studentId → boolean | — | — | **Root cause of CC-01** |
| `NoteShareService.addRecipient` | `notes/NoteShareService.java:88` | Adds a classmate by roll number or email; returns names | → `ShareResponse` | 400 when unknown | `NotesIT` | **Leak CC-05** |
| `QuizService` start/submit + `QuizEvaluationService` | `quiz/` | Random questions, shuffled options, server-side expiry, scoring | → attempt and result DTOs | 409 codes for expired and duplicate | `QuizApiIT`, `QuizEvaluationServiceTest` | OK |
| `AttendanceCalculator`, `CgpaCalculator`, `ProgressCalculator` | `attendance/`, `assessment/`, `progress/` | Core rules in plain Java with `BigDecimal` HALF_UP | numbers → results | Edge cases tested | Unit tests | OK |
| `AiUsageService` | `ai/AiUsageService.java` | Daily, per-minute, concurrency and global limits; kill switch | user → allow or throw | `AiQuotaExceededException` | `AiUsageServiceTest` | OK; repeats the logic of `AiRequestStatus.countsTowardLimits()` instead of calling it (CC-17) |
| `GeminiAiProvider.complete` | `ai/provider/GeminiAiProvider.java` | HTTP call; key only in the `x-goog-api-key` header | `AiRequest` → `AiCompletion` | Timeout → `AiTimeoutException`; misconfiguration errors | `GeminiAiProviderTest` (fake server) | Not tested live |
| `SupabaseStorageClient` | `common/storage/SupabaseStorageClient.java` | Upload, sign and delete; sends `apikey` and `Authorization` | path/bytes → URL | `StorageUnavailableException` | `SupabaseStorageClientTest` | Not tested live this session |
| `SlidingWindowRateLimiter.tryAcquire` | `common/security/SlidingWindowRateLimiter.java:28` | In-memory sliding window; rejected calls are not counted | key, time → boolean | — | Unit test | OK; lost on restart |
| `createAutosave` | `static/js/notes/autosave.js:17` | 800 ms debounced save queue | callbacks → controller | Sets status "error" and **does not retry** (`:70`) | None (no JS tests) | Risk CC-07 |
| `htmlToFragment` / `setHtml` | `static/js/notes/safe-html.js` | Inserts stored HTML under the CSP; moves styles through the CSS object model | HTML → DOM | — | None | OK |

### 5.4 Dead, unreachable or duplicate code

| Item | Evidence | Classification |
| --- | --- | --- |
| `js/index.js`, `js/landing-grid.js`, `css/landing.css` | Nothing references them since the React landing replaced `index.html` | **Likely unused** (confirmed by search) |
| `landing-frontend/index.original.html`, `landing-frontend/src/components/ui/demo.tsx` | Not a Vite input; `demo.tsx` is imported nowhere | Likely unused |
| 7 stale landing JS bundles + 6 stale CSS bundles | Only `main-BSEaXpJF.js` and `main-B-uTlbfi.css` are referenced in `static/index.html:12-13` | **Confirmed generated leftovers** (CC-08) |
| 13 Java methods never called from main code: `SubjectResult.getAttempt`, `AssistantMessage.getConversationId`, `NoteLink.getSourceNoteId`, `Notice.getDocumentSize`, `PyqBookmark.getPaperId`, `QuizAttemptAnswer.getAttemptId`, `User.getFailedLoginCount`, `User.getLockedUntil`, `CsvReader.getLine`, `NoteText.firstImage`, `SlidingWindowRateLimiter.trackedKeys`, `GeminiAiProvider.forTest`, `AiRequestStatus.countsTowardLimits` | Name appears once in `src/main` (the last 7 are used by tests) | Entity getters may be read by JPA or Jackson indirectly, so this is **uncertain**. `countsTowardLimits` is duplicate logic (CC-17). |
| JavaScript functions | All 689 names are referenced somewhere besides their definition | No dead JS found by name search |
| TODO/FIXME markers | None in project code (one "TODOs" heading is template text in `notes/templates.js:45`) | — |

---

## 6. Performance measurements

**Test conditions (all local, none production).** MacBook (Apple Silicon), Java 21.0.12. The built JAR ran on port 8099 with `-Xmx256m -XX:+UseSerialGC`, against a **throw-away PostgreSQL 17 container** with demo seed data. There was no `.env` and no storage key, and the AI was the mock. Pages were loaded in headless Chromium through Playwright at 1280×800 and at 375×812. Public static files were also timed on the running 8080 server, without logging in.

### 6.1 Build and startup

| Operation | Runs | Result |
| --- | --- | --- |
| `./mvnw -B verify` (compile, 526 tests, Spotless) | 1 | 90.6 s wall, 113 s CPU, peak RSS 1.11 GB (Maven JVM) |
| Application start (empty DB, 15 migrations) | 1 | **4.25 s**; Flyway 0.19 s |
| Memory after start, idle | 1 | **RSS 461 MB** with `-Xmx256m`. The Dockerfile comment says about 330 MB; on a 512 MB host this is tight (CC-13). |
| Landing TypeScript check (`tsc --noEmit`) | 1 | 1.1 s, no errors |

### 6.2 API response times (curl, warm session, 6 runs each, ms)

| Endpoint | Range | | Endpoint | Range |
| --- | --- | --- | --- | --- |
| `GET /auth/me` | 5 | | `GET /pyqs/overview` | 9–17 |
| `GET /students/me` | 8–10 | | `GET /complaints` | 8–13 |
| `GET /students/me/dashboard` | 25–74 (first call slowest) | | `GET /notes` | 8–17 |
| `GET /attendance/me` | 11–16 | | `GET /notes/folders` | 9–46 |
| `GET /marks/me` | 12–16 | | `GET /quiz-attempts/me` | 9–13 |
| `GET /results/me` | 8–11 | | `GET /progress/me` | 9–21 |
| `GET /timetable/me` | 10–13 | | `GET /ai/usage/me` | 7–23 |
| `GET /notices` | 7–13 | | `GET /assistant/conversations` | 8–12 |
| `GET /resources` | 9–18 | | `POST /notes` | 26 |
| `GET /bookmarks/me` | 9–11 | | `PATCH /notes/{id}` (autosave) | 13–22 |
| `POST /ai/explain` (mock) | 15–24 | | `POST /assistant/chat` (mock) | 20–24 |
| `POST /quiz-attempts` | 25 | | `POST /quiz-attempts/{id}/submit` | 34 |
| `POST /auth/change-password` | 142 (BCrypt) | | Public static on :8080 | 2–7 |

**Observation.** My 138 quick calls in under a minute hit the per-session limit of 120 per minute (HTTP 429), as designed. Normal browsing will not reach it. A heavy note-editing session with an 800 ms autosave could (CC-07).

### 6.3 Page loads (Playwright, ms; cold = first visit, warm = repeat visit)

| Page | DCL | FCP | LCP | Transferred | Long tasks |
| --- | --- | --- | --- | --- | --- |
| `/` landing (logged out) | 59 / 39 | 804 / 92 | 804 / 92 | **1.32 MB** both times | 553 + 81 ms cold |
| `/login.html` | 26 / 19 | 444 / 44 | 444 / 44 | 46 KB | — |
| Student dashboard | 47 / 184 | 556 / 220 | 588 / 252 | **3.11 MB** | **1442 ms** cold, 116 ms warm |
| Attendance, results, timetable, progress, bookmarks, profile, settings | 32–43 | 60–80 | 80–252 | 3.08–3.11 MB | ≤110 ms |
| Notices | 40–44 | 64–72 | **820–912** | 3.11 MB | — |
| Notes | 54–55 | 64 | 128–132 | 3.15 MB | — |
| Assistant | 39–42 | 60–64 | 148–792 | 3.13 MB | — |
| Quiz, quiz history, PYQs, resources, complaints | 28–54 | 60–76 | 96–144 | 3.11–3.13 MB | — |

**Bottleneck (confirmed).** On every student page, about 2.6 MB of the 3.1 MB is the robot: `vendor/spline/runtime.js` 2.0 MB, `robot-scene.splinecode` 368 KB, `draco_decoder.wasm` 275 KB, `draco_wasm_wrapper.js` 58 KB. The app sends **`Cache-Control: no-cache, no-store`** on every static file (Spring Security's default) and **no gzip** (`server.compression` is not enabled). So the browser downloads it again on each navigation. Gzip alone would shrink the landing bundle from 1.30 MB to 369 KB and the Spline runtime from 2.0 MB to 582 KB (measured with `gzip -c`). Heap was 30–52 MB per page. No horizontal scrolling was found at 375 px on the 6 student pages checked.

### 6.4 Not measured

| Area | Why not | How to measure |
| --- | --- | --- |
| Production or hosted latency, Supabase query times | Not deployed; I was told not to use the hosted DB | After deployment, run the same curl loop and Lighthouse against the public URL |
| Real file upload, preview and download | Would contact production storage | Run against a test bucket; time upload, signed URL and first byte |
| Real AI latency and failures | Would call a third-party API | Owner behaviour run B1–B20 with timings |
| Admin pages | Time budget; would need an admin session | Repeat the Playwright loop for `/admin/*.html` |
| CPU under load, concurrent users | No load tool approved | A small k6 or JMeter run against a local server |
| Low-end phone performance | No device or CPU throttling in this run | Lighthouse mobile with 4× CPU throttle |
| Editor typing latency | Needs interactive tracing | Chrome Performance panel while typing in a long note |

---

## 7. Bugs and glitches (issue register)

Unique root causes only. Severity uses the impact on users and data.

| ID | Sev. | Category | Feature / location | What is wrong (plain English) | Evidence / reproduction | User impact | Recommended correction | State | Now |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| **CC-01** | **High** | Security | Note attachments · `NoteAttachmentService.java:135-146, 210-219` | When link sharing is on for a note, **any** signed-in student can open its pictures and files. They need only the attachment number (numbers count up from 1), **not** the secret link. This also works while the note is in the Trash. | Probe test: stranger got 404 before sharing, **302 (signed link) after link sharing with no slug**, and 302 after the note was trashed | Private pictures or PDFs inside shared notes can be harvested by guessing numbers | Treat the slug as the key: allow link access only through `/notes/shared/{slug}` (e.g. a `?share=slug` on the file link, checked server-side), and refuse trashed notes. Add an IT. | Confirmed | **Fixed** `68e48ff` |
| **CC-02** | **High** | Security / Documentation | Privacy policy · `static/privacy.html:64` vs `V15__assistant_chats.sql`, `docs/14` §(exception), ADR 0014 line 45 | The privacy page says the text sent to the AI assistant is **not stored**. V15 stores Quick Assistant chats (up to six per student). | Read directly in code and docs | Students are told something untrue about their data. This matters more because ADR 0011 allows real student data | Update `privacy.html` (and `terms.html` if relevant) to describe chat storage, the six-chat limit and deletion | Confirmed | **Fixed** `f7a4f41` |
| **CC-03** | **High** | Architecture | Landing page · `landing-frontend/`, `static/js/landing/`, `docs/04-TRD.md:41`, `AGENTS.md` §2 | The landing page is React 19 + Vite + Tailwind + three.js + framer-motion. The project's rules forbid any frontend framework, bundler or CSS framework without an accepted decision record. None exists (ADR 0014 even rejects React for notes). `StaticFrontendRulesTest.java:50` exempts the bundle from the frontend rules. | Commit `4fd019e`; ADR list 0001–0014 | Viva risk ("why is there React in a Java project?"); a second toolchain (189 MB `node_modules`) to maintain; rules bypassed by a test exemption | Owner decides: either accept it in a new ADR (and update TRD, AGENTS, README) or rebuild the page in plain HTML/CSS/JS | Confirmed | **Fixed** `502b70c` (ADR 0015) |
| **CC-04** | **High** | Performance | Every student page · `static/js/shell.js:128`, `application.yml` (no `server.compression`), security headers | About 3.1 MB per student page view, 2.6 MB of it the robot. Not compressed, never cached. The first dashboard load blocks the main thread for 1.4 s. | Section 6.3 measurements; response headers `Cache-Control: no-cache, no-store` | Slow on phones and mobile data; heavy for a free host | Enable `server.compression` for JS/CSS/JSON/wasm; allow long caching for fingerprinted and `vendor/` files; load the robot only after idle or on click | Confirmed | **Fixed** `af614a6` |
| CC-05 | Medium | Security (privacy) | Note sharing · `NoteShareService.java:88-112` | Adding a "recipient" returns the full name for any roll number, so a student can look up classmates' names one by one. | Probe: adding roll numbers returned `200` with names | Personal data enumeration | Return a name only after the share is confirmed, or rate-limit and audit lookups | Confirmed | **Fixed** `bef4ee6` |
| CC-06 | Medium | Functional | Note sharing + attachments · `NoteService.java:232` (adopt only for owner), `NoteAttachmentService.java:150-158` | A picture added by a shared **editor** stays owned by the editor and attached to nothing, so the **owner** (and other readers) get 404 for it. | Probe: owner opening the editor's picture → **404** | Broken images in shared notes | Adopt editor uploads into the note (owned by the note owner, or allow access by note) | Confirmed | **Fixed** `933f06d` |
| CC-07 | Medium | Functional / Reliability | Notes autosave · `static/js/notes/autosave.js:70` | After a failed save (network, 429, server), the status shows "error" but nothing retries. If the student stops typing or closes the tab, the last edit is lost. | Code reading; 120/min session limit vs 800 ms debounce | Silent data loss in edge cases | Retry with backoff, honour `Retry-After`, flush on `visibilitychange`/`pagehide` | Suspected | **Fixed** `31b7ccc` |
| CC-08 | Low | Data / Maintenance | `static/js/landing/` (8 × 1.3 MB), `static/css/landing/` (7) | 7 old JS bundles and 6 old CSS bundles are committed and packed into the JAR, about 9 MB of dead weight. | `static/index.html:12-13` references only one pair | Bigger repo and JAR | Delete the unreferenced bundles; have the build clean `js/landing` | Confirmed | **Fixed** `82bd52b` |
| CC-09 | Medium | Testing | Frontend | There is no automated browser test and no JavaScript unit test. The notes editor (~3,000 lines across `js/notes`) is checked only by a static rules test. | Test inventory | Editor regressions go unnoticed | Add a small Playwright smoke suite (login, each page, save a note) — needs approval as a dependency | Confirmed | **Fixed** `c00a4f9` (rendering still manual) |
| CC-10 | Medium | Documentation | `AGENTS.md:11`, `docs/decisions/0005-…`, README hosting, `docs/02-PRD.md` | Several documents are out of date. AGENTS.md lists only V1–V13 (V15 exists) and says the real AI provider is not done (a Gemini adapter exists). ADR 0005 does not mention Gemini. README and TRD say Heroku while `render.yaml` and doc 23 target Render. Notes, robot and Quick Assistant have no PRD requirement. | Read directly | Confusion in the viva and for future agents | Update the documents together; add PRD rows for the ADR 0014 features | Confirmed | **Fixed** `1d8c180` |
| CC-11 | Medium | Testing | `application.yml:7` imports `.env`; `PostgresIntegrationTest.java:36-45` overrides only DB, domain and seed | Integration tests read the developer's `.env`. Storage is safely replaced by `InMemoryStorageClient` (`@Primary`), but `AI_PROVIDER`/`AI_API_KEY` are not overridden. If `.env` were set to `gemini` with a key, tests would call the real AI. | Code reading (today `.env` says `mock`, so it is not happening now) | Accidental paid/third-party calls; tests that pass only on one machine | Force `AI_PROVIDER=mock` (and empty Supabase settings) in the test base class | Suspected | **Fixed** `b92a2e3` |
| CC-12 | Medium | Data | `NoteService.java:219-227` | When an autosave leaves the owner's note empty (title and content), the note is **permanently deleted** with its attachments, shares and links. Selecting all text, deleting it and pausing 0.8 s is enough. | Code reading | Shares, links and files lost after an accidental clear | Only delete a brand-new never-saved draft; otherwise keep the empty note | Suspected | **Fixed** `ce27473` |
| CC-13 | Medium | Performance / Deployment | `Dockerfile` JAVA_OPTS, `render.yaml` plan free | The idle JVM used 461 MB RSS locally with `-Xmx256m`; the free 512 MB container leaves little headroom. Sessions, rate limits and the session registry live in memory, so every restart or free-tier sleep logs everyone out. | Section 6.1 | Crashes or logouts in production | Measure on the host; consider a JDBC session store later; document the trade-off | Confirmed (local) | **Open**: measure on the chosen host |
| CC-14 | Low | UI | Robot · `vendor/spline/runtime.js:8117` | "Missing property" is logged as a console error on every student page load. | Playwright console log, 40 entries | Noise; possibly a broken scene property | Check the scene export against the runtime version | Confirmed | **Open**: cause found, fix in the Spline editor |
| CC-15 | Low | Privacy | `privacy.html:95` | 90-day removal of AI usage logs is promised but not built (the page admits it). | Read directly; no scheduled job in code | Data kept longer than the policy aims | Add a scheduled cleanup | Confirmed | **Fixed** `f7a4f41` |
| CC-16 | Low | Maintenance | `js/index.js`, `js/landing-grid.js`, `css/landing.css`, `landing-frontend/index.original.html`, `ui/demo.tsx` | Files left behind by the landing rewrite. | Section 5.4 | Confusion | Remove after the owner's decision on CC-03 | Confirmed | **Fixed** `82bd52b` (`css/landing.css` kept) |
| CC-17 | Low | Code quality | `ai/AiRequestStatus.java:13`, `AiUsageService` | `countsTowardLimits()` exists but the usage service repeats the rule instead of calling it; 12 other methods are unused (section 5.4). | Search | Two places to change one rule | Use the enum method; delete truly dead getters | Confirmed | **Fixed** `448c84e` |
| CC-18 | Low | Security | `ApiRateLimitFilter.java:49-58` | The per-session API limit can be reset by logging in again (new session). The login limit (20 per IP per 10 min) caps this. | Observed: a fresh login got a full allowance | Weak limit for a determined user | Key the limit by user ID as well as session | Confirmed | **Fixed** `f4c14da` |
| CC-19 | Low | Documentation | `.env` sets `SPRING_PROFILES_ACTIVE=dev`; no `application-dev.yml` | The `dev` profile has no file. `AGENTS.md` §5 lists `application-dev.yml`. | File listing | Minor confusion | Add the file or fix the docs | Confirmed | **Fixed** `1d8c180` |
| CC-20 | Low | Accessibility | `student/settings.html` | The password form has no (hidden) username field; Chrome warns. | Console verbose message | Password managers work less well | Add a hidden `autocomplete="username"` field | Confirmed | **Fixed** `7826b40` |
| CC-21 | (High if open) | Security | `docs/21-security-review.md` F4, F5 | Credentials exposed during development were to be rotated. I cannot see whether that happened. | Doc only | Leaked credentials stay valid | Owner confirms rotation in the Supabase and AI consoles | Unverified | **Open**: owner must rotate |

**Now:** 18 of the 20 fixed (all 4 High); CC-13 and CC-14 open; CC-21 is the owner's.

**Count (20 unique):** High 4 (CC-01, 02, 03, 04) · Medium 8 (CC-05, 06, 07, 09, 10, 11, 12, 13) · Low 8 (CC-08, 14, 15, 16, 17, 18, 19, 20) · plus CC-21, which could not be verified.

---

## 8. Security, privacy and data integrity

**What is in place (verified in code and by passing tests):**

- Deny-by-default URL rules, with a role check on admin services again via `@PreAuthorize` (`SecurityConfig.java:40-108`).
- CSRF on every state-changing request, including login (`csrf.spa()`).
- Server-side sessions. Sessions are renewed on password change and ended on deactivation (`UserSessions`, `SessionRegistry`).
- BCrypt passwords, account lockout (5 failures / 15 min), login IP limit (20 / 10 min), API limit (120 / min per session), JSON body limit 64 KB (notes larger, up to 200,000 characters).
- CSP `default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self'`, `frame-ancestors 'none'`, `object-src 'none'`, `Referrer-Policy: no-referrer`, HSTS on HTTPS, Permissions-Policy. `'wasm-unsafe-eval'` and `blob:` are narrow allowances for the robot, recorded in ADR 0014.
- Stored note HTML is rebuilt from allow-lists on the server (`NoteHtmlSanitizer`). The browser never uses `innerHTML` to *write* (only `DOMParser` + `importNode` in `safe-html.js`). Inline scripts are blocked by the CSP anyway.
- Uploads: PDF signature and parse checks (`PdfValidator`, `NoteFileChecker`); images re-encoded to new JPEGs (`NoteImageProcessor`, `ProfilePhotoProcessor`); stored names cannot carry paths (`NotesIT` checks `../evil`).
- Private storage links are short-lived signed URLs (≤ 5 minutes; attachment redirects cached for only 3 minutes).
- Database: 37 tables, all with Row Level Security, runtime role `cc_app` separate from migrator `cc_migrator` (`DatabaseSecurityModelIT`). Audit log protected from truncate (`V2__academic.sql:187`).
- AI: the key travels only in the `x-goog-api-key` header; no personal data is added to prompts (`AiArchitectureTest`). The usage log holds numbers only.
- Secrets: `.env` was never committed (`git log --all -- .env` is empty). A pattern search for common key formats in tracked files found only fake test values. `.env` sets 19 of 21 names (`AI_API_KEY` and `AI_MODEL` are empty); values were not printed.

**Gaps:** CC-01 (attachment access), CC-05 (name lookup), CC-02 and CC-15 (privacy text and retention), CC-11 (tests may reach a real AI), CC-18 (limit reset), CC-21 (credential rotation unverified). The "secure" complaint hides the author from admins in the API, but the link still exists in `complaint_owners`. The ADR documents this, and it should be stated in the privacy page.

---

## 9. Architecture and code quality

| Topic | Assessment |
| --- | --- |
| Layering | Controllers are thin; services hold rules; repositories only in services. DTOs are records (121). Constructor injection, no Lombok, no `System.out` in main code. **Good, matches AGENTS.md §7.** |
| Core rules | `AttendanceCalculator`, `CgpaCalculator`, `ProgressCalculator`, `QuizEvaluationService` are plain Java with unit tests. Good for the viva. |
| Stack conformance | Backend: fully conforms. Frontend: student and admin pages conform (plain ES modules through `api.js`); **the landing page does not** (CC-03). Spline runtime is vendored, not CDN-loaded (allowed by ADR 0014). |
| Size and complexity hot spots | `NoteHtmlSanitizer` (508 lines), `NoteService` (421), `DemoAcademicSeeder` (426), `rich-editor.js` (894), `editor-commands.js` (855), `notes.css` (2,351). Notes is now the biggest and least-tested area of the frontend. |
| Error handling | One problem-details format from `GlobalExceptionHandler`; request IDs in logs; no stack traces in prod (`application-prod.yml`). |
| Database | Indexes present in most migrations (48 `create index` statements); foreign keys from notes tables to `student_profiles`. No destructive migration (only `drop not null` / `drop constraint` for a check change). |
| Logging and monitoring | Only `/actuator/health` exposed, with details hidden. No metrics or error-reporting service (acceptable for an assignment; note for production). |
| Accessibility and responsive layout | No horizontal overflow at 375 px on the pages checked; all `<img>` have `alt`; all buttons have accessible names on those pages. Full WCAG review not done. |
| Dependencies | Java: Spring Boot 4.1.1 parent, PDFBox, Testcontainers; no duplicates seen. Landing: 8 runtime and 8 dev npm packages; `npm audit` not run (it would contact the registry). |

**Recommendations (keep the stack):** enable compression and caching (no new dependency); lazy-load the robot; split `NoteService` sharing and editor rules if it grows further; add a Playwright smoke suite only after owner approval.

---

## 10. Unused files and cleanup candidates

Nothing was deleted, moved or renamed.

| Path | Type | Evidence of possible non-use | Confidence | Risk if deleted | Recommendation |
| --- | --- | --- | --- | --- | --- |
| `src/main/resources/static/js/landing/main-{BVjriLt5,Bk7So6qR,CnWSy_04,DAfbIKKw,DCjL0OYW,Dqu00XPI,KwMyD875}.js` | Generated (tracked) | Not referenced; only `main-BSEaXpJF.js` is | High | None | **Confirmed generated artifacts** — remove |
| `src/main/resources/static/css/landing/main-{BoRrgdIR,Bolb2vtP,CuYzeyTj,DLlMcf0j,DZ28hu9J,GsO8S_i-}.css` | Generated (tracked) | Only `main-B-uTlbfi.css` is referenced | High | None | Remove |
| `static/js/index.js`, `static/js/landing-grid.js`, `static/css/landing.css` | Project code | No reference from any page or module | High | Low (old landing) | Likely unused — remove after CC-03 decision |
| `landing-frontend/index.original.html` | Project file | Not a Vite input; nothing refers to it | Medium | Low | Likely obsolete |
| `landing-frontend/src/components/ui/demo.tsx` | Project code | Imported nowhere | High | None | Likely unused |
| `stitch_file_based_generator.zip` (9 MB) | Design export | Git-ignored; reference only | High | Lose design reference | Keep outside the repo folder |
| `target/` (incl. two JARs) | Build output | Git-ignored | High | None (rebuilt) | Leave; never commit |
| `landing-frontend/node_modules/` (189 MB) | Dependencies | Git-ignored | High | Need reinstall | Leave |
| `.DS_Store` | OS file | Git-ignored | High | None | Ignore |
| `private-data/`, `notes/`, `notices/` | Owner's real data | Git-ignored (ADR 0011) | — | **Real data loss** | **Required — keep, never commit** |
| `.agents/skills/*`, `.github/modernize/` | Agent and tooling config | Used by tooling, not by the app | Medium | Lose agent instructions | Uncertain — owner decides |
| `docs/design-prompts-stitch.md` | Documentation | Design prompts only | Medium | Low | Possibly obsolete; manual check |
| `src/main/resources/demo/quiz-questions.json` | Seed data | Used by `DemoQuizSeeder` | High | Breaks demo seeding | **Required — keep** |
| `vendor/spline/{physics,navmesh,howler,opentype,boolean,gaussian-splat-compression,ui,process}.js` | Vendor | Possibly loaded dynamically by `runtime.js`; only `runtime.js` and the draco files were seen in the page network log | Low | May break the robot | **Uncertain** — test the robot before removing any |

---

## 11. Test results and verification gaps

| Command / check | Where | Result |
| --- | --- | --- |
| `./mvnw -B verify` (Java 21, Colima Docker) | Isolated copy in the scratchpad, **without** `.env`, data folders or `target`, so the running 8080 server (which runs from `target/classes`) and hosted services were not touched | **BUILD SUCCESS** — surefire 263 tests, failsafe 263 tests, 0 failures/errors/skips; Spotless check passed; 1 min 29 s |
| Expected error logs during tests | same | Storage and duplicate-key errors in the log come from negative-path tests that passed |
| Probe IT `AuditProbeIT` (written only in the scratch copy) | same | Confirmed CC-01, CC-05, CC-06 (outputs in section 7) |
| `npx tsc --noEmit -p landing-frontend` | project (no output files) | Passed, no errors |
| Live API and page timings | JAR on :8099 + throw-away DB container `cc-audit-db` (removed afterwards) | Section 6 |
| Public static timing on :8080 | existing server, unauthenticated GETs only | 2–7 ms; server not restarted |

**Not verified:** admin pages in a browser; real Supabase Storage; real Gemini calls; production; `npm audit` and dependency vulnerability scan (network); visual regressions; keyboard-only use; the uncommitted changes in 23 frontend files were measured as they are on disk (they are part of what was served) but not reviewed line by line.

---

## 12. Prioritised improvement roadmap

Effort figures are **estimates**, not measurements.

| Priority | Item | Files / feature | Why it matters | Depends on | Order | Effort (est.) | Now |
| --- | --- | --- | --- | --- | --- | --- | --- |
| **P0** | Fix attachment access (CC-01) + IT | `NoteAttachmentService`, `NoteAttachmentController`, note pages that build file links, `NotesIT` | Broken access control on student files | — | 1 | 0.5–1 day | **Done** `68e48ff` |
| **P0** | Correct privacy page (CC-02, CC-15) | `privacy.html`, `docs/14` | Legal accuracy with real data | — | 2 | 1–2 h | **Done** `f7a4f41` |
| **P0** | Confirm credential rotation (CC-21) | Supabase / AI consoles | Leaked keys | Owner | 3 | 30 min (owner) | Open (owner) |
| P1 | Owner decision on landing stack (CC-03) | ADR, TRD, AGENTS, README or a plain-JS landing | Assignment rules and viva | Owner | 4 | 1 h (accept) / 2–4 days (rebuild) | **Done**: accepted, ADR 0015 `502b70c` |
| P1 | Fix shared-editor pictures (CC-06) and name lookup (CC-05) | `NoteService`, `NoteAttachmentService`, `NoteShareService` | Broken shared notes; privacy | CC-01 | 5 | 1 day | **Done** `933f06d`, `bef4ee6` |
| P1 | Real AI provider acceptance (T5-06, T5-08) | config, ADR 0005 | P0 AI requirements | Owner key, OQ-1 | 6 | 1 day + owner run | Open (owner) |
| P1 | Storage bucket check (T3-01) | Supabase | P0 resources and photos | Owner | 7 | 2–4 h | Open (owner) |
| P1 | Deploy (T6-04 to T6-06), pick Heroku or Render | `Procfile`/`Dockerfile`/`render.yaml`, docs 18/23 | Nothing is live | Above | 8 | 1–2 days | Open (owner) |
| P2 | Compression, caching, lazy robot (CC-04) | `application.yml`, `SecurityConfig` headers, `shell.js` | 3 MB per page | — | 9 | 0.5–1 day | **Done** `af614a6` |
| P2 | Autosave retry and safe "empty note" (CC-07, CC-12) | `autosave.js`, `NoteService.update` | Data loss risk | — | 10 | 0.5–1 day | **Done** `31b7ccc`, `ce27473` |
| P2 | Hermetic tests (CC-11) | `PostgresIntegrationTest` | Accidental external calls | — | 11 | 1 h | **Done** `b92a2e3` |
| P2 | Browser smoke tests (CC-09) | new test module | Frontend regressions | Dependency approval | 12 | 1–2 days | **Done** without a new dependency `c00a4f9`; rendering still manual |
| P2 | Memory headroom and session store (CC-13) | Dockerfile, docs | Stability on 512 MB | Deployment | 13 | 0.5 day | Open (after deployment) |
| P2 | Remaining P1 backlog (T7-02, T7-04, T7-05, T7-06) | Phase 7 | Feature completion | — | 14 | 1–3 days each | Open (not part of this remediation) |
| P3 | Docs refresh (CC-10, CC-19) | AGENTS, README, ADR 0005, PRD | Viva and onboarding | CC-03 decision | 15 | 2–4 h | **Done** `1d8c180` |
| P3 | Cleanup (CC-08, CC-16, CC-17) | section 10 | Smaller repo and JAR | CC-03 | 16 | 1–2 h | **Done** `82bd52b`, `448c84e` |
| P3 | Robot console error, username field, limit by user (CC-14, CC-20, CC-18) | robot, settings, rate limiter | Polish | — | 17 | 2–4 h | CC-20 `7826b40` and CC-18 `f4c14da` **done**; CC-14 open (Spline editor) |

---

## 13. Deployment readiness and remaining blockers

| Check | State |
| --- | --- |
| Production profile (`application-prod.yml`): HTTPS redirect, secure cookie, no stack traces | Present; `ProductionProfileIT` passes |
| JAR builds and starts | Yes (4.25 s locally) |
| Hosting target | **Not chosen.** Recommendation (docs/24 §7): Render Starter; `render.yaml` still says `plan: free`, to be changed by the owner. Heroku via `Procfile` remains possible |
| Hosted app | **Not deployed** (T6-04 to T6-06 open) |
| Database | Hosted Supabase is at **V19** (V16 to V18 applied 09:53 IST and V19 13:50 IST on 2026-10-24, after the owner's backup); question banks and the December exam papers loaded |
| Storage bucket | T3-01 not signed off |
| AI | Router over Groq, Ollama Cloud, Gemini and OpenRouter, live-tested locally (Part A2); mock is the default. Which providers may receive student text is the owner's decision (docs/24 §4) |
| CI | `.github/workflows/ci.yml` runs `./mvnw -B verify` and now also the Node browser-module tests on push to `main`; the landing bundle is committed, not built in CI (ADR 0015) |
| Memory | 461 MB idle locally at the audit, 401 MB after remediation, vs a 512 MB container (CC-13, still open; measurement plan in docs/24 §6a) |
| Sessions | In memory: restarts and free-tier sleep log users out |

**Blockers now (2026-10-24):** owner tasks only: T0-04 (GitHub security settings), credential rotation (CC-21), T3-01, T5-06, the backup and approval of V16 to V18, and T6-04 to T6-06. The code blockers CC-01 and CC-02 are fixed. Static files are now compressed and cached, which also helps on a small host.

---

## 14. Final verdict

*As found (original audit):* the backend was strong and well tested, but the Notes feature had an access hole (CC-01), the privacy page was untrue (CC-02), the React landing page had no approval (CC-03), and pages were heavy and uncached (CC-04).

*Now, after remediation (2026-10-23):*

- **What works:** everything listed before, plus Notes sharing and attachments with correct access rules, autosave that retries and saves on page hide, an accurate privacy page, compressed and cached static files, and a robot that loads after the page. Java tests (265 unit, 282 integration) and Node tests (13) pass. The committed state was verified on its own.
- **What is incomplete:** live AI provider acceptance, storage bucket sign-off, choosing a host and deploying, automated rendering checks, and the remaining P1 backlog (T7-02, T7-04 to T7-06, T7-09).
- **What is still risky:** credentials not yet confirmed rotated (CC-21); memory on a 512 MB host not measured (CC-13); the robot scene's console error (CC-14, cosmetic); and the unverified chance that `.env`'s `dev` profile seeds demo data into the database `.env` points at (Part A, A.3).
- **What to do next:** rotate the credentials, choose Heroku or Render and deploy, measure memory there, sign off storage and the AI provider, then fix the robot scene in the Spline editor.

*Now, after the completion run (2026-10-24):*

- **What works:** all of the above, plus the planner, report a resource, flowchart generation and the (switched-off) AI usage-log retention. Every approved P0 and P1 requirement is built and integration tested; 295 unit and 305 integration tests and 13 Node tests pass on the committed state; all 17 student and 14 admin pages were checked in a browser, desktop and phone width. Completion ≈ 94 %.
- **What is incomplete:** 12 requirements are not browser verified because they need the real storage bucket (8) or an approved AI provider (4); the orphaned-file clean-up (T7-09) is deferred; nothing is staging or production verified.
- **What is still risky:** the same as before (CC-21, CC-13, CC-14), plus HTTPS behind the host's proxy, which can only be checked on the first deploy.
- **Verdict:** **not ready for real students yet.** The repository work is done; the owner actions in [docs/26](docs/26-final-project-completion-report.md) §18 remain.

---

## Appendix A — Function and method inventory

Generated by scanning declarations (`grep`/`perl`) on 2026-10-23. Each entry is `name`:line. Overloads appear once per line. Java record accessors are implicit and not listed. Whether something is called was checked separately (section 5.4).

### A.1 Java methods (src/main/java, 1174 entries)

| File | Methods (name:line) |
| --- | --- |
| `CampusCompanionApplication.java` | `main`:11 |
| `ai/AiController.java` | `explain`:36, `summarize`:42, `generateQuiz`:48, `usage`:54 |
| `ai/AiRequestLog.java` | `getId`:74, `getStatus`:78 |
| `ai/AiRequestLogRepository.java` | `countByUserIdAndStatusInAndCreatedAtGreaterThanEqual`:10, `countByStatusInAndCreatedAtGreaterThanEqual`:14 |
| `ai/AiRequestStatus.java` | `countsTowardLimits`:13 |
| `ai/AiStudyService.java` | `summarize`:103, `generateQuiz`:125, `ask`:141, `ask`:162, `prompt`:192, `names`:202, `explain`:77 |
| `ai/AiUsageService.java` | `usageFor`:104, `startOfTomorrow`:114, `usedToday`:119, `takeSlot`:124, `startOfToday`:139, `startOfTomorrow`:143, `begin`:63, `end`:79, `record`:84 |
| `ai/parse/AiResponseParser.java` | `answerObject`:108, `jsonObject`:124, `draftOf`:144, `textOrNull`:157, `clean`:158, `textOrNull`:160, `clean`:161, `longText`:166, `requiredText`:180, `optionalText`:189, `texts`:201, `basis`:217, `textOrNull`:227, `clean`:232, `invalid`:246, `parseExplanation`:58, `parseSummary`:67, `parseQuestions`:88 |
| `ai/parse/GeneratedQuestionValidator.java` | `validate`:38, `fits`:71, `fourDifferentOptions`:75, `optionKey`:88 |
| `ai/prompt/PromptBuilder.java` | `build`:34, `clean`:57 |
| `ai/prompt/PromptLibrary.java` | `explain`:25, `summarize`:29, `quiz`:33, `quickChat`:38, `load`:42 |
| `ai/provider/AiProviderConfig.java` | `aiProvider`:17 |
| `ai/provider/AiProviderException.java` | `getKind`:23 |
| `ai/provider/GeminiAiProvider.java` | `textOf`:111, `forTest`:43, `complete`:56, `body`:95 |
| `ai/provider/MockAiProvider.java` | `summary`:103, `special`:110, `questions`:124, `special`:163, `between`:173, `firstWords`:178, `complete`:29, `replyFor`:41, `chatReply`:64, `explanation`:80, `demoExplanation`:95 |
| `assessment/AdminAssessmentController.java` | `create`:51, `update`:56, `marksSheet`:62, `saveMarks`:67, `resultsOf`:75, `saveResult`:80 |
| `assessment/AdminAssessmentService.java` | `sheet`:119, `classListOf`:124, `save`:139, `rejectInvalidRows`:169, `marksProblem`:196, `differs`:212, `classListOf`:221, `savedMarks`:227, `requireSubject`:232, `toResponse`:238, `list`:61, `create`:70, `update`:91 |
| `assessment/AdminResultService.java` | `resultsOf`:42, `save`:49, `snapshot`:81, `toResponse`:90 |
| `assessment/Assessment.java` | `getHeldOn`:103, `getAcademicYear`:107, `isPublished`:111, `update`:66, `getId`:83, `getSubjectId`:87, `getName`:91, `getType`:95, `getMaxMarks`:99 |
| `assessment/AssessmentRepository.java` | `findBySubjectIdInAndPublishedTrueOrderBySubjectIdAscIdAsc`:10, `existsBySubjectIdAndNameAndAcademicYearAndIdNot`:19 |
| `assessment/CgpaCalculator.java` | `cgpa`:29 |
| `assessment/GradeScale.java` | `points`:18, `passed`:23 |
| `assessment/Mark.java` | `record`:51, `getId`:58, `getStudentId`:62, `getAssessmentId`:66, `getMarksObtained`:70, `isAbsent`:74 |
| `assessment/MarksController.java` | `marks`:34, `results`:42 |
| `assessment/MarksService.java` | `marksFor`:49, `toMark`:82, `percent`:96 |
| `assessment/ResultService.java` | `resultsFor`:34, `toResponse`:54, `gradesBySemester`:63, `toResponse`:90 |
| `assessment/SemesterResult.java` | `getSgpa`:101, `getCreditsRegistered`:105, `getCreditsEarned`:109, `getResultStatus`:113, `record`:66, `isPublished`:85, `getId`:89, `getStudentId`:93, `getSemester`:97 |
| `assessment/SubjectResult.java` | `getStudentId`:54, `getSemester`:58, `getSubjectCode`:62, `getSubjectName`:66, `getCredits`:70, `getGrade`:74, `getAttempt`:78, `getExam`:82 |
| `assistant/AssistantController.java` | `chat`:32, `conversations`:38, `messages`:45, `delete`:51 |
| `assistant/AssistantConversation.java` | `getId`:48, `getStudentId`:52, `getTitle`:56, `getSource`:60, `getUpdatedAt`:64, `touch`:68 |
| `assistant/AssistantConversationRepository.java` | `newest`:21 |
| `assistant/AssistantHistory.java` | `owned`:39, `recent`:47, `all`:55, `list`:60, `save`:69, `delete`:94 |
| `assistant/AssistantMessage.java` | `getId`:45, `getConversationId`:49, `getRole`:53, `getContent`:57, `getCreatedAt`:61 |
| `assistant/AssistantService.java` | `conversations`:101, `messages`:110, `delete`:117, `ask`:127, `transcript`:170, `trimmed`:180, `respond`:184, `respond`:189, `chat`:79 |
| `attendance/AdminAttendanceController.java` | `sheet`:35, `save`:44 |
| `attendance/AdminAttendanceReportController.java` | `importReports`:34 |
| `attendance/AdminAttendanceService.java` | `rejectInvalidRows`:124, `savedRows`:147, `sheet`:59, `save`:81 |
| `attendance/AttendanceCalculator.java` | `percent`:20, `isBelowRequired`:30 |
| `attendance/AttendanceController.java` | `summary`:33, `history`:38 |
| `attendance/AttendanceRecord.java` | `correct`:71, `getId`:77, `getStudentId`:81, `getSubjectId`:85, `getClassDate`:89, `getPeriodNo`:93, `getStatus`:97 |
| `attendance/AttendanceRecordRepository.java` | `count`:27, `findByStudentIdAndSubjectIdOrderByClassDateDescPeriodNoDesc`:37, `findBySubjectIdAndClassDateAndPeriodNoAndStudentIdIn`:41 |
| `attendance/AttendanceReport.java` | `correct`:58, `getId`:64, `getStudentId`:68, `getFromDate`:72, `getToDate`:76, `getClassesHeld`:80, `getClassesAttended`:84 |
| `attendance/AttendanceReportImportService.java` | `checkPeriod`:118, `read`:137, `check`:158, `checkPercent`:226, `columnsOf`:258, `value`:300, `number`:309, `shortened`:324, `importReports`:75 |
| `attendance/AttendanceReportRepository.java` | `findByStudentIdAndFromDateAndToDate`:12 |
| `attendance/AttendanceService.java` | `historyFor`:107, `summaryFor`:52, `latestReport`:87 |
| `audit/AdminAuditController.java` | `search`:29, `actions`:39 |
| `audit/AdminAuditService.java` | `idOf`:107, `parse`:115, `blankToNull`:119, `search`:40, `respond`:48, `parse`:60, `parse`:61, `namesFor`:66, `isAccount`:85, `isStudent`:89, `subject`:95 |
| `audit/AuditAction.java` | `isPasswordEvent`:17 |
| `audit/AuditLog.java` | `getIpAddress`:100, `getCreatedAt`:104, `getId`:72, `getActorUserId`:76, `getAction`:80, `getEntityType`:84, `getEntityId`:88, `getOldValue`:92, `getNewValue`:96 |
| `audit/AuditLogRepository.java` | `count`:33, `search`:37 |
| `audit/AuditService.java` | `record`:34, `recordPasswordEvent`:48, `save`:55, `actorUserId`:59, `toJson`:68, `actorUserId`:73, `ipAddress`:81 |
| `auth/AccountDirectory.java` | `emailsOf`:23 |
| `auth/AccountPrincipal.java` | `getId`:31, `getRole`:35, `isActive`:44, `getAuthorities`:49, `getPassword`:54, `getUsername`:59, `eraseCredentials`:64 |
| `auth/AccountUserDetailsService.java` | `loadUserByUsername`:21 |
| `auth/AdminAccounts.java` | `firstAdminId`:19 |
| `auth/AdminBootstrap.java` | `run`:51, `looksLikeEmail`:88 |
| `auth/AuthController.java` | `csrf`:42, `login`:50, `me`:60, `changePassword`:66, `logout`:76 |
| `auth/AuthService.java` | `login`:49, `describe`:75, `authenticate`:86, `invalidCredentials`:94 |
| `auth/EmailNormalizer.java` | `normalise`:14 |
| `auth/LoginAttemptService.java` | `assertNotBlocked`:37, `recordFailure`:53, `recordSuccess`:65 |
| `auth/PasswordChangeRequiredFilter.java` | `doFilterInternal`:43, `isAllowed`:62, `mustChangePassword`:74 |
| `auth/PasswordPolicy.java` | `violations`:33, `localPart`:55 |
| `auth/PasswordService.java` | `changePassword`:59 |
| `auth/StudentAccountService.java` | `createStudentAccount`:45, `deactivate`:56, `activate`:64, `resetPassword`:74, `studentAccount`:82, `randomPassword`:89 |
| `auth/UnknownEmailAttempts.java` | `isBlocked`:21, `recordFailure`:26, `size`:40, `removeStale`:45 |
| `auth/User.java` | `recordSuccessfulLogin`:107, `changePassword`:114, `setTemporaryPassword`:123, `deactivate`:131, `activate`:135, `rename`:139, `getId`:143, `getLoginEmail`:147, `getPasswordHash`:151, `getRole`:155, `getDisplayName`:159, `isActive`:163, `isMustChangePassword`:167, `getFailedLoginCount`:171, `getLockedUntil`:175, `getLastLoginAt`:179, `getCreatedAt`:183, `getUpdatedAt`:187, `onCreate`:78, `onUpdate`:85, `isLocked`:90, `recordFailedLogin`:98 |
| `auth/dto/ChangePasswordRequest.java` | `toString`:19 |
| `auth/dto/LoginRequest.java` | `toString`:26 |
| `common/config/AiProperties.java` | `toString`:28 |
| `common/config/BootstrapProperties.java` | `toString`:10 |
| `common/config/CampusTime.java` | `today`:18, `now`:22 |
| `common/config/ClockConfig.java` | `clock`:12 |
| `common/config/DemoAcademicSeeder.java` | `seedIfEmpty`:164, `actAs`:191, `seedSubjects`:199, `seedTimetable`:214, `seedAttendance`:233, `present`:255, `present`:272, `seedMarks`:278, `assessment`:307, `marks`:314, `seedResults`:320, `tenths`:346, `seedNotices`:350, `publish`:352, `publish`:359, `publish`:366, `publish`:373, `publish`:380, `publish`:387, `publish`:406, `subjectRow`:423 |
| `common/config/DemoQuizSeeder.java` | `read`:123, `seedIfEmpty`:70, `toRequest`:96 |
| `common/config/DevDataSeeder.java` | `run`:65, `seedingRequested`:79 |
| `common/config/StorageProperties.java` | `toString`:10 |
| `common/csv/CsvReader.java` | `getLine`:31, `read`:37, `addUnlessEmpty`:89 |
| `common/error/ApiException.java` | `getCode`:19, `getProperties`:27 |
| `common/error/ErrorCode.java` | `status`:43, `title`:47 |
| `common/error/GlobalExceptionHandler.java` | `handleExceptionInternal`:105, `validationProblem`:124, `problem`:132, `toFieldError`:146, `codeFor`:153, `plainDetailFor`:163, `handleApiException`:37, `handleAccessDenied`:50, `handleConstraintViolation`:59, `handleUnexpected`:71, `problem`:75, `handleMethodArgumentNotValid`:86 |
| `common/error/ValidationException.java` | `getErrors`:22 |
| `common/image/ExifOrientation.java` | `of`:17, `isExif`:34, `tagIn`:45, `number`:73 |
| `common/security/ApiRateLimitFilter.java` | `shouldNotFilter`:40, `doFilterInternal`:46 |
| `common/security/ApiSecurityErrorHandler.java` | `commence`:37, `handle`:53, `isApiRequest`:71, `write`:77 |
| `common/security/LoginRateLimitFilter.java` | `shouldNotFilter`:37, `doFilterInternal`:42 |
| `common/security/RequestBodyLimitFilter.java` | `read`:117, `read`:126, `count`:134, `isFinished`:142, `isReady`:147, `setReadListener`:152, `shouldNotFilter`:51, `doFilterInternal`:60, `limitFor`:77, `getInputStream`:97 |
| `common/security/SecurityConfig.java` | `passwordEncoder`:139, `authenticationManager`:145, `sessionRegistry`:154, `httpSessionEventPublisher`:160, `securityContextRepository`:166, `contentSecurityPolicy`:175, `securityFilterChain`:57 |
| `common/security/SessionLogin.java` | `renewAfterPasswordChange`:37, `start`:46, `end`:61 |
| `common/security/SlidingWindowRateLimiter.java` | `tryAcquire`:34, `retryAfter`:48, `trackedKeys`:57, `forgetIdleKeys`:62, `dropExpired`:72 |
| `common/security/UserSessions.java` | `register`:22, `expireOthers`:27, `expireAll`:36 |
| `common/storage/StorageUnavailableException.java` | `super`:10 |
| `common/storage/SupabaseStorageClient.java` | `downloadParameter`:116, `authenticate`:128, `call`:133, `requireConfigured`:144, `baseUrl`:153, `objectUrl`:157, `builderWithTimeouts`:53, `upload`:62, `call`:63, `delete`:77, `call`:78, `signedUrl`:89 |
| `common/web/PageResponse.java` | `of`:13 |
| `common/web/RequestIdFilter.java` | `doFilterInternal`:28 |
| `complaint/AdminComplaintController.java` | `list`:33, `one`:42, `handle`:47 |
| `complaint/AdminComplaintService.java` | `find`:100, `respond`:104, `info`:174, `list`:60, `respond`:64, `one`:73, `handle`:78 |
| `complaint/Complaint.java` | `getTitle`:102, `getDescription`:106, `isShowIdentity`:110, `getStatus`:114, `getAdminResponse`:118, `getRespondedAt`:122, `getCreatedAt`:126, `handle`:76, `isOnPublicBoard`:86, `getId`:90, `getType`:94, `getCategory`:98 |
| `complaint/ComplaintController.java` | `raise`:40, `board`:48, `mine`:61, `one`:69, `withdraw`:75, `support`:82, `unsupport`:88 |
| `complaint/ComplaintOwner.java` | `getComplaintId`:32, `getStudentId`:36 |
| `complaint/ComplaintRepository.java` | `boardNewest`:22, `boardTop`:38, `count`:55, `forAdmin`:72 |
| `complaint/ComplaintService.java` | `mine`:107, `respond`:111, `one`:120, `withdraw`:131, `support`:145, `unsupport`:160, `onBoard`:169, `isOwner`:177, `respond`:188, `raise`:61, `board`:84, `respond`:98 |
| `complaint/ComplaintSupport.java` | `getComplaintId`:32, `getStudentId`:36, `equals`:53, `hashCode`:60 |
| `notes/Note.java` | `getSource`:101, `getTags`:105, `setTags`:109, `isPinned`:113, `setPinned`:117, `isArchived`:121, `setArchived`:125, `getDeletedAt`:129, `setDeletedAt`:133, `getCreatedAt`:137, `getUpdatedAt`:141, `touch`:145, `getId`:69, `getStudentId`:73, `getFolderId`:77, `setFolderId`:81, `getTitle`:85, `setTitle`:89, `getContent`:93, `setContent`:97 |
| `notes/NoteAssembler.java` | `own`:37, `respond`:47, `sharedWithMe`:54, `one`:77, `sharedIds`:81, `respond`:91 |
| `notes/NoteAttachment.java` | `getCreatedAt`:100, `getId`:64, `getStudentId`:68, `getNoteId`:72, `setNoteId`:76, `getStoragePath`:80, `getName`:84, `getMimeType`:88, `getSizeBytes`:92, `getKind`:96 |
| `notes/NoteAttachmentController.java` | `list`:42, `upload`:50, `delete`:64, `file`:75 |
| `notes/NoteAttachmentRepository.java` | `forStudent`:28 |
| `notes/NoteAttachmentService.java` | `delete`:122, `linkFor`:135, `adopt`:151, `deleteForNotes`:166, `removeFromStorage`:174, `copyAttachments`:190, `isSharedWith`:210, `respond`:222, `displayName`:236, `safeName`:247, `upload`:71, `displayName`:88, `list`:97 |
| `notes/NoteController.java` | `duplicate`:101, `links`:106, `addLink`:111, `removeLink`:119, `list`:45, `create`:58, `bulk`:64, `linkTargets`:70, `one`:78, `update`:83, `delete`:91 |
| `notes/NoteFileChecker.java` | `matchesPicture`:100, `matchesDocument`:109, `pdfOpens`:122, `isText`:133, `startsWith`:151, `mismatch`:163, `invalid`:167, `prepare`:72 |
| `notes/NoteFolder.java` | `setPosition`:101, `touch`:105, `getId`:57, `getStudentId`:61, `getName`:65, `setName`:69, `getColor`:73, `setColor`:77, `getIcon`:81, `setIcon`:85, `getParentId`:89, `setParentId`:93, `getPosition`:97 |
| `notes/NoteFolderController.java` | `list`:35, `create`:41, `reorder`:48, `update`:56, `delete`:64 |
| `notes/NoteFolderService.java` | `create`:104, `colorOrBlue`:121, `cleanIcon`:122, `update`:129, `reorder`:182, `delete`:206, `ownedFolderId`:219, `foldersById`:226, `quickNotesFolder`:236, `seedDefaults`:254, `wouldCycle`:271, `previews`:283, `toInstant`:300, `toInstant`:306, `respond`:319, `colorOrBlue`:331, `cleanIcon`:335, `list`:81, `respond`:95 |
| `notes/NoteHtmlSanitizer.java` | `attributes`:123, `styleValues`:150, `sanitize`:165, `isNameEnd`:231, `endOfTag`:236, `skipPast`:254, `openTag`:264, `isTextAttribute`:309, `closeTag`:313, `parseAttributes`:327, `cleanValue`:376, `cleanLink`:422, `cleanStyle`:432, `appendText`:453, `escapeAttribute`:475, `unescape`:495 |
| `notes/NoteImageProcessor.java` | `upright`:102, `encode`:142, `invalid`:161, `toJpeg`:41, `decode`:48, `try`:54, `fitted`:83 |
| `notes/NoteLink.java` | `getId`:45, `getStudentId`:49, `getSourceNoteId`:53, `getTargetType`:57, `getTargetId`:61 |
| `notes/NoteLinkRepository.java` | `findBySourceNoteIdAndTargetTypeAndTargetId`:11, `findByIdAndSourceNoteIdAndStudentId`:14 |
| `notes/NoteLinkService.java` | `targetExists`:139, `resourceOrNull`:147, `read`:155, `list`:57, `add`:63, `remove`:86, `targets`:94 |
| `notes/NoteRepository.java` | `count`:28, `left`:43, `substring`:44, `trash`:63 |
| `notes/NoteService.java` | `one`:123, `create`:138, `update`:168, `delete`:244, `duplicate`:262, `bulk`:287, `destroy`:328, `sharedWithMe`:337, `accessTo`:360, `cleanContent`:377, `cleanTags`:387, `searchTerm`:400, `orderFor`:411, `list`:81 |
| `notes/NoteShare.java` | `getId`:51, `getNoteId`:55, `getOwnerId`:59, `getSlug`:63, `getLinkAccess`:67, `setLinkAccess`:71 |
| `notes/NoteShareController.java` | `get`:31, `setLinkAccess`:36, `addRecipient`:44, `removeRecipient`:52, `stop`:61, `openByLink`:67 |
| `notes/NoteShareRecipient.java` | `getId`:48, `getNoteId`:52, `getOwnerId`:56, `getRecipientId`:60, `getPermission`:64, `setPermission`:68 |
| `notes/NoteShareService.java` | `removeRecipient`:117, `stop`:126, `openByLink`:141, `owned`:159, `rollNoOf`:166, `read`:178, `get`:58, `setLinkAccess`:69, `addRecipient`:88 |
| `notes/NoteSpecifications.java` | `inView`:17, `inFolder`:38, `withTag`:51, `matching`:57, `titleMatching`:66, `byIds`:71, `escape`:75 |
| `notes/NoteText.java` | `plain`:24, `snippet`:42, `snippet`:46, `firstImage`:57, `hasContent`:66, `hasAttachmentMarkup`:71 |
| `notes/dto/NoteSaved.java` | `removed`:13, `of`:9 |
| `notice/AdminNoticeController.java` | `list`:45, `create`:53, `update`:59, `publish`:65, `unpublish`:71, `attachDocument`:78, `removeDocument`:85, `documentLink`:90, `delete`:96 |
| `notice/AdminNoticeService.java` | `attachDocument`:111, `removeDocument`:136, `documentLink`:149, `removeQuietly`:157, `find`:167, `branch`:172, `list`:46, `create`:55, `branch`:61, `update`:69, `branch`:75, `publish`:82, `unpublish`:90, `delete`:96 |
| `notice/Notice.java` | `publish`:102, `unpublish`:109, `getId`:115, `getTitle`:119, `getBody`:123, `getCategory`:127, `getTargetBranch`:131, `getTargetSemester`:135, `getDueDate`:139, `getStatus`:143, `getPublishedAt`:147, `attachDocument`:152, `removeDocument`:162, `getDocumentPath`:171, `getDocumentType`:175, `getDocumentSize`:179, `update`:86 |
| `notice/NoticeController.java` | `list`:30, `document`:40, `one`:48 |
| `notice/NoticeDocumentChecker.java` | `startsWith`:130, `invalid`:142, `check`:51, `read`:68, `try`:69, `redraw`:98 |
| `notice/NoticeRepository.java` | `findVisible`:27, `findVisibleById`:42, `findDeadlines`:55 |
| `notice/NoticeService.java` | `recentFor`:110, `deadlinesFor`:122, `audienceOf`:135, `toResponse`:143, `visibleTo`:50, `oneVisibleTo`:64, `documentLink`:77, `linkTo`:87, `downloadName`:98 |
| `progress/ProgressCalculator.java` | `of`:33, `levelFor`:47 |
| `progress/ProgressController.java` | `mine`:22 |
| `progress/ProgressService.java` | `recommendationsFrom`:109, `average`:132, `progressFor`:55, `recommendationsFor`:64, `unitsOf`:69 |
| `pyq/AdminPyqController.java` | `list`:46, `upload`:62, `link`:74, `update`:79, `delete`:85 |
| `pyq/AdminPyqService.java` | `link`:121, `update`:129, `delete`:143, `checked`:152, `refuseIfSittingTaken`:179, `describe`:200, `removeStoredFile`:212, `blankToNull`:221, `list`:65, `upload`:89 |
| `pyq/PyqAssembler.java` | `toResponses`:16, `toResponse`:20, `monthName`:27, `monthName`:41, `downloadName`:48 |
| `pyq/PyqBookmark.java` | `getStudentId`:32, `getPaperId`:36, `equals`:54, `hashCode`:61 |
| `pyq/PyqController.java` | `overview`:37, `subjects`:42, `search`:49, `saved`:64, `link`:72, `save`:78, `unsave`:85 |
| `pyq/PyqPaper.java` | `describe`:109, `isPublished`:123, `getId`:127, `getBranch`:131, `getSubjectName`:135, `getExamYear`:139, `getExamMonth`:143, `getExamKind`:147, `getMidNumber`:151, `getRegulation`:155, `getCourseCode`:159, `getVariant`:163, `getStoragePath`:167, `getSizeBytes`:171, `getStatus`:175, `getDownloadCount`:179, `getCreatedAt`:183 |
| `pyq/PyqPaperRepository.java` | `findSameSitting`:109, `and`:29, `and`:30, `and`:31, `search`:33, `count`:58, `and`:62, `and`:63, `and`:64, `subjects`:68, `count`:77 |
| `pyq/PyqPathParser.java` | `parse`:31, `monthOf`:77, `problem`:86 |
| `pyq/PyqSearchTerms.java` | `of`:14, `pattern`:26 |
| `pyq/PyqService.java` | `saved`:103, `link`:116, `save`:130, `unsave`:137, `published`:145, `page`:152, `sortOf`:166, `branchOrNull`:187, `blankToNull`:191, `overview`:53, `subjects`:67, `search`:74, `branchOrNull`:89, `blankToNull`:90, `blankToNull`:93 |
| `quiz/AdminQuizQuestionController.java` | `list`:38, `create`:49, `update`:56 |
| `quiz/AdminQuizQuestionService.java` | `update`:100, `write`:120, `asksTheSame`:133, `unitOf`:146, `subjectOf`:156, `toResponse`:160, `list`:50, `toResponse`:68, `anyQuestionExists`:84, `create`:89 |
| `quiz/AnsweredQuestion.java` | `isCorrect`:6 |
| `quiz/QuizAttempt.java` | `getStudentId`:100, `getSubjectId`:104, `getUnitId`:108, `getQuestionCount`:112, `getStartedAt`:116, `getExpiresAt`:120, `getSubmittedAt`:124, `getStatus`:128, `getScore`:132, `isInProgress`:75, `isOverdue`:80, `submit`:84, `expire`:91, `getId`:96 |
| `quiz/QuizAttemptAnswer.java` | `mark`:52, `getAttemptId`:57, `getQuestionId`:61, `getPosition`:65, `getSelectedOption`:69, `isCorrect`:73 |
| `quiz/QuizAttemptExpiredException.java` | `super`:13 |
| `quiz/QuizAttemptRepository.java` | `findByStudentIdAndStatusAndExpiresAtBefore`:28 |
| `quiz/QuizController.java` | `start`:39, `history`:46, `result`:54, `submit`:60 |
| `quiz/QuizEvaluationService.java` | `evaluate`:31 |
| `quiz/QuizNotEnoughQuestionsException.java` | `super`:13, `getProperties`:22 |
| `quiz/QuizQuestion.java` | `describe`:105, `asksTheSame`:113, `optionText`:132, `getId`:141, `getSubjectId`:145, `getUnitId`:149, `getQuestionText`:153, `getCorrectOption`:157, `getExplanation`:161, `getDifficulty`:165, `getSource`:169, `getStatus`:173, `getCreatedAt`:177, `write`:84 |
| `quiz/QuizQuestionRepository.java` | `search`:29 |
| `quiz/QuizService.java` | `submit`:141, `result`:182, `history`:197, `answeredBy`:238, `submittedPercents`:244, `percent`:251, `ownAttempt`:257, `closeOverdue`:265, `expire`:274, `resultOf`:279, `optionsInOrder`:292, `percent`:304, `questionsById`:316, `questionIdsOf`:321, `optionsInOrder`:325, `shuffledOptions`:334, `start`:92 |
| `resource/AdminResourceController.java` | `list`:45, `upload`:56, `update`:66, `delete`:72 |
| `resource/AdminResourceService.java` | `displayName`:104, `update`:117, `blankToNull`:123, `delete`:142, `checked`:150, `savePaperDetails`:175, `removeStoredFile`:189, `displayName`:201, `blankToNull`:211, `list`:61, `upload`:82, `blankToNull`:99 |
| `resource/Bookmark.java` | `getResourceId`:37, `equals`:55, `hashCode`:62 |
| `resource/BookmarkRepository.java` | `bookmarkedAmong`:28 |
| `resource/BookmarkService.java` | `list`:38, `add`:52, `remove`:62 |
| `resource/LearningResource.java` | `describe`:103, `isPublished`:119, `getId`:123, `getTitle`:127, `getDescription`:131, `getSubjectId`:135, `getUnitId`:139, `getResourceType`:143, `getStoragePath`:147, `getOriginalFileName`:151, `getFileSizeBytes`:155, `getStatus`:159, `getCreatedAt`:163 |
| `resource/LearningResourceRepository.java` | `and`:26, `lower`:27, `search`:29, `in`:44, `and`:48, `lower`:49, `searchPapers`:51, `count`:82 |
| `resource/PdfValidator.java` | `opensAsPdf`:114, `sha256`:123, `validate`:68, `startsWithSignature`:83, `opensInTime`:98 |
| `resource/QuestionPaper.java` | `update`:41, `getResourceId`:47, `getExamYear`:51, `getExamType`:55, `getMaxMarks`:59 |
| `resource/ResourceAssembler.java` | `toResponses`:38, `toResponse`:86 |
| `resource/ResourceController.java` | `removeBookmark`:105, `search`:42, `subjectFolders`:59, `one`:64, `downloadUrl`:70, `papers`:78, `bookmarks`:90, `addBookmark`:98 |
| `resource/ResourceService.java` | `papers`:108, `searchText`:121, `forRevision`:127, `get`:132, `downloadUrl`:143, `readable`:154, `page`:161, `studentIdOf`:172, `sortOf`:182, `searchText`:195, `search`:57, `searchText`:77, `subjectFolders`:86 |
| `resource/ResourceType.java` | `isPaper`:12 |
| `resource/dto/ResourceUpdateRequest.java` | `metadata`:21 |
| `student/AdminImportController.java` | `students`:34 |
| `student/AdminStudentController.java` | `search`:43, `provision`:56, `get`:61, `update`:66, `deactivate`:72, `activate`:78, `resetPassword`:84 |
| `student/AdminStudentService.java` | `activate`:109, `resetPassword`:127, `find`:134, `snapshot`:138, `blankToNull`:148, `search`:50, `blankToNull`:59, `blankToNull`:61, `get`:68, `update`:74, `deactivate`:94 |
| `student/DashboardService.java` | `dashboardFor`:60, `attendanceCard`:64, `resultsCard`:65, `attendanceCard`:72, `resultsCard`:82 |
| `student/OwnProfileService.java` | `removePhoto`:111, `photoUrl`:125, `own`:134, `removeQuietly`:138, `snapshot`:146, `blankToNull`:156, `update`:60, `blankToNull`:66, `blankToNull`:67, `setPhoto`:89 |
| `student/ProfileLinks.java` | `email`:27, `link`:43, `example`:82 |
| `student/ProfilePhotoProcessor.java` | `turned`:112, `encode`:132, `orientationOf`:145, `startsWith`:149, `invalid`:161, `process`:44, `decode`:60, `try`:66, `squareOf`:93 |
| `student/StudentController.java` | `ownProfile`:46, `updateOwnProfile`:52, `setPhoto`:61, `removePhoto`:73, `photoUrl`:79, `dashboard`:84 |
| `student/StudentDirectory.java` | `idsByRollNumber`:23, `rollNumbersOf`:33 |
| `student/StudentImportService.java` | `toRequest`:144, `value`:147, `value`:148, `value`:149, `number`:150, `value`:151, `number`:152, `checkRollNoIsNew`:157, `columnsOf`:178, `hasValueWithoutColumn`:217, `value`:226, `number`:235, `shortened`:250, `importStudents`:66, `read`:78, `check`:99 |
| `student/StudentProfile.java` | `update`:112, `updateOwnDetails`:131, `getPersonalEmail`:151, `getLinkedinUrl`:155, `getGithubUrl`:159, `getPortfolioUrl`:163, `getSkills`:167, `setPhotoPath`:171, `onCreate`:176, `onUpdate`:183, `getId`:187, `getUser`:191, `getRollNo`:195, `getFullName`:199, `getBranch`:203, `getCurrentSemester`:207, `getSection`:211, `getAdmissionYear`:215, `getPhone`:219, `getCreatedAt`:223, `getUpdatedAt`:227, `getBio`:231, `getPhotoPath`:235 |
| `student/StudentProfileRepository.java` | `findByBranchAndCurrentSemesterAndSectionOrderByRollNo`:23, `findByBranchAndCurrentSemesterOrderByRollNo`:27, `and`:41, `lower`:42, `count`:46, `and`:50, `lower`:51, `search`:53 |
| `student/StudentProfileService.java` | `findByRollNo`:103, `studentsByIds`:111, `toContext`:117, `anyStudentExists`:129, `rollNoOf`:31, `ownProfile`:42, `currentStudent`:68, `studentsOfClass`:80, `studentsOfSemester`:90, `findStudent`:97 |
| `student/StudentProvisioningService.java` | `emailFor`:100, `blankToNull`:104, `provision`:65 |
| `student/StudentResponses.java` | `forAdmin`:13 |
| `student/StudentRosterValidator.java` | `violations`:25, `checkRollNo`:41, `checkText`:55, `checkRange`:65 |
| `student/dto/TemporaryPasswordResponse.java` | `toString`:11 |
| `subject/AdminSubjectController.java` | `list`:40, `create`:48, `update`:53, `addUnit`:60, `updateUnit`:65, `deleteUnit`:71 |
| `subject/AdminSubjectService.java` | `normalise`:104, `toResponse`:108, `list`:33, `create`:40, `update`:53, `normalise`:62, `addUnit`:70, `updateUnit`:81, `deleteUnit`:93 |
| `subject/Subject.java` | `update`:53, `getId`:63, `getCode`:67, `getName`:71, `getBranch`:75, `getSemester`:79, `getCredits`:83, `isActive`:87 |
| `subject/SubjectController.java` | `list`:30, `units`:37 |
| `subject/SubjectRepository.java` | `findAllFiltered`:36 |
| `subject/SubjectService.java` | `toResponse`:110, `visibleTo`:40, `unitsOf`:47, `subjectsOf`:58, `activeIdsOfSemester`:67, `anySubjectExists`:72, `find`:76, `byIds`:81, `findUnit`:88, `unitsByIds`:98 |
| `subject/SubjectUnit.java` | `update`:44, `getId`:49, `getSubject`:53, `getUnitNo`:57, `getTitle`:61 |
| `timetable/AdminTimetableController.java` | `list`:38, `create`:47, `update`:52, `delete`:58 |
| `timetable/AdminTimetableService.java` | `time`:102, `blankToNull`:110, `toResponse`:114, `list`:36, `create`:43, `blankToNull`:54, `update`:60, `blankToNull`:71, `delete`:77, `checked`:84 |
| `timetable/TimetableController.java` | `timetable`:28 |
| `timetable/TimetableEntry.java` | `getSection`:100, `getDayOfWeek`:104, `getStartTime`:108, `getEndTime`:112, `getSubjectId`:116, `getRoom`:120, `getFacultyName`:124, `update`:67, `getId`:88, `getBranch`:92, `getSemester`:96 |
| `timetable/TimetableEntryRepository.java` | `findByBranchAndSemesterAndSectionOrderByDayOfWeekAscStartTimeAsc`:12, `findByBranchAndSemesterAndSectionAndDayOfWeekOrderByStartTimeAsc`:16, `findFiltered`:28, `count`:40, `overlaps`:46 |
| `timetable/TimetableService.java` | `timetableFor`:38, `toResponse`:51, `format`:54, `format`:55, `format`:62 |

### A.2 Browser JavaScript functions (src/main/resources/static/js, excluding the generated landing bundle; 689 entries)

| File | Functions (name:line) |
| --- | --- |
| `js/admin-attendance.js` | `choice`:21, `option`:22, `updateCount`:39, `showSheet`:44, `all`:58, `showRows`:70, `start`:116, `importReport`:140 |
| `js/admin-audit.js` | `actionLabel`:25, `valuesText`:31, `changeCell`:40, `table`:53, `load`:68, `start`:90 |
| `js/admin-complaints.js` | `who`:17, `open`:25, `load`:86, `filterBar`:119 |
| `js/admin-kit.js` | `input`:8, `textarea`:12, `select`:17, `numberSelect`:26, `checkbox`:32, `field`:38, `valuesOf`:51, `clearErrors`:65, `showFieldErrors`:79, `dialogShell`:97, `openForm`:113, `confirmAction`:152, `showOnce`:175, `dataTable`:202, `pager`:227, `button`:231, `rowAction`:246, `attempt`:253, `hidePageError`:271, `semesterOptions`:279, `whereOf`:285, `importProblems`:297 |
| `js/admin-marks.js` | `academicYear`:16, `assessmentForm`:23, `marksSheet`:48, `loadAssessments`:120, `start`:144 |
| `js/admin-notices.js` | `noticeForm`:14, `openDocument`:55, `setPublished`:64, `remove`:70, `audience`:79, `load`:86 |
| `js/admin-pyqs.js` | `sleep`:19, `sitting`:21, `metadataOf`:27, `paperForm`:42, `setHidden`:78, `remove`:91, `openFile`:110, `importFolder`:120, `addProblem`:164, `load`:245, `filterBar`:289 |
| `js/admin-questions.js` | `subjectOptions`:19, `fillUnits`:23, `statusChoices`:37, `questionForm`:45, `withStatus`:90, `setStatus`:95, `statusAction`:105, `load`:112, `filterBar`:154, `changed`:158, `start`:179 |
| `js/admin-resources.js` | `subjectOptions`:18, `metadataOf`:23, `chosenFile`:37, `fillUnits`:53, `resourceForm`:67, `showExamFields`:103, `withStatus`:118, `setHidden`:123, `openFile`:133, `remove`:150, `place`:169, `load`:173, `filterBar`:220, `start`:236 |
| `js/admin-results.js` | `resultForm`:15, `showStudent`:36, `search`:63 |
| `js/admin-students.js` | `status`:14, `profileFields`:27, `addStudent`:39, `importStudents`:57, `edit`:112, `resetPassword`:126, `setActive`:143, `table`:155, `load`:183, `filterBar`:205, `changed`:207 |
| `js/admin-subjects.js` | `subjectForm`:12, `units`:38, `unitForm`:48, `remove`:66, `refresh`:78, `load`:111, `filterBar`:147 |
| `js/admin-timetable.js` | `subjectOf`:15, `entryForm`:17, `remove`:45, `dayCard`:55, `load`:77, `filterBar`:95 |
| `js/api.js` | `readCookie`:25, `csrfToken`:32, `request`:39, `homeFor`:118, `messageFor`:130 |
| `js/change-password.js` | `validate`:44 |
| `js/dom.js` | `h`:16, `setAttributes`:32, `append`:49, `icon`:65, `fill`:77, `tag`:84, `stagger`:90, `percent`:96, `formatDate`:113, `formatToday`:118, `daysUntil`:123, `formatSize`:130, `toast`:136 |
| `js/forms.js` | `showBanner`:5, `setFieldError`:18, `clearErrorsOnInput`:32, `setBusy`:43, `wirePasswordToggle`:49 |
| `js/index.js` | `showLanding`:9 |
| `js/landing-grid.js` | `lerp`:21, `rgba`:22, `resize`:30, `pointAt`:40, `draw`:80, `segment`:114, `animate`:167, `startAnimation`:194, `stopAnimation`:202, `onMotionPreferenceChange`:207, `onPointerMove`:219, `onClick`:232, `onPointerLeave`:244, `onVisibilityChange`:253 |
| `js/login.js` | `validate`:35 |
| `js/mascot.js` | `startMascot`:8, `follow`:16 |
| `js/motion.js` | `motionReduced`:12, `reveal`:33, `enableTilt`:44, `countUp`:71, `show`:72, `step`:83, `ring`:98, `circle`:106, `bar`:131, `segmented`:142, `moveThumb`:147, `choose`:155, `swap`:187, `enter`:198, `leave`:220, `finish`:226 |
| `js/note-shared.js` | `showMissing`:14, `show`:21 |
| `js/notes/attachments-view.js` | `attachmentsView`:18, `load`:23, `draw`:35 |
| `js/notes/autosave.js` | `createAutosave`:17, `setStatus`:31, `run`:36, `persist`:75, `schedule`:83 |
| `js/notes/drawing-dialog.js` | `openDrawingDialog`:20, `whiten`:32, `refresh`:82, `pointFrom`:100, `end`:127, `clear`:134, `insertDrawing`:140 |
| `js/notes/editor-commands.js` | `createCommands`:14, `run`:16, `settle`:33, `range`:45, `inside`:46, `startNode`:52, `wrapSelection`:67, `unwrapSelected`:100, `toggleInline`:118, `format`:128, `setInlineStyle`:133, `unsetInlineStyle`:149, `highlight`:168, `unsetHighlight`:185, `setLineHeight`:189, `setTextAlign`:198, `setParagraph`:215, `toggleHeading`:223, `toggleBlockquote`:231, `toggleCodeBlock`:242, `setHorizontalRule`:261, `toggleList`:269, `leaveTaskItem`:279, `taskItemToParagraph`:286, `taskListToParagraphs`:319, `toggleTaskList`:327, `newTaskList`:358, `newTaskItem`:364, `convertListToTasks`:372, `setTaskChecked`:406, `splitTaskItem`:418, `leaveBlockquote`:457, `backspaceInTaskItem`:480, `indentList`:502, `sinkTaskItem`:515, `liftTaskItem`:530, `currentLink`:560, `applyLink`:564, `insertBlock`:603, `insertImage`:626, `insertFile`:638, `insertTable`:655, `newCell`:671, `cellParts`:679, `addRow`:684, `deleteRow`:697, `addColumn`:711, `deleteColumn`:728, `toggleHeaderRow`:746, `removeTable`:763, `deleteTable`:770, `moveInTable`:778, `clearFormatting`:796 |
| `js/notes/editor-core.js` | `isElement`:15, `rangeIn`:20, `selectRange`:29, `placeCaret`:36, `closestIn`:44, `currentTextBlock`:56, `leafBlocksIn`:74, `saveSelection`:88, `restoreSelection`:101, `pathOf`:116, `nodeAt`:126, `lengthOf`:134, `prepareForEditing`:144, `normalizeBlocks`:175, `hasContent`:197, `shapeTaskItem`:250, `ensureTrailingParagraph`:292, `serialize`:326, `dropZeroWidthAndEmptyMarks`:341, `isEmptyParagraph`:364, `tidy`:368, `renameLegacyTags`:384, `swap`:416, `cleanElement`:426, `cleanPastedHtml`:486, `appendClean`:493 |
| `js/notes/editor-toolbar.js` | `createToolbar`:33, `button`:40, `divider`:52, `row`:54, `swatch`:74, `textButton`:77, `setMore`:146, `away`:176, `escape`:179, `setState`:185, `setUploading`:218, `destroy`:224 |
| `js/notes/folder-card.js` | `countText`:23, `folderCard`:26, `paint`:65, `open`:81 |
| `js/notes/folder-colors.js` | `paletteFor`:20 |
| `js/notes/folder-dialog.js` | `openFolderDialog`:14, `refresh`:52 |
| `js/notes/link-picker.js` | `openLinkPicker`:16, `hint`:22, `schedule`:25, `search`:31 |
| `js/notes/modal.js` | `openModal`:11 |
| `js/notes/note-card.js` | `noteCard`:14, `cancelPress`:24 |
| `js/notes/note-editor-pane.js` | `createEditorPane`:28, `menuItem`:83, `closeMenu`:88, `away`:93, `act`:97, `openMenu`:107, `openMoveMenu`:123, `place`:124, `refreshHeader`:143, `loadLinks`:161, `drawRelated`:173, `close`:203, `onKeydown`:210, `destroy`:217 |
| `js/notes/note-text.js` | `stripHtml`:7, `toSnippet`:22, `firstImageUrl`:34, `checklistProgress`:40, `hasAttachmentMarkup`:48, `isEmptyDocument`:53, `formatBytes`:58, `timeAgo`:66 |
| `js/notes/notes-api.js` | `listNotes`:9, `getNote`:21, `createNote`:23, `updateNote`:29, `deleteNote`:35, `restoreNote`:37, `duplicateNote`:39, `bulkNoteAction`:41, `emptyTrash`:46, `listFolders`:53, `createFolder`:57, `updateFolder`:59, `deleteFolder`:61, `reorderFolders`:64, `getShare`:68, `setLinkAccess`:72, `addShareRecipient`:76, `removeShareRecipient`:80, `stopSharing`:84, `openSharedNote`:88, `listLinks`:92, `addLink`:96, `removeLink`:100, `searchLinkTargets`:104, `listAttachments`:112, `deleteAttachment`:116, `uploadNoteAttachment`:122 |
| `js/notes/rich-editor.js` | `createRichEditor`:30, `setContent`:77, `getHTML`:87, `emit`:92, `scheduleEmit`:102, `updatePlaceholder`:107, `queryState`:118, `computeState`:126, `inside`:128, `scheduleState`:157, `uploadAndInsert`:167, `uploadMany`:185, `openLinkBar`:222, `closeLinkBar`:229, `applyLink`:234, `selectIsland`:248, `drawIslandTools`:263, `positionIslandTools`:307, `afterIslandChange`:317, `editAlt`:324, `removeIsland`:332, `move`:349, `up`:355, `edgeCell`:373, `move`:394, `up`:398, `ensureColgroup`:409, `setColumnWidth`:426, `plain`:439, `textBeforeCaret`:442, `clearBlockText`:453, `topLevel`:458, `blockRule`:461, `run`:466, `ruleRule`:486, `inlineRule`:507, `autolink`:542, `leaveCodeBlockOnTripleEnter`:744, `onSelectionChange`:802, `onResize`:811, `setEditable`:871 |
| `js/notes/safe-html.js` | `hideStyles`:17, `showStyles`:22, `htmlToFragment`:27, `setHtml`:42 |
| `js/notes/share-dialog.js` | `openShareDialog`:12, `load`:17, `apply`:27, `draw`:42 |
| `js/notes/symbols.js` | `symbol`:8 |
| `js/notes/templates.js` | `TASK`:5 |
| `js/parts.js` | `loadInto`:21, `cardHead`:43, `empty`:52, `categoryTag`:73, `dueText`:79, `dueTag`:94, `scoreKind`:109, `toggleBookmark`:119, `bookmarkButton`:127, `show`:129, `typeTag`:150, `resourcePlace`:155, `examText`:164, `resourceCard`:173, `isWide`:200, `openSheet`:212 |
| `js/preferences.js` | `reduceMotionChosen`:6, `applyPreferences`:16, `setReduceMotion`:20 |
| `js/robot/floating-panel.js` | `maxSize`:13, `clampSize`:20, `readSize`:28, `clampToViewport`:41, `openingPosition`:52, `createFloatingPanel`:70, `paint`:82, `endDrag`:107, `endResize`:151, `onWindowResize`:180 |
| `js/robot/quick-assistant.js` | `openQuickAssistant`:15, `refresh`:59, `drawLog`:63, `drawHistory`:82, `loadHistory`:99, `openChat`:108, `startNewChat`:121, `send`:131, `close`:158, `onKeydown`:166 |
| `js/robot/quick-note.js` | `openQuickNote`:17, `close`:75, `onKeydown`:87 |
| `js/robot/robot-scene.js` | `robotFallback`:23, `mountScene`:33 |
| `js/robot/robot.js` | `styleSheet`:22, `clamp`:28, `storedPosition`:37, `mountRobot`:48, `paintSize`:82, `place`:86, `setPosition`:89, `paintState`:99, `pulse`:106, `anchor`:113, `openNote`:115, `openAssistant`:133, `finish`:181, `showBadge`:238 |
| `js/robot/taps.js` | `createMultiTapDetector`:15, `clearPending`:19, `reset`:26, `registerTap`:31 |
| `js/settings.js` | `looksRight`:36 |
| `js/shell.js` | `initialsOf`:8, `show`:15, `applyAvatarPhoto`:22, `showAvatarPhoto`:67, `initials`:69, `syncActiveNavigation`:153 |
| `js/student-assistant.js` | `readPrefill`:23, `textField`:35, `choice`:61, `subjectAndUnit`:81, `fillUnits`:89, `paragraphs`:175, `part`:179, `explanationBody`:183, `summaryBody`:193, `questionsBody`:203, `resultCard`:225, `waitingCard`:246, `failureOf`:256, `failureCard`:275, `quotaText`:288, `blockedNote`:293, `formCard`:303, `ask`:311, `refreshUsage`:353, `renderSide`:362, `render`:385 |
| `js/student-attendance.js` | `totals`:15, `plan`:28, `pct`:35, `hero`:37, `plannerCard`:60, `whatIfCard`:83, `update`:87 |
| `js/student-bookmarks.js` | `countText`:14, `emptyState`:18, `remove`:24, `render`:36 |
| `js/student-complaints.js` | `statusTag`:27, `supportButton`:32, `show`:34, `card`:56, `pager`:90, `move`:91, `load`:102, `select`:119, `drawControls`:126, `change`:141, `raiseDialog`:149, `refresh`:170, `invalid`:174, `check`:180 |
| `js/student-dashboard.js` | `greeting`:10, `attendanceCard`:34, `resultsCard`:56, `tile`:57, `classState`:72, `classesCard`:82, `deadlinesCard`:99, `noticesCard`:115, `recommendationsCard`:131 |
| `js/student-notes.js` | `activeFolder`:56, `rootFolders`:57, `showFolderBrowser`:58, `failed`:59, `refreshFolders`:63, `fetchNotes`:73, `scheduleFetch`:91, `selectView`:96, `openFolder`:105, `upsertNote`:114, `dropNote`:121, `toggleSelect`:126, `runBulk`:133, `noteAction`:149, `guarded`:186, `saveFolder`:188, `removeFolder`:204, `commitFolderDrop`:219, `openPane`:241, `closePane`:262, `toggleTemplates`:293, `awayTemplates`:312, `closeTemplates`:315, `onEmptyTrash`:326, `render`:347, `renderSidebar`:352, `renderHeading`:407, `renderMain`:413, `renderBulk`:429, `button`:434, `emptyState`:459, `noteList`:463, `renderBody`:486, `openNoteById`:516, `folderDialog`:526, `closeContextMenu`:530, `dismissContext`:536, `showContextMenu`:540, `item`:542, `sep`:544 |
| `js/student-notices.js` | `matches`:19, `officialDocument`:25, `detail`:59, `card`:72, `renderList`:100, `toolbar`:108 |
| `js/student-profile.js` | `show`:36, `shortLink`:41, `renderLinks`:45, `renderSkills`:56, `render`:63, `optional`:81, `showPhoto`:98, `load`:128, `showBioCount`:187 |
| `js/student-progress.js` | `quizLink`:20, `rounded`:21, `hero`:23, `bySubject`:42, `subjectCard`:53, `recommendation`:79, `render`:96 |
| `js/student-pyqs.js` | `kindLabel`:26, `sittingText`:32, `libraryBranchOf`:37, `readRecent`:43, `rememberOpened`:51, `openPaper`:62, `downloadPaper`:105, `saveButton`:120, `show`:122, `paperRow`:144, `pager`:163, `button`:164, `query`:179, `subjectCard`:199, `loadSubjects`:217, `loadSubjectPapers`:238, `loadPapers`:263, `loadSaved`:279, `showRecent`:294, `option`:315, `select`:319, `drawFilters`:326, `change`:333, `drawBranches`:354, `drawViews`:372, `load`:389, `render`:403 |
| `js/student-quick-history.js` | `toggle`:16, `remove`:33, `draw`:48 |
| `js/student-quiz-history.js` | `stat`:14, `row`:24, `pager`:43, `button`:47, `loadRows`:58 |
| `js/student-quiz.js` | `clock`:25, `subjectOf`:26, `setupView`:29, `showCount`:37, `fillUnits`:45, `notEnough`:62, `begin`:78, `practiceCard`:131, `recentCard`:152, `remember`:176, `startTest`:181, `runTest`:195, `secondsLeft`:208, `tick`:210, `testView`:227, `answeredCount`:250, `renderQuestion`:252, `answer`:296, `go`:307, `askToSubmit`:313, `finish`:349, `forget`:379, `showResult`:385, `stateOf`:395, `textOf`:408, `resultView`:410, `countOf`:412, `renderReview`:421, `reviewCard`:424, `askAi`:445, `openResult`:501, `openSetup`:511, `savedTest`:532 |
| `js/student-resource.js` | `linkFor`:15, `reasonFor`:20, `actions`:24, `showBookmark`:26, `details`:78, `paper`:94, `preview`:101, `show`:104, `missing`:136, `load`:141 |
| `js/student-resources.js` | `option`:16, `field`:20, `query`:34, `filtered`:47, `pager`:51, `button`:52, `nothingFound`:70, `folders`:84, `openSubject`:116, `subjectView`:126, `load`:156, `refresh`:191, `fillUnits`:196, `clearFilters`:211 |
| `js/student-results.js` | `gradingPolicy`:31, `gradeSheet`:57, `row`:64, `semesterCard`:98, `semestersView`:121 |
| `js/student-timetable.js` | `isNow`:23, `dayClasses`:24, `rowOf`:25, `place`:26, `prepare`:29, `headerChips`:40, `block`:54, `week`:66, `dayCard`:85, `renderDay`:94, `dayView`:102, `legend`:127 |

### A.3 Landing frontend (landing-frontend/src, TypeScript/React)

| File | Components and functions (name:line) |
| --- | --- |
| `src/App.tsx` | `App`:16 |
| `src/components/landing/college-section.tsx` | `CollegeCard`:44, `CollegeSection`:136 |
| `src/components/landing/feature-carousel-3d.tsx` | `FeatureCarousel3D`:84, `handleResize`:104, `step`:132, `handlePointerDown`:173, `handlePointerMove`:191, `handlePointerUp`:226, `handlePrev`:265, `handleNext`:270, `handleDotClick`:275, `currentNorm`:276, `handleCardClick`:283, `handleKeyDown`:289 |
| `src/components/landing/habit-faq-scroller.tsx` | `FaqCard`:40, `HorizontalScroller`:60, `FaqSection`:99 |
| `src/components/landing/hover-footer.tsx` | `HoverFooter`:6 |
| `src/components/landing/kinetic-grid.tsx` | `lerpN`:36, `lerpColor`:40, `KineticGrid`:54, `age`:210, `drawSeg`:254, `avg`:260, `setSize`:420, `onMouseMove`:461, `onClick`:469, `loop`:483 |
| `src/components/landing/privacy-section.tsx` | `PrivacyCard`:49, `PrivacySection`:141 |
| `src/components/ui/demo.tsx` | `GlobeDemo`:3 |
| `src/components/ui/globe-utils/scene-container.tsx` | `SceneContainer`:17 |
| `src/components/ui/globe-utils/use-shadcn-theme.ts` | `useShadcnTheme`:14 |
| `src/components/ui/globe.tsx` | `fibonacciSphere`:17, `ResponsiveCamera`:50, `halfFovRad`:60, `GlobeMesh`:73, `groupRef`:88, `positions`:90, `Globe`:171, `rotationState`:180, `handlePointerDown`:192, `handlePointerMove`:204, `handlePointerUp`:218 |
| `src/components/ui/tilt-card.tsx` | `TiltCard`:19 |
| `src/lib/utils.ts` | `cn`:4 |
