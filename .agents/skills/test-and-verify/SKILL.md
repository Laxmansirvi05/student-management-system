---
name: test-and-verify
description: Turn Campus Companion acceptance criteria into unit, integration, security, browser, and release checks with honest evidence.
---

# Test and Verify

## Before testing
- Identify the implementation-plan task and acceptance criteria.
- Find the task's requirement in the traceability matrix in `docs/16-implementation-plan.md` and its acceptance test and scenario IDs (`AT-`, `AU`, `PV`, `Z`, `UP`) in `docs/17-testing-strategy.md`. Name the IDs a test covers.
- Check the current Git branch and working tree; do not overwrite unrelated work.
- Use synthetic fixtures only. Never use real student data or production credentials.

## Test levels
- Unit: JUnit 5 and Mockito for attendance, results/CGPA, quiz scoring, progress logic, input validation, and AI response parsing.
- Web/security: Spring MVC tests for request/response contracts, session state, roles, CSRF, error formats, and access denial.
- Database: PostgreSQL integration tests via approved Testcontainers. Do not substitute H2 for PostgreSQL.
- External services: mock AI and Storage adapters in automated tests; never call a paid AI provider from the normal test suite.
- Browser: use Playwright on demand once the UI exists; inspect console/network errors for relevant flows.

## Required high-risk cases
- Student A cannot read or mutate Student B's academic records.
- A student cannot call admin endpoints or alter role/ownership fields.
- Expired/invalid sessions and missing CSRF tokens fail safely.
- Invalid and oversized uploads are rejected.
- Quiz timer, submission, unanswered questions, duplicate answers, and scoring boundaries behave as specified.
- AI malformed output, prompt injection, provider timeout, rate limits, kill switch, and quota exhaustion fail safely.
- No password, token, API key, signed URL or sensitive prompt text leaks into logs.

## Commands and evidence
- Before the Maven project exists, do not pretend Maven checks can run.
- Once initialized, start with `./mvnw test`; use `./mvnw verify` for the full suite when Docker/Testcontainers are available.
- Record each command, exit code, and relevant summary.
- Never disable a test to get green output. If a check cannot run, give the exact blocker and the next required action.
- End with a pass/fail/block table linked to the acceptance criteria.
