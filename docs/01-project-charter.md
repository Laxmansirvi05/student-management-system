# 01. Project Charter

> Status: Draft v0.1 · Owner: Laxman Sirvi · Last updated: 2026-10-10

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
3. The student sees both changes, and the marks change appears in the audit log.

And: all P0 requirements pass their acceptance criteria, the automated tests pass, and the access-control tests in [17-testing-strategy.md](17-testing-strategy.md) pass.

## 9. Non-negotiable decisions

1. **Java-first.** This is a Spring Boot application. It is not an Express.js application with a Java component added.
2. **Authorization is enforced on the backend** for every protected request.
3. **Secrets never reach the browser or Git.**
4. **AI never scores a test and never states official records.**
5. **Synthetic data only.**
6. **Build in phases.** A feature is finished and tested before dependent work starts.

## 10. Assumptions

| ID | Assumption |
| --- | --- |
| A1 | One college, one deployment. No multi-tenancy. |
| A2 | Eight semesters; students belong to a branch and a section. |
| A3 | A student's subjects are the subjects of their branch and semester. There is no elective enrolment in the first release. |
| A4 | Students do not self-register. An administrator creates accounts. |
| A5 | Traffic is small (a class-sized demo), so one Heroku dyno is enough. |

## 11. Open decisions

These are unresolved. Documents refer to them by ID. Until the owner answers, the stated default applies and is marked `TBD` where it matters.

| ID | Question | Default used in the documents |
| --- | --- | --- |
| OQ-1 | Which AI provider and model, and what budget? | Provider-neutral Java interface with a mock provider. Proposed real provider: Anthropic Claude API on a low-cost model. See [ADR 0005](decisions/0005-ai-integration-behind-java-interface.md). |
| OQ-2 | Required attendance percentage, and is it per subject or overall? | 75%, per subject, configurable. |
| OQ-3 | Grading scale and credit system for SGPA and CGPA? | 10-point scale. CGPA is the credit-weighted mean of SGPAs. |
| OQ-4 | Is "Campus Companion" the final name? | Yes. |
| OQ-5 | What is the submission deadline? | Unknown. The roadmap uses phases, not dates. |
| OQ-6 | Exact Spring Boot and dependency versions? | Chosen and verified in task `T1-01`, then recorded in [04-TRD.md](04-TRD.md). |
| OQ-7 | Which Heroku and Supabase plans, and what monthly budget? | Lowest paid Heroku dyno during the demo period; Supabase free plan. Verify current pricing first. |
| OQ-8 | Which branches, sections and subjects does the demo cover? | One branch (CSE), two sections, semesters 3 to 5, synthetic. |
