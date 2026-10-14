# Demo and Viva Guide

> Status: Draft v0.3 · Owner: Laxman Sirvi · Last updated: 2026-10-14

This guide prepares you to present Campus Companion as a Java assignment and to answer questions about it yourself.

> **Checked against the code on 2026-10-14.** Class names, files, endpoints and numbers below are those of the built application. Read each class yourself before the viva; do not present an answer you have not confirmed in your own code.

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

- 18 tables, created by six Flyway migrations (`V1` to `V6`). The core chain: `users` → `student_profiles` → `attendance_records`, `marks`, `semester_results`, `quiz_attempts`.
- `subjects` → `subject_units` categorise resources and questions.
- **Constraints to mention:** unique roll number; foreign keys from marks to student and assessment; check constraints on ranges (semester 1 to 8, SGPA 0 to 10); a unique key so the same class cannot be recorded twice for a student.
- **Design points:** PDFs are in file storage and the database holds only the path; CGPA is calculated, not stored; the audit log is append-only and never holds password data.
- **Access model:** the application logs in with a low-privilege database role that cannot change the schema or the audit log, and a separate role runs migrations. The Java code, not the database, decides which student sees which row.

ER diagram and data dictionary: [08-database-design.md](08-database-design.md).

## 6. Important classes to be ready to open

| Class (file) | What it shows | Be ready to explain |
| --- | --- | --- |
| `AttendanceCalculator` (`attendance/`) | Plain Java business logic, no Spring | The percentage formula, `BigDecimal` with `HALF_UP` to two decimals, the null case for zero classes, "below" only when strictly less |
| `CgpaCalculator` (`assessment/`), used by `ResultService` | Calculation over a collection | CGPA weighted by the credits registered in each semester; why registered and not earned; withheld results left out |
| `QuizEvaluationService` (`quiz/`) | Collections and maps | A `Map` from question ID to the right option; unanswered counts wrong; an answer for a foreign question is refused |
| `QuizService` (`quiz/`) | A transaction with a lock | Random questions, shuffled options, the end time fixed at the start, `findOwnForUpdate` so a test is submitted once, the 15-second grace |
| `ProgressCalculator` (`progress/`) | Rules and enums | Latest 20 answers, at least 5, below 60% and from 80%; why in Java and not AI |
| `AttendanceController` (`attendance/`) | A REST controller | Annotations, `@AuthenticationPrincipal`, why there is no student ID parameter |
| `SecurityConfig` (`common/security/`) | Spring Security | URL rules, BCrypt, CSRF cookie to header, session rules, the Content-Security-Policy, HTTPS in production |
| `GlobalExceptionHandler` (`common/error/`) | Exception handling | One place that turns exceptions into problem responses with a stable `code` |
| `StudentProfile`, `StudentProfileRepository` (`student/`) | JPA entity and Spring Data | Annotations, the link to the account, a derived query method, a JPQL query with optional filters |
| `AiProvider`, `MockAiProvider`, `AiProviderConfig` (`ai/provider/`) | Interface and polymorphism | One interface, one implementation chosen by configuration, the mock for tests and the demo |
| `AiResponseParser`, `PromptBuilder` (`ai/`) | Treating outside text as untrusted | Wrapper tags removed from the student's text; the reply checked for shape and size, never trusted |
| `PdfValidator` (`resource/`), `ProfilePhotoProcessor` (`student/`) | Safe file handling | Size, type, signature, parsing with a time limit; a photo redrawn so nothing hidden in it is kept |
| `StartQuizRequest` (`quiz/dto/`) | Record and validation | Records as DTOs, Bean Validation annotations |
| `V1__core.sql` (`resources/db/migration/`) | SQL | Tables, keys, check constraints, Row Level Security and the minimum grants |

## 7. Java and OOP concepts and where they appear

| Concept | Where |
| --- | --- |
| Classes and objects | Entities such as `StudentProfile`, `Subject`, `QuizQuestion` |
| Encapsulation | Private fields with controlled access; services expose operations, not data structures |
| Abstraction | `AiProvider` and `StorageClient` hide how the external service is called |
| Interfaces and polymorphism | `MockAiProvider` (and later the real provider) used through `AiProvider`; `AiProviderConfig` picks one from `AI_PROVIDER`. `SupabaseStorageClient` and the tests' in-memory store share `StorageClient` |
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
| File I/O | Reading an uploaded file's bytes, checking its signature, opening it with PDFBox; reading and redrawing an image with `javax.imageio` |
| Concurrency | A two-thread executor with a time limit for opening PDFs; a database row lock for submitting a test; synchronised rate-limit counters |
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
- A Java service checks the usage limits, builds a prompt from a versioned template (`prompts/explain-v1.txt`), and calls the provider through the `AiProvider` interface with a timeout. Until a real provider is chosen, the built-in mock answers and the page says "Demo mode".
- The model must reply in JSON. **Java parses and validates it** before returning it. Malformed output is rejected. This checks the shape of a reply, not whether its content is right, which is why every answer is labelled.
- The AI receives only what the student typed plus the subject name. It cannot read marks or attendance.
- **AI never scores a test.** Scoring is deterministic Java.
- Every answer is labelled as AI-generated.
- If the provider is down, only the assistant is affected. There is a switch to turn it off.

## 11. Testing and deployment

- **191 unit tests** with JUnit and Mockito for the rule classes, the parsers and the file checks.
- **161 integration tests** over real HTTP against real PostgreSQL in a container, including "not logged in", "wrong role" and "someone else's record".
- **`EndpointAccessIT`** reads every endpoint from the running application and checks that a visitor, a student and an admin are each refused where they must be, so a new endpoint cannot be forgotten.
- **Database tests** check the roles, grants and Row Level Security of every table.
- **AI tests** use the mock provider, so they cost nothing and are repeatable.
- **Deployment:** GitHub runs the tests; the JAR is deployed to Heroku; configuration and secrets are environment variables; Flyway updates the database at startup.

## 12. Demonstration script (about eight minutes)

**Before you start:** follow the pre-demo checklist in the [runbook](19-operations-runbook.md#11-before-a-demo). Have two browser windows ready, one per role.

| # | Do | Say |
| --- | --- | --- |
| 1 | Open the login page; try a wrong password | "The error does not say whether the email or the password was wrong, or even whether the account exists." |
| 1a | Log in as a newly provisioned student with the institutional email and the roll number | "The first password is the roll number, so the server forces a change. Watch: I cannot open the dashboard, and a direct API call is refused too." |
| 1b | Try the roll number as the new password, then choose a real one | "It is rejected. After the change the roll number never works again. I documented this first-password rule as a known weakness, acceptable only for a demonstration." |
| 2 | Log in as the demo student | "The dashboard is built from one API call to my Spring Boot service." |
| 3 | Open Attendance | "The percentage and the red flag are calculated in `AttendanceService`." |
| 4 | Open Results | "CGPA is calculated in Java from the semester results." |
| 5 | Open Resources; filter to Data Structures question papers; preview one | "The file is in private storage. The server gave my browser a link that expires in five minutes." (Needs the storage bucket, `T3-01`. Without it the page says the file is temporarily unavailable, and the rest still works.) |
| 6 | Open the Assistant; explain a topic from the paper | "My Java service called the AI provider and validated the JSON it returned." |
| 7 | Take a 5-question test; submit | "The correct answers were never sent to the browser. Java scored it." |
| 8 | Open Progress | "This unit is marked for revision by a simple Java rule, with notes recommended." |
| 9 | Switch to the admin window; change the student's marks; publish a notice | "Only an admin can do this, and the change is written to an audit log in the same transaction." |
| 10 | Back in the student window, refresh | "The student sees the new marks and the notice." |
| 11 | As the student, paste an admin URL | "Access denied. The server enforces the role." |
| 12 | Open one core class in the editor | Walk through `QuizEvaluationService` or `AttendanceService`. |

**If something fails live:** say what should have happened, show the passing test for it, and move on. If the AI provider is down, switch to the mock provider.

## 13. Demo data and accounts

All data is synthetic. These are the accounts of the demo seeder (`DEMO_SEED_ENABLED=true`), with the example domain used locally. On the deployed application the domain is the one confirmed under OQ-15.

| Account | Login email | Purpose |
| --- | --- | --- |
| Demo student with low attendance in some subjects | `23cse001@university.example` | Main walkthrough |
| Seeded student, never logged in | `23cse002@university.example` (also `23cse003`, `23cse004`) | Showing the first-time password change |
| Demo student with no records | `23ece001@university.example` (ECE has no subjects) | Showing empty states |
| Demo admin | The address in `ADMIN_BOOTSTRAP_EMAIL` | Admin walkthrough |

- **Passwords are never written in this document or anywhere in the repository.** A seeded student's first password is the roll number and must be changed at first login; keep the passwords you then choose privately.
- The seeder prepares: five CSE semester 3 subjects with their units, a weekly timetable, four weeks of attendance, assessments with marks, semester results, notices, and 38 practice questions (10 in Data Structures unit 2). Upload three or four PDFs, including a past paper, by hand once the storage bucket exists.

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

**Why do students log in with an email address and not the roll number?**
The email is the institution's identity for the student. I keep the roll number separately as the academic identifier, so changing how someone logs in never touches their records. The domain is configuration, not code.

**The first password is the roll number. Is that not insecure?**
Yes, and I say so in the documents. The email contains the same roll number, so anyone who knows it could log in before the student does. I limit the damage: the server blocks everything until the password is changed, the roll number can never be chosen again, a reset uses a random password, and only synthetic data is loaded. It is recorded as an exception that must be reviewed before real students use it.

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
Text in the input that tries to give the model new instructions. It cannot be fully prevented, and a reply can be valid JSON and still be wrong or off-topic. So my design limits the damage instead: the model has no tools and no data to leak, it has no memory, and its output is shown only as labelled plain text and is never used for scoring or records.

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
