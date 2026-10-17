# 01. Project Charter

> Status: Draft v0.2 · Owner: Laxman Sirvi · Last updated: 2026-10-11

## 1. Vision

Campus Companion gives a college student one place to see where they stand academically and to prepare for exams. It gives an administrator one place to maintain the data and content that students depend on.

## 2. Problem statement

- Academic information (attendance, marks, timetable, notices) is spread across notice boards, spreadsheets and the office.
- Study material and previous-year papers circulate informally and are hard to search.
- Students cannot easily test themselves on a specific unit or see which topics need revision.
- A records-only portal is opened a few times a semester. It does not help a student learn.

## 3. Objectives

| ID | Objective |
| --- | --- |
| O1 | A student sees all of their own academic information after one login. |
| O2 | A student finds a note or question paper by subject, semester, unit, year and type. |
| O3 | A student takes a timed practice test and gets a score with explanations immediately. |
| O4 | A student gets AI help to understand a topic, condense notes and practise questions. |
| O5 | An administrator manages every piece of data the student features depend on. |
| O6 | The codebase demonstrates Java and Spring Boot concepts clearly enough to explain in a viva. |

## 4. Target users

- **Students:** enrolled undergraduates. Mostly on phones, sometimes on lab computers.
- **Administrators:** college staff who maintain records and upload content. Mostly on desktop.

A faculty role is out of scope for the first release.

## 5. What makes it different from a basic student portal

1. It joins records and learning. Quiz results feed revision recommendations that link back to the resource library.
2. Practice tests are scored by deterministic Java code, so results are reliable and explainable.
3. The AI assistant is a study tool with rules: it explains, it does not invent official data, and it never grades.
4. Access control is designed first. A student can never read another student's records.

## 6. Main capabilities

Authentication and roles; student academic portal; notices and deadlines; learning resource hub; Quick Exam; AI study assistant; progress and revision recommendations; admin and content management. Requirements are in [02-PRD.md](02-PRD.md).

## 7. Constraints

### Academic assignment

- This is a **Java assignment**. The backend must be Java, and the owner must be able to explain the Java code.
- Important business logic (attendance, CGPA, quiz scoring, progress rules) is written as plain, readable Java services, not hidden inside framework configuration.
- The submission date is not yet known (see OQ-5).

### Technology

- Java 21 and Spring Boot are the only backend. No Node.js, no Express, no second backend.
- The frontend is plain HTML, CSS and JavaScript. No frontend framework.
- The database is PostgreSQL on Supabase. MySQL is not used.
- Hosting is Heroku.

### Budget

- This is a student project. Recurring cost must be near zero outside the demo period.
- Heroku has no free tier and AI APIs charge per use. Both are capped and reviewed before deployment (see OQ-1 and OQ-7).

### Data

- **Only synthetic data.** No real student's name, roll number, marks or contact details are stored in any environment or committed to the repository.

## 8. Success criteria

The project succeeds when both flows run end to end on the deployed application.

**Student flow**

1. Log in and see attendance and results on the dashboard.
2. Open a DBMS previous-year paper from the resource hub.
3. Ask the AI assistant to explain a topic from it.
4. Take a short practice test on that unit and receive a score.
5. See the weak unit in revision recommendations.

**Admin flow**

1. Log in and change a student's marks.
2. Publish a notice.
3. The student sees both changes, and the marks change is recorded in the audit log. This is shown by the automated audit test and a read-only database query; a screen for browsing the log is P1.

And: **every** P0 requirement passes its acceptance criteria, the automated tests pass, and the access-control tests in [17-testing-strategy.md](17-testing-strategy.md) pass. Step 3 of the student flow uses a real AI provider unless the owner decides otherwise under OQ-9.

## 9. Non-negotiable decisions

1. **Java-first.** This is a Spring Boot application. It is not an Express.js application with a Java component added.
2. **Authorization is enforced on the backend** for every protected request.
3. **Secrets never reach the browser or Git.**
4. **AI never scores a test and never states official records.**
5. **Synthetic data only.**
6. **Build in phases.** A feature is finished and tested before dependent work starts.
7. **Every P0 requirement is required.** None is dropped without an approved, recorded scope change ([03-scope-and-roadmap.md](03-scope-and-roadmap.md) §9).
8. **The application is the authorization boundary.** Database and storage controls are defence in depth and never replace the checks in Java ([08-database-design.md](08-database-design.md) §11).

## 10. Assumptions

| ID | Assumption |
| --- | --- |
| A1 | One college, one deployment. No multi-tenancy. |
| A2 | Eight semesters; students belong to a branch and a section. |
| A3 | A student's subjects are the subjects of their branch and semester. There is no elective enrolment in the first release. |
| A4 | Students do not self-register. An administrator provisions accounts from a verified roster. Students log in with an institutional email address generated from the roll number and a configured domain; the roll number remains the separate academic identifier. |
| A5 | Traffic is small (a class-sized demo), so one Heroku dyno is enough. |
| A6 | The college supplies, for each semester, an official SGPA, the credits registered and the credits earned. The application records them; it does not calculate SGPA from course grades. |
| A7 | CGPA is the average of the SGPAs weighted by the credits registered in each semester. This is the default pending OQ-3. |

## 11. Open decisions

These are unresolved. Documents refer to them by ID. Each one has someone who decides it, a point by which it must be decided, and a default that applies until then.

A default is **not** a decision. Where it matters, the affected text in other documents is marked `TBD` or "Proposed".

"Needed by" names the task or gate in [16-implementation-plan.md](16-implementation-plan.md) that cannot be completed without the answer. Calendar dates are not given because the submission deadline is itself unknown (OQ-5).

| ID | Question | Decided by | Needed by | Default until decided |
| --- | --- | --- | --- | --- |
| OQ-1 | Which AI provider and model, what budget, and are the provider's data-handling terms acceptable? | Owner | Before `T5-06` (gate G4) | Provider-neutral Java interface with the mock provider. Proposed real provider: Anthropic Claude API on a low-cost model. See [ADR 0005](decisions/0005-ai-integration-behind-java-interface.md). |
| OQ-2 | Required attendance percentage, and is it per subject or overall? | Owner, confirming with the college | Before `T2-05` | 75%, per subject, set by `ATTENDANCE_REQUIRED_PERCENT`. |
| OQ-3 | Grading scale, and the college's CGPA rule, including failed, repeated and withheld courses? | Owner, confirming with the college regulation | Before `T2-10` | 10-point scale. CGPA is the SGPAs weighted by credits **registered** per semester; withheld semesters are left out. See [08-database-design.md](08-database-design.md) §4.8. |
| OQ-4 | Is "Campus Companion" the final name? | Owner | Before `T1-01`, because the Java base package is fixed then | Yes; package `com.campuscompanion`. |
| OQ-5 | What is the submission deadline? | Owner | Before gate G1 | Unknown. The roadmap uses phases, not dates, and no P0 item is cut. |
| OQ-6 | Exact Spring Boot and dependency versions? | The agent verifies; the owner approves | Within `T1-01` | The current stable Spring Boot line that supports Java 21, recorded in [04-TRD.md](04-TRD.md). |
| OQ-7 | Which Heroku and Supabase plans, and what monthly budget? | Owner | Supabase: before `T0-03`. Heroku: before `T6-04` | Supabase free plan; lowest paid Heroku dyno during the demo period. Verify current pricing first. |
| OQ-8 | Which branches, sections and subjects does the demo cover? | Owner | Before `T1-10` | One branch (CSE), two sections, semesters 3 to 5, synthetic. |
| OQ-9 | Is it acceptable to submit with the AI assistant demonstrated on the mock provider only? | Owner, confirming with the course instructor | Before gate G4 | **No.** A real provider is required for acceptance. The mock is for development, tests and a demo-day outage. See [03-scope-and-roadmap.md](03-scope-and-roadmap.md) §3. |
| OQ-10 | Approve the database security model: two database roles, schema `app`, Data API switched off? | Owner | Before `T0-03` | Proceed as proposed in [ADR 0007](decisions/0007-database-roles-and-rls.md). If custom roles cannot connect through the pooler, stop and report. |
| OQ-11 | What is the daily AI request ceiling across all users? | Owner, together with the OQ-1 budget | Before `T5-02` | 500 requests per day (`AI_DAILY_LIMIT_GLOBAL`). |
| OQ-12 | Approve the decision records still marked Proposed: 0004 (sessions), 0005 (AI approach), 0006 (Flyway)? | Owner | 0006 before `T1-03`; 0004 before `T1-06`; 0005 before `T5-01` | Proceed as proposed. |
| OQ-13 | ~~Accept that a locked account answers differently from an unknown user name?~~ | Owner | | **Resolved 2026-10-11:** no. The owner's change request requires that login never reveals whether an account exists, so the lockout applies to any email, existing or not ([02-PRD.md](02-PRD.md) FR-AUTH-005). |
| OQ-14 | Approve small wording updates to `SECURITY_RULES.md` and `AGENTS.md` so they mention grants and policies as well as RLS? | Owner | Before `T1-03` | The root rule files are unchanged. They do not contradict the model; [08-database-design.md](08-database-design.md) §11 is authoritative for the detail. |

| OQ-15 | What is the official student email domain, and is `<roll number>@<domain>` really the institution's naming convention? | Owner, confirming with the college | Before `T6-04` (production configuration). Development can proceed with a reserved example domain | Generate `<roll number in lower case>@<STUDENT_EMAIL_DOMAIN>`. The domain in the change request (`university.edu.com`) was given as an example and is **not** verified; documents use `university.example`. **Answered 2026-10-17 by the owner:** the format is `<roll number in lower case>@gcet.edu.in`, so the production value is `STUDENT_EMAIL_DOMAIN=gcet.edu.in`. Development and tests keep the reserved example domain. |
| OQ-16 | Two account rules: (a) should an admin reset restore the roll number or issue a random temporary password? (b) may a roll number or login email be corrected after the account exists? | Owner | Before `T2-03` | (a) Random temporary password, never the roll number. (b) Neither can be changed through the application; a mistake is corrected by deactivating the account and provisioning the right one. |
| OQ-17 | May the roll-number temporary password be used with real students? | Owner, after an explicit security review | Before any deployment that holds real student data | **Yes, answered 2026-10-17 by the owner**, with four operating rules ([ADR 0010](decisions/0010-roll-number-first-password-with-real-students.md)). Before that it was approved only for synthetic data ([ADR 0008](decisions/0008-institutional-email-login.md)). |

### Technical verifications

These are facts to check, not choices for the owner. Each is checked in the task named and the result is written into the relevant document.

| Item | Checked in |
| --- | --- |
| Spring Boot, Spotless and Testcontainers versions and their compatibility with Java 21 | `T1-01` |
| Custom database roles through the Supabase session pooler; the statements needed to create the roles and schema | `T0-03` |
| Whether identity-column inserts by the runtime role need a sequence privilege | `T1-03` |
| Colour contrast of the design tokens | `T1-09` |
| Storage works with the Data API off; current names of Supabase's API keys | `T3-01` |
| PDFBox version | `T3-03` |
| A signed PDF link can be shown inside a frame | `T3-06` |
| Heroku buildpack defaults, Java 21 support, and whether outbound connections are IPv4 only | `T6-01` |
| Heroku and Supabase plan limits and prices | `T6-04` |
| Mermaid version and the Content Security Policy change it needs | `T7-05` |

## 12. Terminology

One term is used for each concept. The right-hand column lists other words that mean the same thing where they still appear.

| Term | Meaning | Also seen as |
| --- | --- | --- |
| Owner | Laxman Sirvi. Decides, approves and is accountable. | |
| Admin | A user with the role `ADMIN`. | administrator |
| Student | A user with the role `STUDENT`. | |
| Login email | The institutional email address an account logs in with. For a student: the roll number in lower case, `@`, and the configured domain. An identifier only; never verified by the application. | institutional email, email |
| Roll number | The student's academic identifier, kept in the student profile. It is not the login identifier. | student ID |
| Temporary password | A password that must be changed at the next login. For a newly provisioned student it is the roll number; after an admin reset it is random. | first-time password, initial password |
| Provisioning | An admin creating a student's account and profile from the roster. | account creation |
| Quick Exam | The timed practice-test feature. | quiz, test, practice test. Code and API use `quiz`. |
| Attempt | One sitting of a Quick Exam by one student. | |
| Resource | A file in the resource hub together with its details. | learning resource, study material |
| Resource hub | The searchable collection of resources. | learning resource hub, library |
| Question paper | A resource of type `QUESTION_PAPER` or `MODEL_PAPER`. | previous-year paper, past paper |
| Question bank | The admin-maintained set of Quick Exam questions. | |
| AI study assistant | The AI feature inside the product. It is a set of single-request tools, not a chat. | assistant, AI assistant |
| AI provider | The external model service the backend calls. | |
| Mock provider | `MockAiProvider`, the built-in stand-in that returns fixed answers. | the mock |
| Coding agent | An AI tool that writes code in this repository. It is unrelated to the AI study assistant. | agent |
| Runtime role | The database role `cc_app`, used by the running application. | |
| Migration role | The database role `cc_migrator`, used only by Flyway. | |
| Service key | Supabase's server-side key that grants full Storage access. | Supabase's current documentation may call it a secret key |
| Priority | P0, P1 or P2, as defined in the PRD. | |
| Requirement ID | `FR-<AREA>-<number>`, defined in the PRD. | |
| Task ID | `T<phase>-<number>`, defined in the implementation plan. Unrelated to priorities. | |
| Milestone, release, gate | M1 to M6, R1 to R3 and G0 to G6, defined in the roadmap and the implementation plan. | |
