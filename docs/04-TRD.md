# 04. Technical Requirements Document

> Status: Draft v0.3 · Owner: Laxman Sirvi · Last updated: 2026-10-13

This document sets the technical constraints every implementation must satisfy. Requirement IDs here start with `TR-`.

## 1. Approved technology stack

| Area | Technology | Version | Notes |
| --- | --- | --- | --- |
| Language | Java | 21 (LTS) | Fixed. |
| Framework | Spring Boot | 4.1.1 | Verified in `T1-01` on Java 21. It manages Spring Framework 7.0.9, Spring Security 7.1.1, Hibernate 7.4.5, Flyway 12.4.0 and the PostgreSQL driver 42.7.13. |
| Web | Spring Web MVC (`spring-boot-starter-webmvc`) | from Boot | REST controllers. |
| Security | Spring Security | from Boot | Session-based login. |
| Persistence | Spring Data JPA with Hibernate | from Boot | |
| Validation | Jakarta Bean Validation | from Boot | |
| Health | Spring Boot Actuator | from Boot | Only the health endpoint is exposed. |
| Database | PostgreSQL on Supabase | Supabase-managed | |
| JDBC driver | PostgreSQL JDBC | from Boot | |
| Migrations | Flyway | from Boot | See [ADR 0006](decisions/0006-flyway-database-migrations.md). |
| File storage | Supabase Storage | Supabase-managed | Called over HTTPS from Java. |
| PDF text | Apache PDFBox | 3.0.5 (chosen in `T3-03`) | Validation in R1, text extraction in P1. |
| HTTP client | Spring `RestClient` | from Boot | For storage and the AI provider. |
| Build | Maven with the Maven Wrapper | Maven 3.9.11, wrapper 3.3.4 | `./mvnw` is the only supported way to build. The build refuses any Java other than 21. |
| Formatting | Spotless with google-java-format | Spotless 3.10.4, google-java-format 1.36.1 | google-java-format 1.37.0 fails under Spotless 3.10.4, so 1.36.1 is pinned. |
| Tests | JUnit Jupiter, Mockito, Spring Boot Test, Spring Security Test | from Boot: JUnit Jupiter 6.0.3, Mockito 5.23.0 | Spring Boot 4.1 manages JUnit 6, the successor of JUnit 5. Earlier documents say "JUnit 5"; the annotations and style are the same. |
| Database tests | Testcontainers for PostgreSQL | 2.0.5, from Boot | Needs Docker. Tests run on the `postgres:17-alpine` image. |
| Frontend | HTML5, CSS3, JavaScript (ES modules) | browser-native | No framework, no bundler. One exception: the public landing page only, see the next row. |
| Landing page only | React, TypeScript, Vite, Tailwind CSS, three.js | from `landing-frontend/package.json` | [ADR 0015](decisions/0015-react-landing-page.md). Built at development time with Node; the built files are committed. Used by `static/index.html` and nothing else. |
| Diagrams (P1) | None | — | Not used: the flowchart (T7-05) is drawn with plain HTML and CSS from the graph Java has checked, and the Mermaid text Java builds is only offered for copying. Vendoring Mermaid would need the owner's approval. |
| Hosting | Heroku or Render, **not yet chosen** (T6-04) | | Heroku runs the JAR from the `Procfile`; Render builds the `Dockerfile` from `render.yaml` ([doc 23](23-deploying-on-render.md)). Either way: one instance. |

**TR-STACK-1.** Versions marked TBD MUST be chosen in the named task by checking the official documentation for mutual compatibility (Java 21, Spring Boot, Heroku runtime), then written into this table and `pom.xml` in the same change.

**TR-STACK-2.** Dependency versions managed by the Spring Boot parent MUST NOT be overridden without a recorded reason.

## 2. Not approved

The following MUST NOT be introduced without an accepted decision record in [docs/decisions](decisions/README.md):

- Node.js, Express or any second backend or microservice.
- React, Vue, Angular, or any frontend framework, bundler or CSS framework. The one accepted exception is the public landing page ([ADR 0015](decisions/0015-react-landing-page.md)); it does not extend to any other page.
- MySQL, MongoDB, or H2 as a substitute for PostgreSQL (including in tests).
- The Supabase JavaScript client, Supabase Auth, or the Supabase Data API from anywhere. The Data API is switched off.
- JWT-based authentication.
- Lombok or other code generators that hide the Java being assessed.
- Any library loaded from a CDN at runtime.

## 3. Java and Spring Boot requirements

- **TR-JAVA-1.** Base package `com.campuscompanion`, organised by feature (see [05-architecture.md](05-architecture.md#4-package-structure)).
- **TR-JAVA-2.** Layers are controller → service → repository. Controllers contain no business logic and never touch a repository directly.
- **TR-JAVA-3.** Entities are never returned from controllers. Requests and responses use DTOs, written as Java records.
- **TR-JAVA-4.** Constructor injection only. No field injection.
- **TR-JAVA-5.** Business rules that the owner must explain (attendance percentage, CGPA, quiz scoring, progress classification, flowchart rendering) are plain Java classes with unit tests and no framework dependencies beyond annotations.
- **TR-JAVA-6.** Money-like and percentage values use `BigDecimal` with explicit rounding (`HALF_UP`, two decimals). No `float` or `double` for marks or percentages.
- **TR-JAVA-7.** Dates and times use `java.time`. Timestamps are stored in UTC and displayed in `Asia/Kolkata`.
- **TR-JAVA-8.** Configuration comes from environment variables bound through `@ConfigurationProperties`. Names are listed in [.env.example](../.env.example) and explained in [18-production-plan.md](18-production-plan.md) §5, which is the configuration reference. A variable used in code must appear in both.

## 4. Frontend approach

- **TR-FE-1.** Static files live in `src/main/resources/static` and are served by Spring Boot.
- **TR-FE-2.** Multi-page: one HTML file per screen, shared CSS, and JavaScript modules per feature.
- **TR-FE-3.** All server communication goes through one `api.js` module that adds the CSRF header, handles 401 by redirecting to login, and converts error responses into messages.
- **TR-FE-4.** Server-supplied and AI-supplied text is inserted with `textContent`. `innerHTML` MUST NOT receive dynamic data.
- **TR-FE-5.** No inline scripts or inline event handlers, so the Content Security Policy can forbid them.
- **TR-FE-6.** The frontend holds no secrets and makes no authorization decisions.

## 5. REST API conventions

- **TR-API-1.** Base path `/api/v1`. JSON request and response bodies, `camelCase` field names, UTF-8.
- **TR-API-2.** Errors use RFC 9457 problem details with a stable `code` field. Formats, status codes and pagination are defined in [09-api-contract.md](09-api-contract.md).
- **TR-API-3.** [openapi.yaml](openapi.yaml) is the authoritative contract. An endpoint change updates it in the same commit.
- **TR-API-4.** Every request body is validated with Bean Validation before reaching a service.

## 6. Database connectivity

- **TR-DB-1.** Connect through the Supabase **session pooler** using the PostgreSQL JDBC driver. The transaction pooler MUST NOT be used with Hibernate. The application's connection pool logs in as the runtime role `cc_app`.
- **TR-DB-2.** HikariCP maximum pool size is 5 (verify against the Supabase plan's connection limit in `T1-02`).
- **TR-DB-3.** `spring.jpa.hibernate.ddl-auto` is `validate` in every profile. Only Flyway changes the schema.
- **TR-DB-4.** `spring.jpa.open-in-view` is `false`.
- **TR-DB-5.** Queries use Spring Data derived queries, JPQL or native queries with bound parameters. SQL built by string concatenation is prohibited.
- **TR-DB-6.** List endpoints are paginated in the database, not in memory.
- **TR-DB-7.** Application tables live in schema `app`. Every one has Row Level Security enabled, the minimum grants for `cc_app`, and policies for `cc_app` only. RLS is defence in depth: it does not restrict the table owner or roles that bypass RLS, and it is not the per-student access control.
- **TR-DB-8.** Flyway connects as the migration role `cc_migrator`, which owns the schema. The runtime role `cc_app` owns nothing, cannot change the schema, and has neither `SUPERUSER` nor `BYPASSRLS`. The application MUST NOT connect as `postgres`.
- **TR-DB-9.** The Supabase Data API is switched off, schema `app` is not an exposed schema, and `anon`, `authenticated` and `service_role` hold no privileges on it.
- **TR-DB-10.** The Spring Boot application is the only authorization boundary for users. No user-level access rule may be delegated to the database.

TR-DB-7 to TR-DB-10 summarise the model in [08-database-design.md](08-database-design.md) §11, which is Proposed in [ADR 0007](decisions/0007-database-roles-and-rls.md) and awaits approval (OQ-10).


Schema details are in [08-database-design.md](08-database-design.md).

## 7. Authentication and authorization

- **TR-SEC-1.** Spring Security with server-side sessions and a session cookie. See [ADR 0004](decisions/0004-session-cookie-authentication.md). Spring Security remains responsible for authentication, session management and role-based authorization.
- **TR-SEC-2.** Passwords are hashed with BCrypt through Spring's `PasswordEncoder`.
- **TR-SEC-3.** CSRF protection is on for all state-changing requests, using a cookie-to-header token.
- **TR-SEC-4.** URL rules protect `/api/v1/admin/**` for `ADMIN`. Ownership is enforced in services by deriving the student from the session, never from a request parameter.
- **TR-SEC-5.** The login identifier is the account's institutional email address, normalised by trimming and lower-casing before every lookup and every write. A roll number is not a login identifier. See [ADR 0008](decisions/0008-institutional-email-login.md).
- **TR-SEC-6.** The official student email domain is read from `STUDENT_EMAIL_DOMAIN`. It MUST NOT be hardcoded in Java, SQL, JavaScript or tests. The application refuses to start if it is missing or is not a valid host name. Tests use a reserved example domain.
- **TR-SEC-7.** While an account has `must_change_password = true`, a filter in the security chain rejects every request except the four listed in [09-api-contract.md](09-api-contract.md) §2. This is enforced on the server for every endpoint, not by the pages.
- **TR-SEC-8.** Password rules are implemented in one Java class (`PasswordPolicy`) used by every path that sets a password, so the rule "never the roll number" cannot be bypassed by one of them.

The complete control list is in [11-security-plan.md](11-security-plan.md). The mandatory rules are in [SECURITY_RULES.md](../SECURITY_RULES.md).

## 8. Storage integration

- **TR-STO-1.** One private bucket. The browser never receives the service key.
- **TR-STO-2.** Uploads go through the backend, which validates and then stores the file.
- **TR-STO-3.** Downloads use signed URLs created by the backend after an authorization check, valid for at most 5 minutes.
- **TR-STO-4.** The database stores metadata and the storage path. It never stores file bytes.
- **TR-STO-5.** Storage access is behind a Java interface (`StorageClient`) with an in-memory implementation for tests.

## 9. AI provider integration

- **TR-AI-1.** All AI calls are made by the backend through the `AiProvider` interface. See [ADR 0005](decisions/0005-ai-integration-behind-java-interface.md).
- **TR-AI-2.** A `MockAiProvider` is the default and is used in all automated tests. Tests MUST NOT call a real provider.
- **TR-AI-3.** Total time budget per AI request is 25 seconds, below Heroku's 30-second router limit.
- **TR-AI-4.** AI output is parsed into typed Java objects and validated for structure and size before it is returned. This validation does not check that the content obeys the AI rules; that risk is handled as described in [14-AI-security-and-privacy.md](14-AI-security-and-privacy.md) §3.
- **TR-AI-5.** The per-user limits, the application-wide daily ceiling and the kill switch are defined once, in [12-AI-features-spec.md](12-AI-features-spec.md) §2, and are enforced before any provider call.

Details are in [15-AI-features-handoff.md](15-AI-features-handoff.md).

## 10. Testing requirements

- **TR-TEST-1.** `./mvnw verify` runs all tests and MUST pass before a change is merged.
- **TR-TEST-2.** Every service class with business logic has unit tests.
- **TR-TEST-3.** Every protected endpoint has a test for unauthenticated access, wrong role, and (where applicable) wrong owner.
- **TR-TEST-4.** Repository and migration tests run against real PostgreSQL through Testcontainers.

The full approach is in [17-testing-strategy.md](17-testing-strategy.md).

## 11. Build and deployment

- **TR-BLD-1.** The build produces one executable JAR containing the API and the static frontend.
- **TR-BLD-2.** The Java version for Heroku is pinned in `system.properties`.
- **TR-BLD-3.** The start command is in `Procfile` and binds to the `PORT` variable.
- **TR-BLD-4.** No secrets appear in the repository, the JAR or build logs.

Details are in [18-production-plan.md](18-production-plan.md).

## 12. Performance and reliability

| ID | Requirement |
| --- | --- |
| TR-PERF-1 | Non-AI endpoints respond in under 1 second at the 95th percentile with the demo dataset on the chosen dyno. |
| TR-PERF-2 | List endpoints return at most 100 items per page. |
| TR-PERF-3 | No request loads an unbounded number of rows. N+1 query patterns are fixed before the change is committed. |
| TR-REL-1 | A failure of the AI provider or storage never causes a failure in academic features. |
| TR-REL-2 | External calls have explicit connect and read timeouts. No call waits indefinitely. |
| TR-REL-3 | The application starts with the AI provider unset (`AI_PROVIDER=mock`) and with `AI_ENABLED=false`. |
| TR-REL-4 | `GET /actuator/health` reports database connectivity. |

## 13. Logging and error handling

- **TR-LOG-1.** Logs go to standard output. Heroku collects them.
- **TR-LOG-2.** Every request gets an ID, included in log lines and returned as `traceId` in error responses.
- **TR-LOG-3.** Logs MUST NOT contain passwords, session IDs, API keys, signed URLs, or the text of AI prompts and responses.
- **TR-LOG-4.** One global exception handler converts exceptions to problem details. Stack traces and SQL never reach the client.
- **TR-LOG-5.** Expected failures (validation, not found, access denied) are logged at `INFO` or `WARN`. Unexpected failures are logged at `ERROR` with the stack trace.

## 14. Dependencies and licensing

- **TR-DEP-1.** A new dependency needs the owner's approval, a stated purpose and a check that it is maintained.
- **TR-DEP-2.** Only permissive open-source licences (Apache 2.0, MIT, BSD, EPL) are accepted.
- **TR-DEP-3.** Prefer the Java standard library and Spring over a new dependency.
- **TR-DEP-4.** Frontend libraries are vendored at a pinned version with their licence file.
