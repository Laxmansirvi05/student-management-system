# 0002. Supabase PostgreSQL and Storage, accessed only from Java

- Status: Accepted
- Date: 2026-10-10
- Deciders: Laxman Sirvi

## Context

The application needs a relational database for academic records and somewhere to keep PDF files. It must cost little or nothing, be reachable from Heroku, and not require the owner to run a database server. Supabase provides managed PostgreSQL and object storage. Supabase also offers its own authentication, an auto-generated Data API and a JavaScript client, which are designed for browsers to talk to Supabase directly.

## Options considered

1. **Heroku Postgres plus a separate file store.** Fewer providers for the database, but file storage still needs another service, and it adds cost.
2. **MySQL.** Common in coursework, but Supabase does not offer it and nothing here needs it.
3. **Supabase with the browser using the Supabase client and Row Level Security policies.** Little backend code, but the logic and the access rules would live in SQL policies and JavaScript, not Java, which contradicts decision 0001.
4. **Supabase PostgreSQL through JDBC and Supabase Storage through its HTTP API, both called only from Spring Boot.**

## Decision

Use Supabase PostgreSQL through the PostgreSQL JDBC driver with Spring Data JPA, connected through the **session pooler**. Use one **private** Supabase Storage bucket, accessed by the backend with the service key. The browser never talks to Supabase, except to fetch a file through a short-lived signed URL that the backend issues after an authorization check. Supabase Auth, the Data API and the JavaScript client are not used.

## Trade-offs

- We do not use Supabase features that could save code (Auth, realtime, policies as the access model).
- Access control depends entirely on our Java code being correct, because the backend connects as one powerful database user.
- The service key and database password are high-value secrets that must be protected.
- Two external services (database and storage) share one provider, so a Supabase outage affects both.

## Consequences

- The transaction pooler must not be used, because it conflicts with Hibernate's prepared statements.
- Row Level Security is enabled on every table with no policies, so the unused Data API exposes nothing.
- PDF bytes are never stored in the database; tables hold metadata and the storage path.
- Files are uploaded through the backend, which limits their size (10 MB) because of Heroku's request timeout.
- Storage access is behind a `StorageClient` interface so tests need no network.
- Free-plan limits and project pausing are operational risks tracked in the [risk register](../20-risk-register.md).
