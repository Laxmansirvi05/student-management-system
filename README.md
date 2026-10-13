# Campus Companion

A Java-first student management and learning platform. It combines a student's academic records with study resources, practice tests and an AI study assistant in one web application.

> **Project status: Phase 1 built, Phase 2 started.** The project skeleton, configuration, first database migration, core entities, error handling, server-side login and sessions, lockout, and the forced password change are built and tested (tasks `T1-01` to `T1-11`, `T2-01` and `T2-13` in the [implementation plan](docs/16-implementation-plan.md)). The login and change-password pages and the empty page layouts exist, and a seeder can create synthetic student accounts; the student profile is the first connected feature, and the other student pages are finished designs that show sample data until their features are built. The specifications below are drafts awaiting owner review.
>
> **Revised on 2026-10-11:** a consistency audit of the documents, and a change to login. Students now log in with an institutional email address, and a new student's first password is the roll number, which must be changed at first login. That first-password rule is a recorded security exception for demonstration with synthetic data only: see [decision 0008](docs/decisions/0008-institutional-email-login.md). The database access model is in [decision 0007](docs/decisions/0007-database-roles-and-rls.md).

## The problem

Students check attendance, marks, timetables and notices in different places. Notes and previous-year papers are scattered across chat groups. There is no simple way to test yourself on a unit and see which topics are weak. A portal that only displays records gives students no reason to come back.

## What it does

| For students | For administrators |
| --- | --- |
| Dashboard, profile, attendance, marks, SGPA and CGPA | Manage student accounts |
| Timetable, notices and deadlines | Enter attendance, marks and results |
| Searchable notes and previous-year papers, with bookmarks | Manage subjects, units and timetables |
| Quick Exam: timed practice tests with instant scoring | Upload learning resources |
| AI study assistant: explain, summarize, generate questions | Maintain the question bank |
| Weak-topic tracking and revision recommendations | Publish notices; review the audit log |

## Technology

| Layer | Choice |
| --- | --- |
| Backend | Java 21, Spring Boot (Web, Security, Data JPA, Validation), Maven |
| Frontend | HTML, CSS and JavaScript, served by Spring Boot |
| Database | Supabase PostgreSQL through JDBC, migrations with Flyway |
| File storage | Supabase Storage, private bucket |
| AI | External AI provider called from a Java service |
| Tests | JUnit 5, Mockito, Spring Boot Test |
| Hosting | Heroku |

There is no Node.js or Express backend. All application logic is Java.

## Architecture

```mermaid
flowchart LR
    B["Browser<br/>HTML, CSS, JavaScript"] -->|"HTTPS, REST /api/v1"| S["Spring Boot app on Heroku<br/>controllers, services, repositories"]
    S -->|"JDBC, session pooler"| D[("Supabase PostgreSQL")]
    S -->|"HTTPS, service key"| F[("Supabase Storage<br/>private bucket")]
    S -->|"HTTPS, API key"| A["AI provider API"]
    B -.->|"short-lived signed URL"| F
```

Details are in [docs/05-architecture.md](docs/05-architecture.md).

## Getting started

These steps apply once the Spring Boot project exists (task `T1-01`).

**Prerequisites:** JDK 21, Git, a Supabase project, and Docker for integration tests.

1. Clone the repository.

   ```bash
   git clone https://github.com/Laxmansirvi05/student-management-system.git
   ```

2. Create your local configuration from the template and fill in the values. Never commit `.env`.

   ```bash
   cp .env.example .env
   ```

3. Run the application. Flyway applies the database migrations on startup.

   ```bash
   ./mvnw spring-boot:run
   ```

4. Run the tests.

   ```bash
   ./mvnw verify
   ```

5. Build the executable JAR.

   ```bash
   ./mvnw -DskipTests package
   ```

Deployment is described in [docs/18-production-plan.md](docs/18-production-plan.md).

## Documentation

Status values: **Draft** (written, not yet reviewed by the owner), **Approved**, **Needs revision**.

| # | Document | Purpose | Status |
| --- | --- | --- | --- |
| 01 | [Project charter](docs/01-project-charter.md) | Vision, constraints, open decisions | Draft |
| 02 | [PRD](docs/02-PRD.md) | Functional requirements | Draft |
| 03 | [Scope and roadmap](docs/03-scope-and-roadmap.md) | What gets built, in what order | Draft |
| 04 | [TRD](docs/04-TRD.md) | Technical requirements | Draft |
| 05 | [Architecture](docs/05-architecture.md) | Components and flows | Draft |
| 06 | [App flow](docs/06-app-flow.md) | User journeys | Draft |
| 07 | [UI/UX spec](docs/07-ui-ux-spec.md) | Interface rules | Draft |
| 08 | [Database design](docs/08-database-design.md) | Schema | Draft |
| 09 | [API contract](docs/09-api-contract.md) and [openapi.yaml](docs/openapi.yaml) | REST API | Draft |
| 10 | [Authorization matrix](docs/10-authorization-matrix.md) | Who can do what | Draft |
| 11 | [Security plan](docs/11-security-plan.md) | Threats and controls | Draft |
| 12 | [AI features spec](docs/12-AI-features-spec.md) | What the AI assistant does | Draft |
| 13 | [AI rules](docs/13-AI-rules.md) | How the AI assistant behaves | Draft |
| 14 | [AI security and privacy](docs/14-AI-security-and-privacy.md) | Protecting users from AI risks | Draft |
| 15 | [AI features handoff](docs/15-AI-features-handoff.md) | AI implementation guide | Draft |
| 16 | [Implementation plan](docs/16-implementation-plan.md) | Ordered tasks | Draft |
| 17 | [Testing strategy](docs/17-testing-strategy.md) | How we prove it works | Draft |
| 18 | [Production plan](docs/18-production-plan.md) | Deployment | Draft |
| 19 | [Operations runbook](docs/19-operations-runbook.md) | What to do when it breaks | Draft |
| 20 | [Risk register](docs/20-risk-register.md) | Risks and mitigations | Draft |
| | [Decision records](docs/decisions/README.md) | Why key choices were made | See index |
| | [Demo and viva guide](docs/demo-and-viva-guide.md) | Presentation preparation | Draft |

Rules for everyone working in this repository:

- [AGENTS.md](AGENTS.md): how coding agents work here.
- [SECURITY_RULES.md](SECURITY_RULES.md): mandatory application security rules.
- [AI_AGENT_SECURITY_RULES.md](AI_AGENT_SECURITY_RULES.md): what a coding agent may and may not do.
- [CONTRIBUTING.md](CONTRIBUTING.md): the single-branch workflow, commits, reviews.

Work is tracked in [GitHub Issues](https://github.com/Laxmansirvi05/student-management-system/issues). Do not report security vulnerabilities in a public issue; see [CONTRIBUTING.md](CONTRIBUTING.md#reporting-security-problems).
