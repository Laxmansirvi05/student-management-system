# 0001. Spring Boot is the sole backend

- Status: Accepted
- Date: 2026-10-10
- Deciders: Laxman Sirvi

## Context

The project is a Java assignment. An earlier idea was a Node.js and Express backend with Java added alongside it. That would put most of the real logic in JavaScript and leave little Java to assess or explain. The application needs REST endpoints, authentication, database access, file handling and calls to an AI provider.

## Options considered

1. **Node.js and Express backend, with a Java component.** Familiar and quick, but the assessed language becomes a side part and two runtimes must be deployed.
2. **Plain Java with servlets and JDBC.** Shows fundamentals, but security, validation and persistence would be written by hand, which is slow and error-prone.
3. **Java with Spring Boot as the only backend.** One runtime; mature support for web, security, data and testing.

## Decision

The backend is a single Spring Boot application written in Java 21. There is no Node.js, Express or other backend service. JavaScript exists only in the browser.

## Trade-offs

- Spring Boot does a lot implicitly, which can hide what the owner needs to understand. To counter this, core rules are written as plain Java classes and code generators such as Lombok are not used.
- A Java application uses more memory and starts more slowly than a small Node.js server, which matters on a small Heroku dyno.
- The owner must learn Spring Security and JPA.

## Consequences

- Every feature's logic, including AI integration and file processing, is implemented in Java.
- Any proposal to add another backend technology needs a new decision record that supersedes this one.
- The stack list in [04-TRD.md](../04-TRD.md) follows from this decision.
