# AGENTS.md

Instructions for any coding agent working in this repository. Human contributors follow the same rules; see also [CONTRIBUTING.md](CONTRIBUTING.md).

## 1. Project context

**Campus Companion** is a Java-first student management and learning platform, built as a Java assignment. One Spring Boot application serves a REST API and a static HTML, CSS and JavaScript frontend. Data is in Supabase PostgreSQL, files in Supabase Storage, and an AI provider is called from Java. It is deployed to Azure App Service (primary) and Render (backup); see docs/24 sections 8a and 8b.

The owner must be able to explain the Java code in a viva. Prefer clear, conventional Java over clever or generated code.

**Current state (2026-10-24, deployed):** every P0 feature is built and tested, on the student side and the admin side: login and accounts, profile (with the student's own phone, text and photo), attendance, marks and results, timetable, notices, dashboard, the resource hub, Quick Exam with progress and recommendations, the AI study assistant, settings, and the privacy and terms pages. Also built: the public landing page (React, for that page only, [ADR 0015](docs/decisions/0015-react-landing-page.md)); from the P1 backlog, the CSV student import (`T7-07`), the audit log viewer (`T7-03`), the attendance calculator (`T7-01`), notice documents, the Past papers (PYQ) library ([ADR 0012](docs/decisions/0012-previous-year-question-papers.md)) and student complaints ([ADR 0013](docs/decisions/0013-student-complaints.md)); the planner (study tasks and exam countdown, T7-02); report a resource (FR-LIB-005, T7-06); flowchart generation in the AI assistant (FR-AI-006, T7-05, drawn without Mermaid); AI usage-log retention (T7-09, off by default); and, at the owner's request, Notes, the Quick Assistant chat and the floating 3D robot ([ADR 0014](docs/decisions/0014-notes-quick-assistant-and-robot.md)), Quick Assistant V2 (T7-14, [ADR 0018](docs/decisions/0018-quick-assistant-v2.md), 2026-11-01: Quick and Explanatory answer modes, picture questions, drawn explanations; no migration; needs `AI_VISION_ROUTES` on a host for pictures), and the coding portfolio and leaderboard (T7-13, [ADR 0017](docs/decisions/0017-coding-portfolio-and-leaderboard.md), 2026-10-30; `V20` and `V21` applied to hosted Supabase, `V22` not yet). Migrations `V1` to `V22` exist (`V16` planner, `V17` resource reports, `V18` usage-log retention, `V19` exam papers and whole-subject Quick Exam; all four applied to hosted Supabase on 2026-10-24, see [docs/24-production-readiness.md](docs/24-production-readiness.md) §6). Quick Exam tests a whole subject; course question banks for 25CS21001 to 25CS21003 are bundled in `src/main/resources/question-bank/` and loaded at start (`QUESTION_BANK_ENABLED`). The AI runs through a router over Groq, Ollama Cloud, Gemini and OpenRouter with automatic fallback ([ADR 0016](docs/decisions/0016-several-ai-providers-with-fallback.md)), live-tested locally; the mock stays the default and what every test uses. The provider data-terms review is an open owner action. FR-AI-005 (PDF summarizer, T7-04) is dropped and is not outstanding work. The production profile, the request limits, the generated access tests and the security review ([docs/21-security-review.md](docs/21-security-review.md)) are done, and the 2026-10-23 audit and its remediation are in [PROJECT_AUDIT_REPORT.md](PROJECT_AUDIT_REPORT.md). **Not done:** the provider data-terms review and the AI behaviour run (`T5-06`, `T5-08`), the storage bucket sign-off (`T3-01`), and the orphaned-file half of `T7-09` (deferred). Every approved P0 and P1 requirement is built and integration tested; production readiness and the one owner checklist are in [docs/26-final-project-completion-report.md](docs/26-final-project-completion-report.md) and [docs/24-production-readiness.md](docs/24-production-readiness.md). Those are owner tasks or later work; see [docs/16-implementation-plan.md](docs/16-implementation-plan.md) "Progress". Check the plan and the Git history before assuming anything else exists.

## 2. Approved stack

Java 21 · Spring Boot (Web, Security, Data JPA, Validation, Actuator) · Maven · PostgreSQL on Supabase · Flyway · Supabase Storage · JUnit 5, Mockito, Spring Boot Test, Testcontainers · plain HTML, CSS, JavaScript.

**Not approved** without an accepted decision record: Node.js or Express, any second backend, any frontend framework or bundler, MySQL or H2, JWT authentication, the Supabase client in the browser, Lombok, CDN-loaded libraries. The full list and versions are in [docs/04-TRD.md](docs/04-TRD.md).

**One accepted exception:** the public landing page (`/`) is built with React, TypeScript, Vite and Tailwind in `landing-frontend/` ([ADR 0015](docs/decisions/0015-react-landing-page.md)). Node is a development tool for that page only; its built files are committed. Every other page, and the whole backend, keeps the stack above. Do not use React or the landing build anywhere else.

## 3. The seven working principles

1. **Read before coding.** Read the documents listed in section 4 for the area you are changing.
2. **Do not invent missing decisions.** If something is unspecified or marked `TBD`, stop and report it. Do not silently choose an architecture, a library, a version or a business rule.
3. **Keep one source of truth.** API details live in the API contract and `openapi.yaml`; schema details in the database design and migrations; AI behaviour in the AI rules. Do not restate them elsewhere; link.
4. **Update documentation with code.** A change to an endpoint, table, permission or feature includes the matching document and test updates in the same change.
5. **Work in small tasks.** Implement one task from the implementation plan, verify it, then move to the next. Never implement several phases in one change.
6. **Verify every claim.** Report what you actually ran and what actually happened. Never say tests pass, an integration works or a deployment succeeded unless you ran it and saw the result in this session.
7. **Protect data and infrastructure.** Never expose credentials, never use real student data, never make destructive or production changes without explicit authorization.

## 4. Required reading

Always, before the first change in a session:

- This file, [SECURITY_RULES.md](SECURITY_RULES.md) and [AI_AGENT_SECURITY_RULES.md](AI_AGENT_SECURITY_RULES.md).
- The task you are implementing in [docs/16-implementation-plan.md](docs/16-implementation-plan.md).

Then, by area:

| If you are changing | Read |
| --- | --- |
| Any feature | The requirement in [02-PRD.md](docs/02-PRD.md); [05-architecture.md](docs/05-architecture.md) |
| An endpoint | [09-api-contract.md](docs/09-api-contract.md), [openapi.yaml](docs/openapi.yaml), [10-authorization-matrix.md](docs/10-authorization-matrix.md) |
| An entity, repository or migration | [08-database-design.md](docs/08-database-design.md) |
| Login, sessions, roles, uploads | [11-security-plan.md](docs/11-security-plan.md); for login identity and first-time passwords also [ADR 0008](docs/decisions/0008-institutional-email-login.md) and [SECURITY_RULES.md](SECURITY_RULES.md) section 11 |
| A page or frontend module | [06-app-flow.md](docs/06-app-flow.md), [07-ui-ux-spec.md](docs/07-ui-ux-spec.md) |
| Anything under `ai` | [12](docs/12-AI-features-spec.md), [13](docs/13-AI-rules.md), [14](docs/14-AI-security-and-privacy.md), [15](docs/15-AI-features-handoff.md) |
| Tests | [17-testing-strategy.md](docs/17-testing-strategy.md) |
| Build, configuration, deployment | [18-production-plan.md](docs/18-production-plan.md) |

## 5. Repository structure

```
.
├── AGENTS.md, SECURITY_RULES.md, AI_AGENT_SECURITY_RULES.md, CONTRIBUTING.md, README.md
├── .env.example                  // configuration names, no values
├── pom.xml, mvnw, system.properties, Procfile        (from T1-01 and T6-01)
├── Dockerfile, render.yaml       // container build for Render (docs/23); Heroku uses the Procfile
├── docs/                         // specifications and decision records
├── landing-frontend/             // React source of the landing page only (ADR 0015)
└── src
    ├── main
    │   ├── java/com/campuscompanion/...
    │   └── resources
    │       ├── application.yml, application-prod.yml
    │       ├── db/migration/     // Flyway: V1__core.sql ... V22__coding_badge_images.sql
│       ├── question-bank/    // bundled Quick Exam questions, one JSON file per subject code
    │       ├── prompts/          // versioned AI prompt templates
    │       ├── demo/             // synthetic demo questions for the seeder
    │       └── static/           // login.html, css/, js/, student/, admin/, vendor/, robot/
    └── test
        ├── java/com/campuscompanion/...
        └── js/                   // tests of browser modules, Node's own runner
```

There is no `application-dev.yml`. The `dev` profile has no settings file of its own: it only switches on the synthetic demo data (`DevDataSeeder`), which `DEMO_SEED_ENABLED=true` also does.

Java packages are organised by feature. The layout and dependency rule are in [docs/05-architecture.md](docs/05-architecture.md#4-package-structure).

## 6. Inspect before you create

- Search the codebase for an existing class, method, style or script before writing a new one.
- Extend or reuse what exists. Do not create a parallel implementation, a second API client, a second error format or a duplicate CSS file.
- Do not rewrite or reformat files you were not asked to change.
- If existing code contradicts a document, report the contradiction; do not pick a side silently.

## 7. Coding standards

### Java

- Layers: controller → service → repository. No business logic in controllers. No repository use in controllers.
- DTOs are Java records. Entities never cross the controller boundary.
- Constructor injection. No field injection. No Lombok.
- `BigDecimal` for marks, percentages and grade points, with explicit `HALF_UP` rounding to two decimals. `java.time` for dates.
- Throw the project's exceptions from `common.error`; let the global handler build the response. Do not catch and ignore exceptions.
- No `System.out.println`; use the logger. Follow the logging rules in the TRD.
- Methods are short and named for what they do. Add a comment only when the reason is not obvious from the code.
- Core rule classes (attendance, CGPA, quiz evaluation, progress) are plain Java that can be unit-tested without Spring.

### Naming

| Kind | Convention | Example |
| --- | --- | --- |
| Controller | `<Feature>Controller`, `Admin<Feature>Controller` | `AttendanceController` |
| Service | `<Feature>Service` | `QuizEvaluationService` |
| Repository | `<Entity>Repository` | `StudentProfileRepository` |
| Entity | Singular noun | `AttendanceRecord` |
| DTO | `<Thing>Request`, `<Thing>Response` | `StartQuizRequest` |
| Exception | `<Problem>Exception` | `ResourceNotFoundException` |
| Test | `<ClassUnderTest>Test`; integration: `...IT` | `AttendanceServiceTest` |
| Table, column | `snake_case`, plural tables | `quiz_attempts` |
| JSON field | `camelCase` | `classesAttended` |
| Migration | `V<n>__<description>.sql` | `V2__academic.sql` |
| Frontend file | `kebab-case` | `quiz-history.html` |

### Frontend

- Plain HTML, CSS and ES modules. No framework, no build step.
- All API calls go through `js/api.js`.
- Insert dynamic text with `textContent`, never `innerHTML`.
- No inline scripts or inline event handlers.
- Use the design tokens and components in [docs/07-ui-ux-spec.md](docs/07-ui-ux-spec.md).

## 8. Commands

Available once task `T1-01` is complete.

| Purpose | Command |
| --- | --- |
| Run locally | `./mvnw spring-boot:run` |
| Unit tests | `./mvnw test` |
| All tests and checks | `./mvnw verify` |
| Format | `./mvnw spotless:apply` |
| Check formatting | `./mvnw spotless:check` |
| Build the JAR | `./mvnw -DskipTests package` |
| Browser-module tests (Node 20 or later, no packages) | `node --test "src/test/js/**/*.test.mjs"` |
| Rebuild the landing page (ADR 0015) | `npm ci && npm run build` in `landing-frontend/`, then delete the old hash-named files in `static/js/landing/` and `static/css/landing/` |

Integration tests need Docker running. If Docker is unavailable, say so in your report; do not skip the tests silently.

Every command needs Java 21: `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`. With Colima as the Docker engine, start it with `colima start` and also set `DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"` and `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock` before `./mvnw verify`.

## 9. Rules for schema changes

1. Update [docs/08-database-design.md](docs/08-database-design.md) first.
2. Add a **new** Flyway migration. Never edit a migration that has been applied anywhere.
3. Enable Row Level Security on every new table in the same migration.
4. Update the entity and repository, and add or update repository tests.
5. A migration that drops or rewrites data needs the owner's approval before you write it.
6. Never run a migration against a hosted database yourself.

## 10. Rules for API changes

1. Update [docs/openapi.yaml](docs/openapi.yaml) and [docs/09-api-contract.md](docs/09-api-contract.md).
2. Add the endpoint to [docs/10-authorization-matrix.md](docs/10-authorization-matrix.md).
3. Add controller tests for success, validation failure, unauthenticated (401), wrong role (403) and, where ownership applies, wrong owner (404).
4. Renaming or removing a field, or changing its type, is a breaking change and needs approval.

## 11. Needs approval before you proceed

Stop and ask the owner before:

- Adding, removing or upgrading a dependency or plugin.
- Changing the architecture, the stack, or an accepted decision record.
- Changing authentication, authorization, session, CSRF, upload validation or secrets handling. This includes the login identity rules and security exception EX-AUTH-01.
- Changing a P0 requirement or adding a feature that is not in the PRD.
- Writing a destructive migration.
- Anything listed as approval-required in [AI_AGENT_SECURITY_RULES.md](AI_AGENT_SECURITY_RULES.md).

## 12. Before declaring a task complete

- [ ] The task's acceptance criteria are met.
- [ ] `./mvnw verify` passes, and you saw it pass.
- [ ] New logic has tests; no test was deleted, disabled or weakened.
- [ ] No rule in [SECURITY_RULES.md](SECURITY_RULES.md) is broken.
- [ ] Affected documents are updated.
- [ ] No secrets, real data or debug output are in the change.
- [ ] You reviewed your own diff.

## 13. How to report

End every task with a report in this form:

```
Task: <ID and title>
Status: complete | partial | blocked

Changed files:
- <path>: <what changed>

Verification:
- <command>: <actual result, with counts>

Not verified:
- <anything you could not run or check, and why>

Known limitations:
- <gaps, shortcuts, follow-ups>

Decisions needed:
- <open questions or TBDs you hit>
```

If a check failed, say so and include the output. A truthful "partial" is always better than an untrue "complete".
