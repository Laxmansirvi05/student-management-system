# 24. Production readiness

> Status: Draft v0.1 · Owner: Laxman Sirvi · Written 2026-10-24 from the repository at commit `c5c1c03` and later · Host-independent

This document says what must be true before real students use Campus Companion, what has been verified, and what remains. It does not replace [18-production-plan.md](18-production-plan.md) (settings, variables, rollback) or [23-deploying-on-render.md](23-deploying-on-render.md) (Render steps). It adds the checks that apply whichever host is chosen.

**Verdict on 2026-10-24: not ready for real students.** The application is built and tested, but the blockers in section 1 are open. Most of them are owner actions outside the repository.

How to read the status words:

- **Built and tested:** the code exists and automated tests pass against a real PostgreSQL in Docker.
- **Checked locally:** also exercised through the running application on this machine (throw-away database).
- **Unverified in production:** depends on the hosted database, the storage bucket, the host or a provider account, none of which were touched.
- **Owner:** only the owner can do it, in an external console or by a decision.

## 1. Blockers before real students

| # | Blocker | Kind | Section |
| --- | --- | --- | --- |
| B1 | Rotate the credentials listed in section 3 | Owner | 3 |
| B2 | Decide which AI providers may receive student text, after reading their terms; adjust `AI_ROUTES` | Owner decision | 4 |
| B3 | Confirm students are 18 or over, or stop using Gemini (its terms forbid apps likely used by under-18s) | Owner decision | 4 |
| B4 | Sign off the storage bucket (private, file types, size limit, links work) | Owner, with a developer | 5 |
| B5 | Back up hosted Supabase, then let `V16` to `V19` run at the first start of the new build | Owner, deployment | 6 |
| B6 | Choose the host and create the service; set every variable of [18](18-production-plan.md) §5 | Owner, deployment | 7, 8 |
| B7 | First-deploy checks: HTTPS without a redirect loop, health, memory, login, one AI request | Deployment | 8 |
| B8 | The college reviews and adopts the privacy policy and terms (the pages say they must be) | Owner | — |
| B9 | GitHub security settings (secret scanning, push protection, Dependabot), task T0-04 | Owner | — |

## 2. Security review (2026-10-24)

What was checked in the repository, and the result:

| Area | Result | Evidence |
| --- | --- | --- |
| Secrets in Git | **None.** The 19 secret values in the local `.env` (and its backup) were searched for in every tracked file and in the full history, along with common key patterns. | Secret scan of this phase (values never printed) |
| `.env` handling | `.env` and `.env.*` are ignored by Git and by Docker (`.dockerignore`); the Dockerfile copies only `.mvn`, `mvnw`, `pom.xml` and `src`. Hosts build from the Git repository, so no `.env` reaches them. | `.gitignore`, `.dockerignore`, `Dockerfile` |
| Authentication, sessions | Server-side sessions; cookie `HttpOnly`, `SameSite=Lax`, `Secure` in `prod`; 30-minute timeout; BCrypt; lockout and login rate limits; per-account API limit. | `application*.yml`, `SecurityConfig`, `AuthFlowIT`, `LockoutIT`, `RequestLimitsIT` |
| CSRF | Required on every state-changing request, including login (cookie to header). | `SecurityConfig`, `AuthFlowIT` |
| CORS | None configured: the API is same-origin only. | No CORS configuration in the code |
| Authorization | Deny by default; student-only and admin-only URL groups, repeated by `@PreAuthorize`; every endpoint is tested for 401, 403 and the wrong owner. | `EndpointAccessIT`, feature ITs |
| Database roles and RLS | The application connects as `cc_app` (no DDL); every table has RLS with policies for `cc_app` only; privileges per table are pinned in a test. Policies are `using (true)`: isolation between students is enforced by the services, by design ([ADR 0007](decisions/0007-database-roles-and-rls.md)), and the Supabase Data API must stay off. | `DatabaseSecurityModelIT`, `PlannerIT`, `NotesIT` |
| Logging | Logs name accounts by internal ID only; no email, name, password, key, prompt or reply is logged. AI routes are logged as `provider:model (key n)`. | Code search; `AiRouterWorkflowIT` checks the log |
| Error responses | `prod` hides stack traces and messages; one problem-details format. | `application-prod.yml`, `ProductionProfileIT` |
| Headers | Strict CSP, HSTS on HTTPS, `Referrer-Policy: no-referrer`, `Permissions-Policy`, `frame-ancestors 'none'`. | `SecurityConfig`, `ContentSecurityPolicyTest` |

**Repository-level defects found in this phase: none that needed a code change.** Two settings are deliberate and documented, so they were not changed:

- `DEMO_SEED_ENABLED=true` also seeds under `prod`. This is the documented way to load a demonstration deployment for one start (T6-06, doc 23). On a deployment for real students it **must not be set** (section 8 checklist).
- `AI_PROVIDER=mock` also works under `prod`; students then see "Demo mode". It is the documented default until a provider is approved.

**Unverified until the first deployment:** behind the host's proxy, Tomcat trusts `X-Forwarded-Proto` only from private addresses (`forward-headers-strategy: native`). If the host's proxy connects from another range, every request would be redirected to HTTPS again and again. Check it in the first minute (section 8, step 3). The fix, if needed, is to set `server.tomcat.remoteip.internal-proxies` to the host's documented proxy range; do not switch HTTPS off.

Local-only notes (not in the repository): `.env` sets `SPRING_PROFILES_ACTIVE=dev`, which has no effect (Spring does not take a profile from an imported file; the 8080 log shows "No active profile set"); demo seeding is off there (`DEMO_SEED_ENABLED=false`).

## 3. Credentials the owner must rotate

Rotation happens only in the providers' consoles, by the owner. After rotating, put the new value in the local `.env` and in the host's secret settings, then delete `.env.backup-2026-10-23` (a local copy of the old values, kept when a dead Gemini key was disabled).

| Variable | Why | Where to rotate | Priority |
| --- | --- | --- | --- |
| `DB_PASSWORD` (role `cc_app`) | Security review F4: seen during development sessions | Supabase SQL editor: `alter role cc_app with password '…'` (as the owner) | **Required** |
| `DB_MIGRATION_PASSWORD` (role `cc_migrator`) | F4, same | Supabase SQL editor: `alter role cc_migrator with password '…'` | **Required** |
| `SUPABASE_SERVICE_KEY` | Full storage access; held in the development `.env` used by AI-assisted sessions | Supabase dashboard → Project Settings → API keys: create a new secret key, switch the app to it, then delete the old one | Recommended before production |
| `ADMIN_BOOTSTRAP_PASSWORD` | Only for the first start; then remove it from every environment | Remove the variable after the admin has changed the password | **Required** (remove) |
| `GEMINI_API_KEY` (fingerprint `36df8d6d`) | Its project is refused by Google ("denied access") | Google AI Studio / Google Cloud console → API keys: delete it | Required (delete) |
| `GEMINI_API_KEY_2` … `_4`, `GROQ_API_KEY` … `_5`, `OPENROUTER_API_KEY` … `_4`, `OLLAMA_API_KEY`, `_2` | Held in the development `.env`; never printed or committed, but used from development machines and tools | Google AI Studio → API keys; console.groq.com → API Keys; openrouter.ai → Keys; ollama.com → Settings → Keys | Recommended: give production its own keys, and keep only the providers kept in section 4 |

Several keys of one provider: use them only within that provider's terms. The router never cycles through them to get round a rate limit, and keys made in extra accounts only to multiply free quota may themselves break the terms.

## 4. AI providers: data terms review

Checked on **2026-10-24** from the providers' own pages. This is a summary for the owner's decision, not legal advice; terms change, so re-read them before deciding.

| Provider | Prompts kept? | Used for training? | Free-tier points | Other considerations | Sources |
| --- | --- | --- | --- | --- | --- |
| **Groq** | Not retained by default; inputs and outputs may be logged up to 30 days for reliability or abuse investigation. **Zero Data Retention** can be switched on by any customer in Data Controls. | No: the Services Agreement bars training on customer inputs or outputs without the customer's permission. | The data page does not distinguish free and paid. | Data stored in Google Cloud in the United States; a Data Processing Addendum exists. | [Your data](https://console.groq.com/docs/your-data), [Services Agreement](https://console.groq.com/docs/legal/services-agreement), [DPA](https://console.groq.com/docs/legal/customer-data-processing-addendum) |
| **Ollama Cloud** | Processed transiently, "not stored beyond the time required"; no fixed period stated. | No: "We do not use your inputs or outputs to train any AI models." | — | May be processed in the United States; third-party inference providers help run the service. | [Privacy policy (March 2026)](https://ollama.com/privacy) |
| **Google Gemini API** | Free tier: no period stated. Paid: logged "for a limited period" for abuse and safety only. | **Free tier: yes.** Content is used to improve Google's products, and human reviewers may read it. Paid: no. | Google says: "Do not submit sensitive, confidential, or personal information to the Unpaid Services." | **Users must be 18 or over, and apps must not be directed at or likely used by under-18s.** For users in the EEA, Switzerland or the UK, only paid services may be used. | [Gemini API terms (updated 28 Apr 2026)](https://ai.google.dev/gemini-api/terms) |
| **OpenRouter** | OpenRouter itself stores prompts only if the account opts in to logging; metadata is always kept. The upstream provider's own policy applies. | OpenRouter itself: no. **Upstream providers of `:free` models may train**: OpenRouter's free-model pages say so for several models, and it treats an unverified provider as one that trains. An account setting can forbid routing to providers that train (separately for free and paid models). | The current route's model page (`nvidia/nemotron-3.5-lightning:free`) states no data policy. | Policies differ per upstream provider and change often. | [Data collection](https://openrouter.ai/docs/guides/privacy/data-collection), [Provider logging](https://openrouter.ai/docs/guides/privacy/provider-logging), [Free models](https://openrouter.ai/collections/free-models), [Privacy policy](https://openrouter.ai/privacy) |

**Recommendation (for the owner to approve; `AI_ROUTES` was not changed):**

1. Keep **Groq** first and switch on Zero Data Retention in its Data Controls.
2. Keep **Ollama Cloud** second.
3. Remove **Gemini** from student traffic unless the project is moved to a paid (billed) account *and* the users are confirmed to be 18 or over. The free tier trains on and lets humans read student text.
4. Remove **OpenRouter `:free`** from student traffic. Its policy for the current model is not stated, free providers may train, and it was also too slow for the study tools in the live test. If OpenRouter is kept at all, forbid training providers in its settings and use a model whose page states its policy.

With only Groq and Ollama the router keeps a fallback, so it still works when one of them is down: `AI_ROUTES=groq:openai/gpt-oss-120b,groq:qwen/qwen3.8-27b,ollama:gpt-oss:20b`. The privacy page names all four services today; if providers are removed, update that sentence in the same change.

**Router behaviour relevant to production** (built and tested, live-checked locally; [docs/12](12-AI-features-spec.md) §2b):

- Keys are read from the server's environment only and never reach the browser, the logs or Git.
- At most 3 calls per request, 15 s each, within 25 s, which is under Heroku's 30-second router limit.
- A 429 pauses the whole provider. A refused key pauses that key. When every provider is paused, the request fails at once with 503.
- The mock is never part of the router. `AI_PROVIDER=mock` is a separate, explicit setting that students see as demo mode.

## 5. Supabase database and Storage

**Database (built and tested):**
- 18 migrations, V1 to V18. Each table has RLS, `cc_app` grants and policies, and the expected privileges are pinned in `DatabaseSecurityModelIT`.
- Isolation between students is tested per feature: notes, attachments, sharing, the planner, the assistant chats, complaints and quiz attempts.
- V16 to V19 are **not yet applied to the hosted database** (it is at V15); see section 6.

**Storage (built and tested against an in-memory stand-in; the real bucket is unverified):**
- The bucket is meant to be private. The service key is used only by the server. Every file is reached through a signed link that lives 5 minutes, issued only after the application's own access check.
- Upload limits: notes attachments and notice documents up to 10 MB, profile photo 2 MB, CSV imports 1 MB.
- Uploads are checked by content: a PDF must parse, and pictures are re-encoded as new JPEGs (which removes location data).
- Stored file names are generated by the server, not taken from the user's file name.

**Storage sign-off checklist (owner with a developer; tick each on the real project):**

- [ ] The bucket named by `SUPABASE_STORAGE_BUCKET` exists and is **private** (public access off).
- [ ] An unsigned URL to an object in the bucket is refused.
- [ ] The bucket's allowed MIME types and size limit match the application (PDF and JPEG; 10 MB), see [22-loading-real-data.md](22-loading-real-data.md).
- [ ] No Storage policy grants `anon` or `authenticated` access (the app uses the service key only).
- [ ] The Supabase Data API is off (task T0-03), so RLS `using (true)` policies are never reachable from outside.
- [ ] With the production key: upload a test PDF as an admin, preview it as a student, download it, delete it, and check the object is gone from the bucket.
- [ ] A signed link stops working after about 5 minutes.
- [ ] A profile photo and a note picture upload, show and delete.
- [ ] The CSP lets the PDF preview frame and photos load from the storage host (no console errors).
- [ ] Old files from earlier tests are cleared, or known.

## 6. Hosted database migration (V16 to V19)

The hosted database is at **V15** (seen in the 8080 log on 2026-10-24, and in `flyway_schema_history`). Four migrations are waiting. Flyway applies them, in order and each in its own transaction, the first time a build that reads the hosted settings starts: a deployment, or a restart of the local 8080 server. **Do not start such a build until the owner has taken the backup and approved.**

**Expected changes (all additive; no existing row is changed or removed):**

| Migration | Creates or changes | Grants to `cc_app` | RLS |
| --- | --- | --- | --- |
| `V16__planner.sql` | Tables `study_tasks`, `student_exams`, two indexes | select, insert, update, delete on both | Enabled, one policy per privilege |
| `V17__resource_reports.sql` | Table `resource_reports`, a partial unique index (one open report per student and resource), two indexes | select, insert, update | Enabled, three policies |
| `V18__ai_log_retention.sql` | No table; lets the app delete from `ai_request_logs` (for the nightly clean-up, which stays **off** until `AI_LOG_RETENTION_ENABLED=true`) | delete on `ai_request_logs` | One delete policy |
| `V19__exam_papers_and_subject_tests.sql` | Table `notice_exam_papers` (the papers of an exam time table); `quiz_questions.unit_id` and `quiz_attempts.unit_id` become nullable (existing rows keep their unit) | select, insert, delete on the new table | Enabled, three policies |

**1. Backup (owner).** Supabase dashboard → Database → Backups: note the newest daily backup, or take one now. With the Supabase CLI or as the database owner, a logical copy also works: `pg_dump --schema=app --format=custom` to a file kept off the repository. Write down the time.

**2. Validate before applying (developer, no hosted access needed).** Already done for this build: every migration runs from an empty database in `./mvnw verify` (Testcontainers PostgreSQL 17, the real role model) and `DatabaseSecurityModelIT` pins the resulting tables, grants and policies. Right before the real run, confirm the hosted history ends at version 15 with no failed row: in the SQL editor, `select version, success from app.flyway_schema_history order by installed_rank desc limit 3;`.

**3. Apply (owner approves).** Deploy, or restart the 8080 server. Watch the log for `Successfully applied 4 migrations to schema "app", now at version v19`, then for `Loaded 135 questions from the course question banks` (the bundled Quick Exam questions, added once to subjects 25CS21001, 25CS21002 and 25CS21003). A failure stops the start-up and Flyway reports which migration failed; nothing after it runs.

**4. Post-migration checks.**
- `select version, success from app.flyway_schema_history order by installed_rank desc limit 4;` shows 16 to 19, all `true`.
- Then, as the owner, run `private-data/load-exam-papers.sql` (kept out of Git): it adds the six papers of the December 2026 regular time table to notice 1 and moves the fee notice (notice 2) from Exam to General, so it is a deadline and not an exam. Check the dashboard of a CSE(AI&ML) semester 3 student: six exams from 7 to 18 December and the 10 November fee deadline.
- `select relname, relrowsecurity from pg_class where relnamespace = 'app'::regnamespace and relname in ('study_tasks','student_exams','resource_reports');` shows RLS on for all three.
- In the app: the planner page loads and a task can be added and deleted; a resource page shows "Report a problem"; the admin Resources page shows "Reported resources".
- The Supabase Data API is still off (section 5).

**5. Rollback and recovery.** All three are additive, so the old build keeps working against the new schema: rolling the application back needs no database change. To undo the schema itself (only if needed, as the owner): restore the backup; or drop the four new tables (`study_tasks`, `student_exams`, `resource_reports`, `notice_exam_papers`; first delete quiz rows without a unit, then set `unit_id` back to `not null`) and `drop policy ai_request_logs_runtime_delete on app.ai_request_logs; revoke delete on app.ai_request_logs from cc_app;`, then delete the rows for versions 16 to 19 from `app.flyway_schema_history`. Any planner tasks or reports students made in between are lost by either route.

## 6a. Memory measurement plan (512 MB host)

The local figure (about 400 to 460 MB resident with `-Xmx256m`) is not production evidence: the host's JVM, container limits and traffic differ.

1. Deploy to the paid 512 MB plan with the Dockerfile's default `JAVA_OPTS` (`-Xmx256m -Xss512k -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=64m -XX:+UseSerialGC`). Adding `-XX:+ExitOnOutOfMemoryError` through the host's `JAVA_OPTS` setting makes an out-of-memory failure restart the service instead of leaving it half-working.
2. Read the host's memory graph right after start, after one hour idle, and during a short load: ten test students using the dashboard, notes, a resource preview and the assistant at the same time for ten minutes.
3. **Acceptable:** the peak stays at or under 85% of the plan (about 435 MB of 512 MB) with no out-of-memory restart. Between 85% and 95%: watch for a week before inviting more students. Above 95%, or any OOM restart: move to the next plan size before students arrive.
4. Record the numbers in this document with the date and the plan.

## 7. Host comparison: Render or Heroku

Both run this application as built today: a single Spring Boot JAR on Java 21, one instance, sessions in memory. Prices and limits are from the providers' pages on 2026-10-24; check again before paying.

| | Render | Heroku |
| --- | --- | --- |
| Build | Docker, from the repository's `Dockerfile` (`render.yaml` exists) | Java buildpack with `mvnw`, `system.properties` (`java.runtime.version=21`) and `Procfile` |
| Smallest always-on plan | Starter: 512 MB, $7/month | Basic dyno: 512 MB, $7/month |
| Cheaper plans | Free: spins down after 15 min idle, about 1 min cold start, 750 h/month, may restart any time. Not for students. | Eco: $5/month, sleeps after 30 min idle |
| Next size up | Standard: 2 GB, $25/month | Standard-2X: 1 GB, $50/month |
| Memory need | Measured locally: about 400 to 460 MB resident after start with `-Xmx256m`. 512 MB plans are tight; measure under load on the chosen host. | Same |
| Request timeout | No short router limit documented for normal requests | 30 s router limit (H12); the AI budget of 25 s fits |
| Health checks and deploys | HTTP health check on `/actuator/health` (2xx or 3xx counts as healthy); new instances get traffic only after passing, so a failed deploy keeps the old one | No HTTP health check; the dyno must bind `$PORT` |
| Restarts | On deploy | Daily dyno restarts; sessions are in memory, so users sign in again at least daily |
| Secrets | Environment variables per service (`sync: false` in `render.yaml`) | Config vars |
| Rollback | Redeploy an earlier deploy from the dashboard | `heroku releases:rollback` |
| Logs | Dashboard log stream | Log stream; add-ons for retention |

Sources: [Render pricing](https://render.com/pricing), [Render free instances](https://render.com/docs/free), [Render health checks](https://render.com/docs/health-checks), [Heroku pricing](https://www.heroku.com/pricing), [Heroku request timeout](https://devcenter.heroku.com/articles/request-timeout), [Heroku Java support](https://devcenter.heroku.com/articles/java-support).

**Recommendation (the owner decides):** Render, Starter plan.
- `render.yaml` and the Dockerfile already exist.
- Its health-checked deploys keep the old version running if a new one fails to start.
- It has no daily restart to log students out, and no 30-second limit near the AI budget.

Do not use either free or sleeping plan for students. Before the first deploy, change `plan: free` in `render.yaml` to the chosen paid plan; that is a deliberate owner change, so it was not made here. Heroku Basic is a fine alternative with the same memory and price, if the owner prefers its buildpack and rollback commands.

## 8. Host-independent deployment steps

**Before:**
1. Section 1 blockers B1 to B4 done; B5 backup taken.
2. Every variable of [18](18-production-plan.md) §5 set as a host secret:
   - `SPRING_PROFILES_ACTIVE=prod`;
   - `DEMO_SEED_ENABLED` **not set**;
   - `AI_PROVIDER` and `AI_ROUTES` as decided in section 4, with the matching provider keys;
   - `ADMIN_BOOTSTRAP_*` only for the first start.
3. `JAVA_OPTS` sized for the plan (the Dockerfile default is `-Xmx256m` for 512 MB).

**First start:**
1. Watch the log for Flyway reaching version 19, "Started CampusCompanionApplication", and "AI router: N routes" (or the mock).
2. Open `https://<host>/actuator/health`: expect `{"status":"UP"}`.
3. Open `https://<host>/login.html`. It must load once, not redirect again and again (section 2, forwarded headers). Also check that `http://` redirects to `https://`.
4. Log in as the bootstrap admin, change the password, then remove `ADMIN_BOOTSTRAP_*`.
5. As a test student: dashboard, planner (add and delete a task), one Quick Assistant message, one resource preview.
6. Check memory on the host's metrics for a day. If it nears the limit, move to the next plan size before students arrive.

**Still needed after the host is chosen:**
- The host's own service settings: plan, region close to the Supabase project, health check path, auto-deploy on or off.
- The custom domain and its TLS certificate, if one is used.
- A log retention choice.
- An uptime check.

## 9. What was verified in this phase

- `./mvnw -B verify` passes in an isolated copy (no `.env`, Testcontainers PostgreSQL), including the planner tests.
- Node browser-module tests pass.
- The secret scan of tracked files and full history found nothing.
- Nothing was deployed, no hosted database or bucket was touched, no credential was changed, and no AI request was made in this phase.
