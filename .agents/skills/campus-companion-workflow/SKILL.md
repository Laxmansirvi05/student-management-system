---
name: campus-companion-workflow
description: Apply Campus Companion's approved documentation, architecture, security boundaries, and task workflow before planning or changing project files.
---

# Campus Companion Workflow

## Mandatory first steps
1. Read `AGENTS.md`, `SECURITY_RULES.md`, and `AI_AGENT_SECURITY_RULES.md`.
2. Read the relevant sections of `docs/02-PRD.md`, `docs/03-scope-and-roadmap.md`, `docs/04-TRD.md`, `docs/05-architecture.md`, `docs/08-database-design.md`, `docs/09-api-contract.md`, and `docs/16-implementation-plan.md`.
3. Check the current task, open owner decisions, git status, active branch, and current implementation state before changing anything.
4. Treat approved docs as source of truth. If a required decision is unresolved or documents conflict, stop at the boundary, identify the conflict, and ask the owner rather than inventing a decision.
5. Follow GSD task/phase planning when installed. Keep a single primary orchestrator; do not duplicate plans across several autonomous systems.

## Architecture invariants
- Java 21 and Spring Boot are the sole application backend.
- Use plain HTML, CSS, and browser JavaScript. Do not introduce a frontend framework or bundler.
- Use Supabase PostgreSQL through Java/JDBC/JPA and Supabase Storage through a Java server-side adapter.
- Use Spring MVC controllers -> services -> repositories. Keep business logic in readable, testable Java services.
- Follow the API contract and `docs/openapi.yaml`. Update both contract sources whenever an endpoint changes.
- Use Flyway migrations only for schema changes; do not use Hibernate schema auto-update.
- Use Spring Security session-cookie authentication as documented. Never silently introduce JWT or Supabase Auth.
- Do not connect this project to the existing Opportunity Radar Supabase project. Use only a dedicated, owner-approved Campus Companion project.
- Treat institutional-email and roll-number temporary-password policy as synthetic-demo-only until the owner approves a safer real-student onboarding policy.

## Task execution
1. Pick one implementation-plan task and state its acceptance criteria and dependencies.
2. Make the smallest coherent change that satisfies the task.
3. Add or update tests and documentation in the same change.
4. Run the relevant tests and required quality checks; do not disable tests to force a pass.
5. Inspect the final diff for scope creep, secrets, generated noise, and unrelated modifications.
6. Report files changed, commands run, exact results, remaining issues, and any decision needed.

## Stop conditions
Stop and ask the owner before production access, paid-resource creation, schema destruction, secret/credential changes, publishing data, deployment, or a change to an approved architecture or P0 scope.
