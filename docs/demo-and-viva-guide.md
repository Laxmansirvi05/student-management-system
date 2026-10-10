# Demo and Viva Guide

> Status: Draft v0.1 · Owner: Laxman Sirvi · Last updated: 2026-10-10

This guide prepares you to present Campus Companion as a Java assignment and to answer questions about it yourself.

> **Written before the code exists.** The class names, endpoints and answers below describe the planned design. In task `T6-07`, go through this guide against the real code: fix anything that changed, and replace each "show this class" note with the actual file and line you will open. Do not present an answer here that you have not confirmed in your own code.

## 1. Opening (one minute)

> "Campus Companion is a student management and learning platform. A basic portal only shows records. Mine also helps students prepare: they can find notes and past papers, take timed practice tests that are scored in Java, and ask an AI assistant to explain topics. The whole backend is Java 21 with Spring Boot. It exposes a REST API, stores data in PostgreSQL on Supabase, and is deployed on Heroku."

## 2. Problem and objectives

- **Problem:** academic information is scattered; study material is hard to find; students cannot easily check what they are weak at.
- **Objectives:** one login for all records; searchable resources; instant practice tests; safe AI study help; complete admin management; clear, explainable Java.

Full version: [01-project-charter.md](01-project-charter.md).

## 3. Technology choices and why

| Choice | Why | What I rejected |
| --- | --- | --- |
| Java 21, Spring Boot | The assignment is Java; Spring Boot gives web, security, data and testing in one framework | Node.js and Express: the logic would not be in Java |
| REST API with JSON | Clean separation between the interface and the logic | Server-rendered pages: harder to show the API |
| PostgreSQL on Supabase | Relational data with foreign keys; managed and low cost | MySQL: not offered by Supabase and not needed |
| Spring Data JPA and Hibernate | Maps tables to Java classes; less repetitive SQL | Raw JDBC everywhere: slower to write, easier to get wrong |
| Flyway | Schema changes are versioned SQL files I can show | Letting Hibernate change tables automatically: unsafe and invisible |
| Session cookies | Simple and secure for one website; the browser's JavaScript cannot read the session | JWT: more complex, and its benefits do not apply to a single application |
| Plain HTML, CSS, JavaScript | Keeps the focus on Java | React: a second toolchain |
| One JAR on Heroku | One build, one deploy, no cross-origin problems | Separate frontend hosting |

Each of these has a decision record in [docs/decisions](decisions/README.md).

## 4. Architecture in three sentences

1. The browser loads static pages and calls `/api/v1/...` endpoints.
2. Each request passes through Spring Security, then a **controller** (HTTP), a **service** (business rules) and a **repository** (database).
3. The backend is the only part that talks to the database, the file storage and the AI provider.

Draw this:

```
Browser -> Security filters -> Controller -> Service -> Repository -> PostgreSQL
                                               |-> StorageClient -> Supabase Storage
                                               |-> AiProvider    -> AI API
```

Diagrams: [05-architecture.md](05-architecture.md).

## 5. Database

- About 18 tables in the first release. The core chain: `users` → `student_profiles` → `attendance_records`, `marks`, `semester_results`, `quiz_attempts`.
- `subjects` → `subject_units` categorise resources and questions.
- **Constraints to mention:** unique roll number; foreign keys from marks to student and assessment; check constraints on ranges (semester 1 to 8, SGPA 0 to 10); a unique key so the same class cannot be recorded twice for a student.
- **Design points:** PDFs are in file storage and the database holds only the path; CGPA is calculated, not stored; the audit log is append-only.

ER diagram and data dictionary: [08-database-design.md](08-database-design.md).

## 6. Important classes to be ready to open

| Class | What it shows | Be ready to explain |
| --- | --- | --- |
| `AttendanceService` | Plain Java business logic | The percentage formula, `BigDecimal` and rounding, the null case for zero classes |
| `ResultService` | Calculation over a collection | Credit-weighted CGPA using streams or a loop |
| `QuizEvaluationService` | Collections and maps | Matching answers to questions with a `Map`, counting correct ones |
| `ProgressService` | Rules and enums | The thresholds and why they are in Java, not AI |
| `AttendanceController` | A REST controller | Annotations, how the current user is obtained, why there is no student ID parameter |
| `SecurityConfig` | Spring Security | URL rules, BCrypt, CSRF, session settings |
| `GlobalExceptionHandler` | Exception handling | One place that turns exceptions into error responses |
| `StudentProfile` and its repository | JPA entity and Spring Data | Annotations, relationships, a derived query method |
| `AiProvider`, `MockAiProvider` | Interface and polymorphism | Why an interface: swap the real provider for a mock in tests |
| `StartQuizRequest` | Record and validation | Records as DTOs, Bean Validation annotations |
| `V1__core.sql` | SQL | Table creation, keys and constraints |

## 7. Java and OOP concepts and where they appear

| Concept | Where |
| --- | --- |
| Classes and objects | Entities such as `StudentProfile`, `Subject`, `QuizQuestion` |
| Encapsulation | Private fields with controlled access; services expose operations, not data structures |
| Abstraction | `AiProvider` and `StorageClient` hide how the external service is called |
| Interfaces and polymorphism | `MockAiProvider` and the real provider are used through the same interface; Spring picks one by configuration |
| Inheritance | Custom exceptions extend a common base exception; repositories extend Spring Data interfaces |
| Composition | A service is built from the repositories and services passed to its constructor |
| Records | Request and response DTOs |
| Enums | `Role`, `AttendanceStatus`, `ResourceType`, `PerformanceLevel` |
| Collections | `List` of answers, `Map` from question ID to selected option, grouping resources by unit |
| Streams and lambdas | Summing credits, filtering published items, mapping entities to DTOs |
| Generics | `PageResponse<T>`, `Optional<T>`, `JpaRepository<T, ID>` |
| Exception handling | Custom exceptions, a global handler, and timeouts on external calls |
| `BigDecimal` and `java.time` | Exact marks and percentages; dates and expiry times |
| Dependency injection | Constructor injection throughout |
| Annotations | `@RestController`, `@Service`, `@Entity`, `@Valid`, `@Transactional` |
| JDBC and SQL | Flyway migrations; the JDBC driver and connection pool under JPA |
| File I/O | Reading an uploaded file's bytes, checking its signature, extracting PDF text |
| HTTP client | Calling the AI provider and the storage API |
| Unit testing | JUnit 5 and Mockito for the service classes |

## 8. REST API examples to show

| Show | Point to make |
| --- | --- |
| `GET /api/v1/attendance/me` | `/me` means the server decides whose data it is |
| `POST /api/v1/quiz-attempts` | The response has no correct answers in it |
| `POST /api/v1/quiz-attempts/{id}/submit` | The server calculates the score |
| `PUT /api/v1/admin/assessments/{id}/marks` as a student | 403: role enforced on the server |
| Any request with a bad field | 400 with a clear field error in a standard format |

Contract: [09-api-contract.md](09-api-contract.md).

## 9. Security explanation

Say these five things:

1. **Passwords** are stored as BCrypt hashes, never as text.
2. **Sessions** use an `HttpOnly`, `Secure` cookie, so page scripts cannot read it.
3. **Every request is checked on the server** for login, role and ownership. Hiding a button is not security.
4. **A student cannot see another student's data** because student endpoints take no student ID; the ID comes from the session.
5. **Inputs are validated and queries use parameters**, which prevents SQL injection; CSRF tokens protect state-changing requests.

Then, if asked: lockout after five failures; audit log for marks changes; private file storage with five-minute links; secrets only in environment variables.

## 10. AI integration explanation

- The browser never calls the AI provider. It calls **my Java endpoint**.
- A Java service checks the usage limit, builds a prompt from a versioned template, and calls the provider through an `AiProvider` interface with a timeout.
- The model must reply in JSON. **Java parses and validates it** before returning it. Invalid output is rejected.
- The AI receives only what the student typed plus the subject name. It cannot read marks or attendance.
- **AI never scores a test.** Scoring is deterministic Java.
- Every answer is labelled as AI-generated.
- If the provider is down, only the assistant is affected. There is a switch to turn it off.

## 11. Testing and deployment

- **Unit tests** with JUnit 5 and Mockito for the rule classes.
- **Controller tests** for every endpoint, including "not logged in", "wrong role" and "someone else's record".
- **Database tests** against real PostgreSQL in a container.
- **AI tests** use a mock provider, so they cost nothing and are repeatable.
- **Deployment:** GitHub runs the tests; the JAR is deployed to Heroku; configuration and secrets are environment variables; Flyway updates the database at startup.

## 12. Demonstration script (about eight minutes)

**Before you start:** follow the pre-demo checklist in the [runbook](19-operations-runbook.md#11-before-a-demo). Have two browser windows ready, one per role.

| # | Do | Say |
| --- | --- | --- |
| 1 | Open the login page; try a wrong password | "The error does not say whether the username or the password was wrong." |
| 2 | Log in as the demo student | "The dashboard is built from one API call to my Spring Boot service." |
| 3 | Open Attendance | "The percentage and the red flag are calculated in `AttendanceService`." |
| 4 | Open Results | "CGPA is calculated in Java from the semester results." |
| 5 | Open Resources; filter to DBMS question papers; preview one | "The file is in private storage. The server gave my browser a link that expires in five minutes." |
| 6 | Open the Assistant; explain a topic from the paper | "My Java service called the AI provider and validated the JSON it returned." |
| 7 | Take a 5-question test; submit | "The correct answers were never sent to the browser. Java scored it." |
| 8 | Open Progress | "This unit is marked for revision by a simple Java rule, with notes recommended." |
| 9 | Switch to the admin window; change the student's marks; publish a notice | "Only an admin can do this, and the change is written to an audit log in the same transaction." |
| 10 | Back in the student window, refresh | "The student sees the new marks and the notice." |
| 11 | As the student, paste an admin URL | "Access denied. The server enforces the role." |
| 12 | Open one core class in the editor | Walk through `QuizEvaluationService` or `AttendanceService`. |

**If something fails live:** say what should have happened, show the passing test for it, and move on. If the AI provider is down, switch to the mock provider.

## 13. Demo data and accounts

All data is synthetic. Fill in the real values after seeding (`T6-06`).

| Account | Username | Purpose |
| --- | --- | --- |
| Demo student with low attendance in one subject | `23CSE001` (TBD) | Main walkthrough |
| Demo student with no data | `23CSE099` (TBD) | Showing empty states |
| Demo admin | `admin` (TBD) | Admin walkthrough |

- **Passwords are never written in this document or anywhere in the repository.** Keep them privately.
- Prepare in advance: one subject (DBMS) with five units; at least ten published questions in one unit; three or four resources including a past paper; one unpublished assessment to show that students cannot see it.

## 14. Likely viva questions

Answer in your own words. These are starting points, to be checked against the code.

### Java and OOP

**Why did you use an interface for the AI provider?**
So the service depends on an abstraction, not on one vendor. In tests I plug in a mock implementation; in production, the real one. That is polymorphism, and it means no test needs the internet.

**Where is encapsulation in your project?**
Entity fields are private and changed through methods. Services expose operations like "calculate attendance" and keep the rules inside. Controllers cannot reach repositories directly.

**What is a record and why use it for DTOs?**
A record is an immutable data class; the compiler generates the constructor, accessors, `equals` and `hashCode`. DTOs just carry data, so records are short and safe.

**Why `BigDecimal` and not `double` for marks?**
`double` cannot represent many decimal fractions exactly, so sums and percentages can be slightly off. `BigDecimal` is exact, and I choose the rounding mode explicitly.

**What collections did you use?**
`List` for ordered results, `Map` to look up a student's answer by question ID when scoring, and `Set` where duplicates must not occur. Streams to filter, map and total them.

**What is the difference between checked and unchecked exceptions, and what do you use?**
Checked exceptions must be declared or caught; unchecked ones extend `RuntimeException`. My business exceptions are unchecked, thrown from services and handled in one global handler.

### Spring Boot

**What is dependency injection?**
A class declares what it needs in its constructor and Spring supplies it. The class does not create its own dependencies, which makes it easy to test with mocks.

**What do `@RestController`, `@Service` and `@Repository` do?**
They mark classes as Spring-managed components in each layer. `@RestController` also maps methods to HTTP endpoints and writes return values as JSON.

**What does `@Transactional` do?**
It runs the method in one database transaction. If an exception is thrown, everything is rolled back. That is how a marks change and its audit entry succeed or fail together.

**How does Spring Data JPA create queries from method names?**
It parses the method name, for example `findByStudentIdAndSubjectId`, and generates the query. For anything complex I write the query explicitly with parameters.

**Why not return entities from controllers?**
Entities may contain fields that must not leave the server, like a password hash or a correct answer, and they tie the API to the table structure. DTOs return exactly what the screen needs.

### Database

**Why PostgreSQL and how do you connect?**
The data is relational with many foreign keys. Spring Boot connects through the PostgreSQL JDBC driver with a connection pool; JPA sits on top.

**What is normalization, and is your schema normalized?**
Organising tables so each fact is stored once. For example, a mark refers to an assessment, and the assessment refers to the subject, so the subject is not repeated on each mark.

**How do you change the schema?**
With Flyway migration files: numbered SQL scripts that run once, in order. Hibernate only validates.

**How do you prevent SQL injection?**
All queries use bound parameters through JPA. I never build SQL by joining strings with user input.

### Security

**How are passwords stored?**
As BCrypt hashes. BCrypt is slow by design and salts each password, so identical passwords have different hashes.

**How do you stop one student seeing another's marks?**
The endpoint has no student ID in it. The service takes the user from the session and queries only that student's rows. For things like quiz attempts, the query includes both the attempt ID and the owner.

**What is CSRF and how do you prevent it?**
Another site tricking a logged-in browser into sending a request. My server issues a token that the page must send back in a header; another site cannot read it.

**Why sessions and not JWT?**
The frontend and backend are one application on one domain. A server-side session with an `HttpOnly` cookie is simpler, and logout or lockout takes effect immediately.

**Where are your secrets?**
In environment variables on the server. Not in the code, not in the frontend, not in Git.

### AI

**How does the AI feature work?**
The browser calls my endpoint; a Java service builds a prompt, calls the provider's HTTP API with a timeout, parses the JSON reply into Java objects, validates it, and returns it.

**What if the AI gives a wrong answer?**
Every answer is labelled as AI-generated and tells the student to verify it. AI is never used for scoring or official records, so a wrong answer cannot change anyone's marks.

**What is prompt injection and how do you handle it?**
Text in the input that tries to give the model new instructions. My design limits the damage: the model has no tools and no data to leak, its output must be valid JSON, and it is displayed as plain text.

**What happens if the AI service is down?**
The assistant shows an "unavailable" message. Nothing else depends on it.

### Design and process

**Why is quiz scoring not done by AI?**
Scoring must be correct, repeatable and explainable. A simple comparison in Java is all three.

**What would you improve with more time?**
Store sessions in the database so a restart does not log users out; AI answers grounded in the uploaded notes; a faculty role; an analyzer for past papers.

**What was the hardest part?**
Answer honestly from your own experience once the project is built.

**Did you use AI tools to write the code?**
Answer honestly. A good answer explains how you used them, how you reviewed and tested what they produced, and then demonstrates that you understand the code by walking through a class.

## 15. Preparation checklist

- [ ] I can draw the architecture from memory.
- [ ] I can explain every row of the "important classes" table with the file open.
- [ ] I have run the demo script twice on the deployed site.
- [ ] I can write the attendance percentage and CGPA formulas on paper.
- [ ] I can explain one test and why it exists.
- [ ] I know which parts were hardest and what I would do next.
- [ ] I have checked every answer in section 14 against my actual code.
