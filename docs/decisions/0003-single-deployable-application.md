# 0003. Frontend and backend deployed as one application

- Status: Accepted
- Date: 2026-10-10
- Deciders: Laxman Sirvi

## Context

The frontend is plain HTML, CSS and JavaScript. It could be hosted separately from the API or served by the same Spring Boot application. The project has one developer, a small budget and a single Heroku dyno.

## Options considered

1. **Separate frontend hosting (a static host) and a separate API.** Independent deploys and a CDN, but two deployments, cross-origin configuration (CORS), and cross-site cookies that complicate session security.
2. **A frontend framework with its own build pipeline.** Richer interface, but a second toolchain and less time for the Java work being assessed.
3. **Static files served by Spring Boot from `src/main/resources/static`, in the same JAR as the API.**

## Decision

Build and deploy one executable JAR that serves both the static frontend and the REST API from the same origin.

## Trade-offs

- A change to a page requires redeploying the whole application.
- Static files are served by the application server, not a CDN, which is slower at scale. Scale is not a concern here.
- Without a framework, the frontend needs disciplined structure (shared `api.js`, shared components and tokens) to stay consistent.

## Consequences

- Same origin means no CORS configuration, simple `SameSite=Lax` session cookies and straightforward CSRF protection.
- One build, one deployment, one URL, one dyno.
- Spring Security can protect the `/student/**` and `/admin/**` pages as well as the API.
- A strict Content Security Policy is practical because all scripts come from the application itself.
- If the frontend is ever separated, this record must be superseded and the session and CSRF design in [0004](0004-session-cookie-authentication.md) revisited.
