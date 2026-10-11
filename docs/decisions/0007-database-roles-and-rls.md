# 0007. Two database roles, a private schema and the Data API switched off

- Status: Proposed
- Date: 2026-10-11
- Deciders: Laxman Sirvi
- Refines: [0002](0002-supabase-postgresql-and-storage.md)

## Context

[0002](0002-supabase-postgresql-and-storage.md) said that enabling Row Level Security (RLS) with no policies would keep Supabase's Data API from exposing application tables, while the application's own database user, as the owner of the tables, would be unaffected. A documentation audit found three problems with relying on that.

1. **RLS does not restrict a table's owner**, nor any role with `BYPASSRLS`. Supabase documents that `service_role` bypasses RLS, and treats its administrative `postgres` role as a superuser for this purpose. So "RLS is on" says nothing about what those roles can do.
2. **The application was to run as the table owner.** A leaked runtime password would then allow dropping tables, removing triggers and rewriting the audit log.
3. **The Data API was only blocked by RLS.** Supabase documents two layers for the Data API, grants and RLS, and notes that on existing projects new tables in `public` are automatically granted to `anon`, `authenticated` and `service_role`. It also documents that the Data API can be switched off entirely when an application does not use it.

The application connects to the database as one role for every user, so the database cannot enforce "this student, these rows" whatever is chosen here.

## Options considered

1. **Keep the model in 0002.** Simple, but it rests on the misunderstanding above and leaves one all-powerful runtime credential.
2. **Run as `postgres` and rely only on the Java checks.** Simplest of all. Any leak of the runtime password is a total compromise, and Supabase advises against giving the `postgres` password to services.
3. **Per-user database sessions with real RLS policies** (set the student's identity on each connection and write ownership policies). This would make the database a second authorization layer, but it moves access logic into SQL, complicates connection pooling, and contradicts the Java-first goal.
4. **Two least-privilege roles, a private schema, the Data API switched off, and RLS as a backstop.**

## Decision

Adopt option 4. The full specification is [08-database-design.md](../08-database-design.md) §11 and §12. In short:

- **The Spring Boot application is the only authorization boundary for users.** Database controls limit what each credential can do and close access paths around the application.
- **Two roles.** `cc_migrator` owns schema `app` and runs Flyway. `cc_app` is used at runtime, owns nothing, and holds only the row-level privileges each table needs. The application never connects as `postgres`.
- **The Data API is closed three ways:** it is switched off; application tables are in schema `app`, which is not exposed; and `anon`, `authenticated` and `service_role` hold no grants there.
- **RLS is enabled on every table** with policies for `cc_app` only. It is a backstop against a mistaken grant or a re-enabled Data API. It is not the access control between students, and it does not restrict the owner or bypass roles.
- **The audit log is append-only for the runtime role**, by privileges, policies and triggers.

## Trade-offs

- More setup: two roles, two passwords, explicit grants and policies in every migration, and a start-up script so tests use the same roles.
- More that can go wrong at first: a missing grant or policy makes a table unusable until a migration fixes it. The database tests are there to catch this before release.
- The policies are `USING (true)`, which can look as if they do nothing. Their purpose is to admit exactly one role; the document says so explicitly.
- The audit log is still not protected from someone holding the migration or administrative credential.
- Several points depend on Supabase behaviour that has not been tested on this project.

## Consequences

- New configuration: `DB_MIGRATION_USERNAME` and `DB_MIGRATION_PASSWORD`.
- Task `T0-03` creates the roles and schema and switches the Data API off; `T1-03` adds the role and privilege tests.
- A leaked runtime password allows reading and changing rows, but not changing the schema or altering the audit log.
- The RLS consequence in 0002 is superseded by this record.
- Unverified points are listed in [08-database-design.md](../08-database-design.md) §11.8. If custom roles cannot be used through the session pooler, work stops and a variant is decided in a new record. Running as `postgres` is not an acceptable silent fallback.

## Sources checked on 2026-10-11

Supabase documentation: "Securing your API" (grants and RLS as two layers; automatic grants in `public`; switching the Data API off), "Using Custom Schemas" (`public` is exposed by default; other schemas only when added), "Postgres Roles" (`service_role` bypasses RLS; create a separate user for each service rather than sharing the `postgres` password), "Row Level Security" (service keys and `bypassrls`), and "Connect to your database" (session pooler on port 5432 over IPv4; transaction mode does not support prepared statements). The first three were read in full; the last two were searched for the specific statements used here.

That a table owner is exempt from RLS unless `FORCE ROW LEVEL SECURITY` is set is standard PostgreSQL behaviour and was not re-checked against the PostgreSQL manual during this audit.
