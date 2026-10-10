# 0006. Flyway for database migrations

- Status: Proposed
- Date: 2026-10-10
- Deciders: Laxman Sirvi

## Context

The schema will grow over six phases and must be identical in development, tests and production. Changes need to be repeatable, reviewable and tied to the code version that depends on them. Hibernate can create or update tables automatically, and SQL can also be run by hand in the Supabase dashboard.

## Options considered

1. **Hibernate `ddl-auto=update`.** No extra work, but changes are implicit, cannot be reviewed, do not handle renames or data changes, and are unsafe in production.
2. **Manual SQL in the Supabase SQL editor.** Direct, but nothing records what was run, environments drift apart, and tests cannot reproduce the schema.
3. **Liquibase.** Capable and database-neutral, but its changelog format is more to learn, and portability across databases is not needed.
4. **Flyway with plain, versioned SQL files.**

## Decision

Use Flyway with versioned SQL migrations in `src/main/resources/db/migration`. Hibernate runs with `ddl-auto=validate`, so it checks the schema but never changes it. Migrations run when the application starts.

## Trade-offs

- Every schema change needs a hand-written SQL file as well as the entity change.
- Migrations are forward-only in the free edition of Flyway; there is no automatic undo. Mistakes are fixed with a new migration.
- Running migrations at startup is only safe while there is one application instance.
- A failed migration stops the application from starting.

## Consequences

- The SQL in the repository is the real schema, readable in a viva, and must match [08-database-design.md](../08-database-design.md).
- Integration tests build the database from the same migrations, so they test the real schema.
- An applied migration is never edited; Flyway detects this by checksum.
- Each migration enables Row Level Security on the tables it creates.
- Demo data is never placed in migrations.
- Destructive migrations require approval and a backup, as stated in [AGENTS.md](../../AGENTS.md).
