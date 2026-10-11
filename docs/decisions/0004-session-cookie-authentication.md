# 0004. Session-cookie authentication with Spring Security

- Status: Proposed
- Date: 2026-10-10
- Deciders: Laxman Sirvi

## Context

Students and administrators must log in, and every request must be tied to a user and a role. The frontend and API share one origin ([0003](0003-single-deployable-application.md)). The two usual approaches are server-side sessions identified by a cookie, and stateless JSON Web Tokens (JWT) held by the browser.

## Options considered

1. **JWT access tokens stored in the browser.** Stateless and popular in tutorials. But tokens kept in JavaScript-readable storage can be stolen by any script injection, logout and revocation are hard without adding server state, and refresh-token handling adds complexity. Its main benefits (multiple services, cross-origin clients) do not apply here.
2. **Supabase Auth.** Managed, but moves identity out of the Java application and contradicts [0002](0002-supabase-postgresql-and-storage.md).
3. **Server-side sessions with an `HttpOnly` cookie, managed by Spring Security.**

## Decision

Use Spring Security with server-side HTTP sessions. The browser holds only an `HttpOnly`, `Secure`, `SameSite=Lax` session cookie. Passwords are hashed with BCrypt. CSRF protection uses a cookie-to-header token. Sessions are stored in application memory in the first release.

## Trade-offs

- The server holds state. With in-memory sessions, a dyno restart (which Heroku does about daily) logs everyone out.
- The application cannot run on more than one dyno without shared session storage.
- Cookies require CSRF protection, which adds a token step to the frontend.

## Consequences

- JavaScript can never read the session identifier, which limits the damage of a script-injection bug.
- Logout, lockout, password change and deactivation take effect immediately because the server controls the session.
- The frontend must fetch a CSRF token and send it on every state-changing request; this is centralised in `api.js`.
- Storing sessions in the database (Spring Session JDBC) is a P1 follow-up that removes the restart problem. It adds a dependency and so needs approval.
- JWT remains on the not-approved list in [04-TRD.md](../04-TRD.md) unless this record is superseded.

## Clarification, 2026-10-11

The owner's change request of this date replaces the roll number with the institutional email address as the login identifier ([0008](0008-institutional-email-login.md)). That changes **what a user types**, not how authentication works. Everything decided in this record stands:

- Spring Security still authenticates the user and manages the session. No JWT, no Supabase Auth, no second backend.
- The session still holds only the account ID and the role. The email is used once, to find the account.
- The session identifier is renewed at login and again after a password change.
- A password change ends the user's other sessions. An admin reset or a deactivation ends all of them. With in-memory sessions this uses Spring Security's session registry; the rule must be kept if sessions move to the database.
- While an account's password is temporary, a filter in the security chain refuses every request except the four needed to change it.
