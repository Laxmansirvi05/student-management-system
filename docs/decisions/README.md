# Architecture Decision Records

A decision record (ADR) captures one significant technical decision: the context, the options considered, what was chosen, and what follows from it. Records are short and are not rewritten later. If a decision changes, a new record supersedes the old one.

## Index

| # | Decision | Status |
| --- | --- | --- |
| [0001](0001-spring-boot-as-sole-backend.md) | Spring Boot is the sole backend | Accepted |
| [0002](0002-supabase-postgresql-and-storage.md) | Supabase PostgreSQL and Storage, accessed only from Java | Accepted |
| [0003](0003-single-deployable-application.md) | Frontend and backend deployed as one application | Accepted |
| [0004](0004-session-cookie-authentication.md) | Session-cookie authentication with Spring Security | Proposed |
| [0005](0005-ai-integration-behind-java-interface.md) | AI integration behind a Java interface, server-side only | Proposed (provider not chosen) |
| [0006](0006-flyway-database-migrations.md) | Flyway for database migrations | Proposed |
| [0007](0007-database-roles-and-rls.md) | Two database roles, a private schema and the Data API switched off | Proposed |
| [0008](0008-institutional-email-login.md) | Institutional email login, with the roll number as a first-time password | Accepted as a security exception, for synthetic data only |

**Accepted** records reflect choices the owner has already made. **Proposed** records are recommendations written with the documentation and await the owner's approval.

## When to write one

Write a record when a decision:

- changes the approved stack or adds a dependency with wide effect,
- changes how components communicate or where data lives,
- changes the security model,
- would be expensive to reverse.

Do not write one for ordinary coding choices such as a class name or a helper method.

## Statuses

`Proposed` → `Accepted` → (later) `Superseded by NNNN` or `Deprecated`.

## Template

Copy this into a new file named `NNNN-short-title.md`, using the next number.

```markdown
# NNNN. Title

- Status: Proposed
- Date: YYYY-MM-DD
- Deciders: <names>

## Context

What problem or constraint forces a decision?

## Options considered

1. Option A: one or two lines.
2. Option B: one or two lines.

## Decision

What we chose, in one or two sentences.

## Trade-offs

What we give up by choosing this.

## Consequences

What becomes true, easier or harder as a result. What must now be done.
```
