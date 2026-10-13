# 18. Production Plan

> Status: Draft v0.2 · Owner: Laxman Sirvi · Last updated: 2026-10-11

This document explains how the application moves from development to a live Heroku deployment. Items marked **Verify** must be checked against the platform's current documentation before use, because plans, prices and defaults change. Record what you find in this document.

## 1. Hosting architecture

| Part | Where | Notes |
| --- | --- | --- |
| Application (API and frontend) | Heroku, one web dyno | One executable JAR |
| Database | Supabase PostgreSQL | Reached through the session pooler |
| Files | Supabase Storage | One private bucket |
| AI | External provider | Called from the dyno |
| Source and CI | GitHub | Actions runs `./mvnw verify` |

The diagram is in [05-architecture.md](05-architecture.md#3-deployment).

## 2. Environments

| Environment | Application | Database | AI | Data |
| --- | --- | --- | --- | --- |
| `dev` | Local machine | Supabase project (dev) or local PostgreSQL | `mock` | Seeded, synthetic |
| `test` | Local and CI | Testcontainers PostgreSQL | `mock` | Created by tests |
| `prod` | Heroku | Supabase project (prod) | Real provider | Synthetic demo data |

**Verify:** whether the free Supabase allowance covers two projects. If not, use local PostgreSQL for `dev` and the single Supabase project for `prod`. Development and production never share a database.

Each environment sets its own `STUDENT_EMAIL_DOMAIN`. Development and tests use a reserved example domain such as `university.example`; production uses the official domain once the owner has confirmed it. The value is not carried over by copying a `.env` file from one environment to another. Changing it later does not rewrite accounts that already exist ([08-database-design.md](08-database-design.md) §8).

## 3. Java runtime and build configuration

Files in the repository root:

**`system.properties`**

```
java.runtime.version=21
```

**`Procfile`**

```
web: java $JAVA_OPTS -Dserver.port=$PORT -jar target/campus-companion.jar
```

- `pom.xml` sets `<finalName>campus-companion</finalName>` so the JAR name is stable.
- Heroku's Java buildpack runs the Maven build. **Verify** the buildpack's current default Maven goals and that it uses the Maven Wrapper.
- **Verify** that Java 21 and the chosen Spring Boot version are supported by the buildpack before fixing versions in task `T1-01`.
- Tests run in CI, not during the Heroku build.

## 4. Production application settings

Set in `application-prod.yml` (no secrets in this file):

| Setting | Value | Why |
| --- | --- | --- |
| `server.forward-headers-strategy` | `native` | Trust Heroku's forwarded protocol header so HTTPS is detected |
| Session cookie | `secure=true`, `http-only=true`, `same-site=lax` | |
| Session timeout | 30 minutes | |
| HTTPS | Require secure channel; HSTS on | |
| `spring.jpa.hibernate.ddl-auto` | `validate` | |
| `spring.flyway.enabled` | `true` | Migrate at startup |
| Hikari `maximum-pool-size` | 5 | Stay within pooler limits |
| Actuator exposure | `health` only, no details | |
| Error responses | No stack traces, no messages from exceptions | |
| Multipart limits | 10 MB file, 11 MB request | |
| Logging | INFO to standard output | |
| Dev seeder | Disabled | |

## 5. Environment variables

Names are defined in [.env.example](../.env.example). In production they are Heroku config vars.

| Variable | Required in prod | Secret | Notes |
| --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | Yes | No | `prod` |
| `STUDENT_EMAIL_DOMAIN` | Yes | No | The official student email domain, for example `university.example`. A host name only, with no `@`. The application will not start without a valid value. Production uses the domain the owner confirmed (OQ-15); development and tests use a reserved example domain |
| `DB_URL` | Yes | No | `jdbc:postgresql://<session-pooler-host>:5432/postgres` with SSL required |
| `DB_USERNAME` | Yes | No | The runtime role, `cc_app`, in the user-name format the pooler requires |
| `DB_PASSWORD` | Yes | **Yes** | Password of `cc_app` |
| `DB_MIGRATION_USERNAME` | Yes | No | The migration role, `cc_migrator`. Used only by Flyway at startup |
| `DB_MIGRATION_PASSWORD` | Yes | **Yes** | Password of `cc_migrator` |
| `SUPABASE_URL` | Yes | No | |
| `SUPABASE_SERVICE_KEY` | Yes | **Yes** | Full storage access. Server only. |
| `SUPABASE_STORAGE_BUCKET` | Yes | No | |
| `AI_ENABLED` | Yes | No | Kill switch |
| `AI_PROVIDER` | Yes | No | `mock` or the real provider's name |
| `AI_API_KEY` | With a real provider | **Yes** | |
| `AI_MODEL` | With a real provider | No | |
| `AI_DAILY_LIMIT_PER_USER` | No | No | Default 30 |
| `AI_DAILY_LIMIT_GLOBAL` | No | No | Application-wide daily ceiling across all users. Default 500, provisional (OQ-11). Set it deliberately before release |
| `AI_MAX_OUTPUT_TOKENS` | No | No | Default 1200 |
| `ADMIN_BOOTSTRAP_EMAIL` | First start | No | Login email of the first admin. Not a student address |
| `ADMIN_BOOTSTRAP_PASSWORD` | First start | **Yes** | A strong password chosen by the owner, never a roll number. Remove after the admin has changed it |
| `DEMO_SEED_ENABLED` | No | No | Default `false`. Set to `true` for one start to load synthetic demo data, then remove it |
| `ATTENDANCE_REQUIRED_PERCENT` | No | No | Default 75 |
| `JAVA_OPTS` | Recommended | No | Memory settings sized for the dyno |
| `PORT` | Set by Heroku | No | Do not set it yourself |

`.env.example` lists every variable above except `JAVA_OPTS` and `PORT`, which exist only on Heroku. Every variable in `.env.example` appears in this table. A variable that is in one and not the other is a defect.

### Fixed defaults

These are set in `application.yml`, not by environment variables. Changing one is a reviewed code change, and the document named is updated with it.

| Setting | Value | Defined in |
| --- | --- | --- |
| Session idle timeout | 30 minutes | [11-security-plan.md](11-security-plan.md) §4 |
| Login lockout | 5 consecutive failures for an email address, 15 minutes | [02-PRD.md](02-PRD.md) FR-AUTH-005 |
| Password length | 8 to 72 characters | [02-PRD.md](02-PRD.md) FR-AUTH-003 |
| Login attempts per IP address | 20 per 10 minutes | [09-api-contract.md](09-api-contract.md) §6 |
| API calls per session | 120 per minute | [09-api-contract.md](09-api-contract.md) §6 |
| JSON body limit | 64 KB | [09-api-contract.md](09-api-contract.md) §6 |
| Upload limit | 10 MB, PDF only | [09-api-contract.md](09-api-contract.md) §6 |
| Signed link lifetime | 5 minutes at most | [04-TRD.md](04-TRD.md) TR-STO-3 |
| Quiz time per question | 60 seconds | [02-PRD.md](02-PRD.md) FR-QZ-002 |
| Quiz submission grace period | 15 seconds | **Proposed here** and built as `QuizService.GRACE`. The PRD says only "a short grace period"; the owner may change the value |
| Progress thresholds | At least 5 answers; latest 20 answers; 60% and 80% | [02-PRD.md](02-PRD.md) FR-PRG-001 |
| AI per-minute limit, concurrency, time budget | 5; 2; 25 seconds | [12-AI-features-spec.md](12-AI-features-spec.md) §2 |
| Database pool size | 5 connections | [04-TRD.md](04-TRD.md) TR-DB-2 |

### Secret handling

- The owner sets secret values directly with `heroku config:set` or the dashboard. They are never pasted into chats, issues, documents or commits.
- Secrets are different in every environment.
- After setting, confirm the **names** with `heroku config` without sharing the output.

## 6. Supabase connection settings

- Use the **session pooler** connection string from the Supabase dashboard. Do not use the transaction pooler: Supabase documents that transaction mode does not support prepared statements, which Hibernate uses.
- The application connects as `cc_app` and Flyway as `cc_migrator`. Neither is `postgres`. **Verify** in `T0-03` that custom roles can connect through the session pooler and which user-name format they need.
- The Data API is switched off in the dashboard, and schema `app` is not listed under exposed schemas. The model is in [08-database-design.md](08-database-design.md) §11.
- Supabase documents the direct connection as IPv6 unless the paid IPv4 add-on is enabled, and the shared pooler's session mode (port 5432) as IPv4 on every plan, recommended for a persistent backend on an IPv4-only network. That is why the session pooler is the default here. **Verify** whether Heroku's outbound connections are IPv4 only.
- Require TLS on the JDBC connection.
- Confirm the pooler's connection limit for the plan and keep the Hikari pool below it.
- Storage: private bucket named by `SUPABASE_STORAGE_BUCKET`; no public access; no policies for `anon` or `authenticated`.

## 7. Database migration procedure

Migrations are applied by Flyway when the application starts. With one dyno this is safe. Flyway uses the migration role's credentials (`DB_MIGRATION_*`); the running application uses the runtime role's.

For each release that contains a migration:

1. The migration has been applied to a fresh Testcontainers database in CI.
2. The migration has been applied to the dev database.
3. For a migration that changes or removes existing data: take a `pg_dump` backup of production first, and get the owner's approval.
4. Deploy. Watch the startup log for Flyway's "Successfully applied" line.
5. Check `/actuator/health`.

Migrations are forward-only. A bad migration is corrected by a new migration, not by editing or deleting the old one.

## 8. Build and release process

1. Work is committed to `main` after the owner's review, and CI on `main` is green.
2. The owner tags the commit (`v0.1.0`).
3. The owner deploys that commit to Heroku. **Verify** the chosen method (Git push to the Heroku remote, or GitHub integration with manual deploy). Automatic deploys are left off so that every release is deliberate.
4. Heroku builds the JAR and starts the dyno.
5. The owner runs the smoke test in section 11.

## 9. Health checks

- `GET /actuator/health` returns `{"status":"UP"}` when the application and database are reachable.
- After each deploy, check it manually.
- Optional: an external uptime monitor pinging the health URL during the demo period. Note that a monitor keeps a sleeping dyno awake, which uses dyno hours.

## 10. HTTPS and domain

- Use the default `https://<app-name>.herokuapp.com` address. Heroku provides TLS for it.
- The application redirects HTTP to HTTPS and sends HSTS.
- A custom domain is not needed for the assignment. If one is added later, enable Heroku's managed certificates and update the Content Security Policy only if origins change.
- The CSP `frame-src` must contain the production Supabase project's storage host.

## 11. Pre-deployment checklist

- [ ] CI is green on the commit being released.
- [ ] Release acceptance criteria in [17-testing-strategy.md](17-testing-strategy.md) §14 are met.
- [ ] Security release checklist in [11-security-plan.md](11-security-plan.md) §16 is complete.
- [ ] All required config vars are set (names checked).
- [ ] `SPRING_PROFILES_ACTIVE=prod`.
- [ ] Storage bucket exists and is private.
- [ ] The Data API is off; `cc_app` and `cc_migrator` exist; the application does not connect as `postgres`.
- [ ] `AI_DAILY_LIMIT_PER_USER` and `AI_DAILY_LIMIT_GLOBAL` are set to values the owner chose.
- [ ] `DEMO_SEED_ENABLED` is not set.
- [ ] `STUDENT_EMAIL_DOMAIN` is the confirmed official domain (OQ-15), and `ADMIN_BOOTSTRAP_EMAIL` is set.
- [ ] The deployment will hold synthetic data only, as security exception EX-AUTH-01 requires (OQ-17).
- [ ] A database backup exists if the release includes a risky migration.
- [ ] The previous release number is noted, for rollback.
- [ ] Heroku plan and expected cost confirmed.

### Smoke test after deployment

1. Health endpoint is UP.
2. HTTP redirects to HTTPS.
3. Login page loads with no console errors and security headers present.
4. Admin logs in.
5. A newly provisioned student logs in with an institutional email and the roll number, is made to change the password, and then sees the dashboard.
6. A resource previews (signed link works).
7. A 5-question test can be taken and scored.
8. One AI request succeeds, or returns the expected "unavailable" message if AI is off.
9. Logs show no errors and no secrets.

## 12. Rollback procedure

**Application problem, no schema change in the release**

1. Roll back to the previous release with `heroku releases:rollback`.
2. Check health and repeat the smoke test.
3. Investigate using the logs.

**Release included a migration**

Rolling back the code does not undo the migration. Choose one:

- If the old code still works with the new schema (additive migration): roll back the application as above.
- Otherwise: fix forward with a corrective release.
- Last resort, for data damage: restore the database from the backup taken before the release (see the [runbook](19-operations-runbook.md)), then roll back the application.

**AI problem only:** set `AI_ENABLED=false`. No rollback needed.

## 13. Cost and resources

**Verify all prices on the providers' pricing pages before deploying. Nothing below is a quoted price.**

| Item | Consideration |
| --- | --- |
| Heroku dyno | Heroku has no free dynos. Choose the lowest tier that fits. Low-cost dynos sleep after inactivity, so the first request after a pause is slow; wake the app before a demo. |
| Dyno memory | Small dynos have limited memory. Set `JAVA_OPTS` to cap the heap, and keep the Hikari pool small. |
| Dyno restarts | Dynos restart about daily. In-memory sessions are lost, so users log in again. |
| Request timeout | The router ends requests after 30 seconds, which is why AI calls are capped at 25 seconds and uploads at 10 MB. |
| Supabase | The free plan has database, storage and bandwidth limits, and inactive free projects can be paused. Open the dashboard and the app shortly before a demo. |
| AI provider | Pay per use. Set a spending limit at the provider. The per-user limit, the application-wide ceiling (`AI_DAILY_LIMIT_GLOBAL`) and the kill switch bound the worst case. The daily volume bound is worked out in [12-AI-features-spec.md](12-AI-features-spec.md) §2. |
| GitHub | Free for a public repository, including Actions minutes within limits. |

To stop spending after the assignment: scale the dyno to zero or delete the app, revoke the AI key, and pause or delete the Supabase project after taking a final backup.

## 14. Release acceptance

A release is live and accepted when the pre-deployment checklist was complete, the smoke test passed, and scenarios E1 to E3 passed on the production URL. Record the release tag, date and result in the GitHub release notes.
