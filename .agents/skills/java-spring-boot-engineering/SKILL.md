---
name: java-spring-boot-engineering
description: Implement and review Java 21 and Spring Boot features using the Campus Companion layered architecture and test requirements.
---

# Java and Spring Boot Engineering

## Before editing
Read the feature requirements, TRD, architecture, database design, API contract, authorization matrix, and corresponding implementation-plan task.

## Required practices
- Use Java 21 and the Spring Boot version pinned in the approved TRD/pom.xml.
- Use constructor injection only.
- Keep controller classes focused on HTTP mapping, DTO validation, and response codes. Business logic belongs in services.
- Use request and response DTOs (Java records where appropriate); never serialize JPA entities directly.
- Use Spring Data repositories and bound parameters; never build SQL by string concatenation.
- Use Bean Validation for untrusted request data and an established global error format.
- Use `BigDecimal` and explicit rounding for marks, grade points, and percentages; use `java.time` for dates.
- Use `@Transactional` at the service boundary where a multi-record operation must be atomic.
- Follow Flyway for schema changes and keep Hibernate `ddl-auto=validate`.
- Do not introduce Lombok, a second backend, JWT authentication, H2 as a PostgreSQL substitute, or a frontend framework without an approved architecture decision.
- Keep secrets in environment configuration. Never hard-code real credentials or test against production accidentally.

## Expected verification
- Add JUnit 5 unit tests for business rules.
- Add Spring MVC/Security tests for API boundaries.
- Add PostgreSQL integration tests using the approved Testcontainers approach.
- Test authorization-denied cases as carefully as successful cases.
- Run `./mvnw test`; when Docker is available and the project is initialized, run `./mvnw verify`.
- Report unrun checks explicitly; never claim success without command output.
