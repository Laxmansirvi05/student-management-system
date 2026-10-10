# 02. Product Requirements Document

> Status: Draft v0.2 · Owner: Laxman Sirvi · Last updated: 2026-10-10

This document says what the application must do from the user's point of view. How it is built is in [04-TRD.md](04-TRD.md). Non-functional requirements (performance, security, reliability) live in the TRD and the [security plan](11-security-plan.md) and are not repeated here.

## 1. Roles

| Role | Can do |
| --- | --- |
| **Student** | Read their own academic records; read published notices and resources; take tests; use the AI assistant; manage their own bookmarks and study tasks. |
| **Admin** | Create and maintain students, academic records, subjects, timetables, notices, resources and the question bank. |

Exact permissions are in [10-authorization-matrix.md](10-authorization-matrix.md).

## 2. Priorities

- **P0:** required for the assignment submission.
- **P1:** should have; built only after every P0 item works.
- **P2:** later.

## 3. Requirement index

| ID | Requirement | Priority |
| --- | --- | --- |
| FR-AUTH-001 | Log in | P0 |
| FR-AUTH-002 | Log out and session expiry | P0 |
| FR-AUTH-003 | First-login and voluntary password change | P0 |
| FR-AUTH-004 | Role and ownership enforcement | P0 |
| FR-AUTH-005 | Account lockout | P0 |
| FR-STU-001 | Student dashboard | P0 |
| FR-STU-002 | Student profile | P0 |
| FR-ATT-001 | View own attendance | P0 |
| FR-ATT-002 | Low-attendance flag | P0 |
| FR-ATT-003 | Attendance calculator | P1 |
| FR-MRK-001 | View own marks | P0 |
| FR-MRK-002 | View semester results and CGPA | P0 |
| FR-TT-001 | View timetable | P0 |
| FR-NOT-001 | Notices and deadlines | P0 |
| FR-LIB-001 | Browse and filter resources | P0 |
| FR-LIB-002 | Search resources | P0 |
| FR-LIB-003 | Preview and download | P0 |
| FR-LIB-004 | Bookmarks | P0 |
| FR-LIB-005 | Report a resource | P1 |
| FR-LIB-006 | Question paper analyzer | P2 |
| FR-QZ-001 | Start a Quick Exam | P0 |
| FR-QZ-002 | Timer and submission | P0 |
| FR-QZ-003 | Scoring and result | P0 |
| FR-QZ-004 | Attempt history | P0 |
| FR-PRG-001 | Unit-wise strengths and weaknesses | P0 |
| FR-PRG-002 | Revision recommendations | P0 |
| FR-PLN-001 | Study tasks | P1 |
| FR-PLN-002 | Exam countdown | P1 |
| FR-AI-001 | Explain a topic | P0 |
| FR-AI-002 | Summarize text | P0 |
| FR-AI-003 | Generate practice questions | P0 |
| FR-AI-004 | Usage limits and failure handling | P0 |
| FR-AI-005 | Summarize a library PDF | P1 |
| FR-AI-006 | Flowchart generation | P1 |
| FR-AI-007 | Flashcards | P2 |
| FR-ADM-001 | Manage student accounts | P0 |
| FR-ADM-002 | Enter attendance | P0 |
| FR-ADM-003 | Manage assessments and marks | P0 |
| FR-ADM-004 | Enter semester results | P0 |
| FR-ADM-005 | Manage subjects, units and timetable | P0 |
| FR-ADM-006 | Manage notices | P0 |
| FR-ADM-007 | Upload and manage resources | P0 |
| FR-ADM-008 | Manage the question bank | P0 |
| FR-ADM-009 | Audit log | P0 write, P1 viewer |
| FR-ADM-010 | CSV bulk import | P1 |
| FR-ADM-011 | Usage statistics | P2 |
| FR-UX-001 | Errors, empty states and loading states | P0 |

## 4. Authentication

### FR-AUTH-001 Log in (P0)

- **Story:** As a student or admin, I want to log in with my username and password so that I reach my own dashboard.
- **Preconditions:** An active account exists. A student's username is their roll number.
- **Acceptance criteria:**
  1. Correct credentials create a session and open the dashboard for the user's role.
  2. Usernames are matched without regard to letter case.
  3. Wrong username and wrong password produce the same message: "Incorrect username or password."
  4. An admin never lands on the student dashboard, and the reverse.
- **Edge cases:** Deactivated account gets the same generic message. Empty fields are rejected before the request is sent.
- **Depends on:** nothing.

### FR-AUTH-002 Log out and session expiry (P0)

- **Story:** As a user, I want to log out so that nobody else can use my session on a shared computer.
- **Acceptance criteria:**
  1. Log out ends the session on the server and returns to the login page.
  2. After log out, the back button does not reveal protected data.
  3. A session idle for 30 minutes expires.
  4. When an expired session calls the API, the page shows "Your session has expired. Please log in again." and returns to login.
- **Edge cases:** The longest test (20 minutes) is shorter than the idle limit, so a test cannot expire the session by itself. If a session does end during a test, unsaved answers are lost.
- **Depends on:** FR-AUTH-001.

### FR-AUTH-003 Password change (P0)

- **Story:** As a new user, I must replace my temporary password so that only I know it.
- **Acceptance criteria:**
  1. An account with a temporary password can do nothing except change the password or log out.
  2. A new password has 8 to 72 characters, is not the username, and is not the current password.
  3. Any user can change their password later by giving the current one.
  4. After a change, other sessions of that user are ended.
- **Edge cases:** Wrong current password is rejected and counts toward lockout.
- **Depends on:** FR-AUTH-001.

### FR-AUTH-004 Role and ownership enforcement (P0)

- **Story:** As a student, I want my records to be visible only to me and to administrators.
- **Acceptance criteria:**
  1. Every protected request is checked on the server for login, role and ownership.
  2. A student requesting another student's record, by any ID in the URL or body, gets "not found".
  3. A student calling any admin function gets "access denied".
  4. Hiding a button is never the only protection.
- **Depends on:** FR-AUTH-001.

### FR-AUTH-005 Account lockout (P0)

- **Story:** As the college, I want repeated password guessing to be blocked.
- **Acceptance criteria:**
  1. Five consecutive failed logins lock the account for 15 minutes.
  2. A locked account shows "Too many attempts. Try again in a few minutes."
  3. A successful login resets the counter.
  4. An admin can unlock an account by resetting its password.
- **Depends on:** FR-AUTH-001.

## 5. Student academic portal

### FR-STU-001 Dashboard (P0)

- **Story:** As a student, I want one page that shows what matters today.
- **Acceptance criteria:** The dashboard shows:
  1. Overall attendance and any subjects below the required percentage.
  2. Latest SGPA and current CGPA.
  3. Today's classes in time order.
  4. The five most recent notices.
  5. Upcoming deadlines in the next 14 days.
  6. Up to three revision recommendations.
- **Edge cases:** Each card has its own empty state. A failure in one card does not blank the others.
- **Depends on:** FR-ATT-001, FR-MRK-002, FR-TT-001, FR-NOT-001, FR-PRG-002.

### FR-STU-002 Profile (P0)

- **Story:** As a student, I want to check that the college has my details right.
- **Acceptance criteria:** Shows name, roll number, branch, semester, section, admission year, email and phone. All fields are read-only for the student.
- **Edge cases:** Missing email or phone shows "Not provided".

### FR-ATT-001 View own attendance (P0)

- **Story:** As a student, I want subject-wise attendance so that I know where I stand.
- **Preconditions:** Logged in as a student.
- **Acceptance criteria:**
  1. For each subject of the current semester: classes held, classes attended and percentage to two decimals.
  2. An overall percentage across subjects.
  3. Selecting a subject shows the dated history, newest first.
- **Edge cases:** A subject with no classes recorded shows "No classes recorded yet", not 0%.
- **Depends on:** FR-ADM-002.

### FR-ATT-002 Low-attendance flag (P0)

- **Acceptance criteria:** A subject below the required percentage (OQ-2, default 75%) is marked "Below required" with text and colour, never colour alone.
- **Depends on:** FR-ATT-001.

### FR-ATT-003 Attendance calculator (P1)

- **Story:** As a student, I want to know how many classes I must attend to reach a target.
- **Acceptance criteria:**
  1. Given a subject and a target percentage, show the number of consecutive classes to attend to reach it.
  2. If already above the target, show how many classes can be missed while staying at or above it.
  3. The result is labelled an estimate based on recorded classes.
- **Edge cases:** Target of 100% with any absence shows "Not reachable".

### FR-MRK-001 View own marks (P0)

- **Acceptance criteria:** For each subject, list published assessments with marks obtained, maximum marks and percentage. Unpublished assessments are not shown.
- **Edge cases:** Marked absent shows "Absent", not 0.
- **Depends on:** FR-ADM-003.

### FR-MRK-002 View semester results and CGPA (P0)

- **Acceptance criteria:**
  1. Each published semester shows SGPA, credits earned and result status.
  2. CGPA is calculated from published semesters (rule per OQ-3) and shown to two decimals.
- **Edge cases:** No published results shows "Results not published yet".
- **Depends on:** FR-ADM-004.

### FR-TT-001 View timetable (P0)

- **Acceptance criteria:** Shows today's classes and a weekly view for the student's branch, semester and section, with subject, time, room and faculty name.
- **Edge cases:** A day with no classes shows "No classes".
- **Depends on:** FR-ADM-005.

### FR-NOT-001 Notices and deadlines (P0)

- **Acceptance criteria:**
  1. Published notices are listed newest first, filterable by category.
  2. A student sees general notices and those targeted at their branch and semester.
  3. A notice with a due date appears under upcoming deadlines until that date passes.
- **Edge cases:** Draft notices are never visible to students.
- **Depends on:** FR-ADM-006.

## 6. Learning resource hub

### FR-LIB-001 Browse and filter (P0)

- **Story:** As a student, I want to narrow resources to exactly what I am studying.
- **Acceptance criteria:**
  1. Filter by subject, unit, semester and type (notes, syllabus, lab manual, question paper, model paper, important questions).
  2. Question papers also filter by exam year and exam type.
  3. Results are paginated, 20 per page, and show title, subject, unit, type and upload date.
- **Edge cases:** No match shows "No resources match these filters" with a clear-filters action.
- **Depends on:** FR-ADM-007.

### FR-LIB-002 Search (P0)

- **Acceptance criteria:** A search box matches words in the title and description, ignoring case, and combines with the filters.
- **Edge cases:** Queries shorter than two characters are ignored.

### FR-LIB-003 Preview and download (P0)

- **Acceptance criteria:**
  1. A PDF opens in an in-page viewer.
  2. A download action saves the file under its original name.
  3. File links expire within five minutes and cannot be used without logging in first.
- **Edge cases:** If the file is unavailable, show "This file is temporarily unavailable" and keep the page usable.

### FR-LIB-004 Bookmarks (P0)

- **Acceptance criteria:** A student can bookmark and un-bookmark any published resource and see their bookmarks on one page. Bookmarking twice has no extra effect.
- **Edge cases:** A bookmarked resource that is later hidden disappears from the list.

### FR-LIB-005 Report a resource (P1)

- **Acceptance criteria:** A student can report a resource with a short reason. Admins see open reports.

### FR-LIB-006 Question paper analyzer (P2)

- **Acceptance criteria:** Shows unit-wise categorization and topic frequency across available papers, labelled as a revision aid that does not predict future questions.

## 7. Quick Exam

### FR-QZ-001 Start a test (P0)

- **Story:** As a student, I want a short test on one unit so that I can check my understanding.
- **Acceptance criteria:**
  1. Choose a subject, a unit and a length of 5, 10 or 20 questions.
  2. Questions are picked at random from published questions for that unit.
  3. Answer options are shown in random order.
  4. The correct answers are not present anywhere in the page before submission.
- **Edge cases:** If the unit has fewer questions than requested, say how many are available and offer that length.
- **Depends on:** FR-ADM-008.

### FR-QZ-002 Timer and submission (P0)

- **Acceptance criteria:**
  1. The time limit is one minute per question and is enforced by the server.
  2. A visible countdown runs, and the test submits itself at zero.
  3. A test can be submitted once.
  4. A submission arriving after the time limit plus a short grace period is rejected and the attempt is closed as expired.
- **Edge cases:** Refreshing the page does not restart the timer. Unanswered questions count as wrong.

### FR-QZ-003 Scoring and result (P0)

- **Acceptance criteria:**
  1. Scoring is done by Java code, never by AI.
  2. The result shows score, percentage, and for each question the chosen answer, the correct answer and the explanation.
  3. The result offers "Retake" and "Ask AI to explain".
- **Depends on:** FR-QZ-002.

### FR-QZ-004 Attempt history (P0)

- **Acceptance criteria:** A student sees their past attempts with date, subject, unit and score, and can reopen any result. They cannot open another student's attempt.

### FR-PRG-001 Strengths and weaknesses (P0)

- **Acceptance criteria:**
  1. For each unit attempted, accuracy is calculated over the student's 20 most recently answered questions in that unit.
  2. With at least 5 answers: under 60% is "Needs revision", 80% and over is "Strong", otherwise "Improving".
  3. With fewer than 5 answers the unit shows "Not enough data".
- **Depends on:** FR-QZ-003.

### FR-PRG-002 Revision recommendations (P0)

- **Acceptance criteria:** For each "Needs revision" unit, show up to three published resources for that unit (notes first) and a "Retake test" link. Rules are plain Java, not AI.
- **Edge cases:** A weak unit with no resources shows only the retake link.
- **Depends on:** FR-PRG-001, FR-LIB-001.

## 8. Planner

### FR-PLN-001 Study tasks (P1)

- **Acceptance criteria:** A student can add a task with an optional subject and due date, mark it done, edit it and delete it. Tasks are private to the student.

### FR-PLN-002 Exam countdown (P1)

- **Acceptance criteria:** The dashboard shows the days remaining until the next notice in the "Exam" category that has a due date.

## 9. AI study assistant

Behaviour rules are in [13-AI-rules.md](13-AI-rules.md). Limits and formats are in [12-AI-features-spec.md](12-AI-features-spec.md).

### FR-AI-001 Explain a topic (P0)

- **Story:** As a student, I want a topic explained simply or in exam-answer form.
- **Acceptance criteria:**
  1. Enter a topic, optionally choose a subject and unit, and choose a format: simple, 5-mark or 10-mark.
  2. The answer has an explanation, key points and, where useful, an example.
  3. The answer is labelled AI-generated and says whether it is based on general knowledge or on text the student supplied.
- **Edge cases:** A request unrelated to study is declined politely.

### FR-AI-002 Summarize text (P0)

- **Acceptance criteria:** Pasted text of 200 to 12,000 characters becomes a summary, key points and a list of definitions found in the text. The summary uses only what is in the text.
- **Edge cases:** Text that is too short or too long is rejected with the limit stated.

### FR-AI-003 Generate practice questions (P0)

- **Acceptance criteria:**
  1. From a topic or pasted text, generate 1 to 10 questions of a chosen type: MCQ, short answer or long answer.
  2. Each question comes with an answer and an explanation.
  3. Generated questions are for practice only and are not added to the official question bank.
- **Edge cases:** Generated MCQs with a missing or ambiguous correct option are discarded before display.

### FR-AI-004 Usage limits and failure handling (P0)

- **Acceptance criteria:**
  1. Each user has a daily request limit (default 30) and sees how many remain.
  2. If the AI service fails or takes too long, the user sees a clear message within 30 seconds and can retry.
  3. The rest of the application works when the AI service is down or switched off.
- **Edge cases:** A failed request does not consume the daily limit.

### FR-AI-005 Summarize a library PDF (P1)

- **Acceptance criteria:** From a resource page, "Summarize" produces a summary from the PDF's extracted text. Scanned PDFs without text show "This PDF has no readable text".

### FR-AI-006 Flowchart generation (P1)

- **Acceptance criteria:** A topic or process description becomes a rendered flowchart of at most 20 steps.

### FR-AI-007 Flashcards (P2)

- **Acceptance criteria:** Notes become question-and-answer cards the student can flip through.

## 10. Admin and content management

### FR-ADM-001 Manage student accounts (P0)

- **Acceptance criteria:**
  1. Create a student with roll number, name, branch, semester, section and admission year. The system generates a random temporary password and shows it once.
  2. Edit profile fields; search by roll number or name.
  3. Deactivate and reactivate an account. Students are never deleted.
  4. Reset a password, which issues a new temporary password and unlocks the account.
- **Edge cases:** Duplicate roll number is rejected with "A student with this roll number already exists".

### FR-ADM-002 Enter attendance (P0)

- **Acceptance criteria:**
  1. Choose subject, section, date and period, then mark each student present or absent in one screen.
  2. A saved sheet can be corrected later.
  3. Every correction is written to the audit log.
- **Edge cases:** Saving the same subject, date and period again updates, never duplicates. Future dates are rejected.

### FR-ADM-003 Manage assessments and marks (P0)

- **Acceptance criteria:**
  1. Create an assessment for a subject with a name, type, maximum marks and date.
  2. Enter marks for all students of that subject in one screen, or mark a student absent.
  3. Marks are visible to students only after the assessment is published.
  4. Every change to saved marks is written to the audit log with old and new values.
- **Edge cases:** Marks below zero or above the maximum are rejected for that row, and the other rows are not saved until all are valid.

### FR-ADM-004 Enter semester results (P0)

- **Acceptance criteria:** Record SGPA (0 to 10), credits earned and status for a student and semester. Changes are audited.

### FR-ADM-005 Manage subjects, units and timetable (P0)

- **Acceptance criteria:** Create and edit subjects (code, name, branch, semester, credits) and their numbered units. Create and edit timetable entries. A subject in use is deactivated, not deleted.
- **Edge cases:** Overlapping timetable entries for the same section are rejected.

### FR-ADM-006 Manage notices (P0)

- **Acceptance criteria:** Create a notice as a draft, optionally targeted at a branch and semester and with a due date; publish it; unpublish it; edit it.

### FR-ADM-007 Upload and manage resources (P0)

- **Acceptance criteria:**
  1. Upload a PDF of up to 10 MB with title, description, subject, unit and type. Question papers also need exam year and exam type.
  2. Files that are not real PDFs are rejected.
  3. Edit the details, hide a resource from students, or delete it.
- **Edge cases:** If storage fails, nothing is saved and the admin can retry.

### FR-ADM-008 Manage the question bank (P0)

- **Acceptance criteria:** Create and edit MCQs with four options, one correct option, an explanation, a subject, a unit and a difficulty. Questions are draft until published. A question that has been used in an attempt is retired, not deleted.

### FR-ADM-009 Audit log (P0 write, P1 viewer)

- **Acceptance criteria:** Changes to marks, attendance, results, account status and passwords record who, what, when, and old and new values. Entries cannot be edited or deleted in the application. P1 adds a filterable admin screen.

### FR-ADM-010 CSV bulk import (P1)

- **Acceptance criteria:** Upload a CSV of students. The file is validated completely first; any invalid row stops the import and all problems are listed by row number.

### FR-ADM-011 Usage statistics (P2)

- **Acceptance criteria:** Counts of resource views and quiz attempts by subject.

## 11. Cross-cutting

### FR-UX-001 Errors, empty states and loading states (P0)

- **Acceptance criteria:**
  1. Every list has an empty state that says what is missing and, where possible, what to do.
  2. Every request shows a loading indicator after 300 ms.
  3. Error messages say what happened and what to do next, in plain language, with no technical details.
  4. A form keeps what the user typed when submission fails.
  5. Field errors appear next to the field.

Wording and component rules are in [07-ui-ux-spec.md](07-ui-ux-spec.md).

## 12. Out of scope

Faculty portal, fee payment, admissions, hostel, library lending, mobile apps, discussion forums, voice assistant, OCR for scanned papers, AI answers grounded in the whole resource library, and any prediction of exam questions. See [03-scope-and-roadmap.md](03-scope-and-roadmap.md).
