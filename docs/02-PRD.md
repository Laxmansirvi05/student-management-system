# 02. Product Requirements Document

> Status: Draft v0.3 · Owner: Laxman Sirvi · Last updated: 2026-10-11

This document says what the application must do from the user's point of view. How it is built is in [04-TRD.md](04-TRD.md). Non-functional requirements (performance, security, reliability) live in the TRD and the [security plan](11-security-plan.md) and are not repeated here.

## 1. Roles

| Role | Can do |
| --- | --- |
| **Student** | Read their own academic records; read published notices and resources; take tests; use the AI assistant; manage their own bookmarks and study tasks. |
| **Admin** | Create and maintain students, academic records, subjects, timetables, notices, resources and the question bank. |

Exact permissions are in [10-authorization-matrix.md](10-authorization-matrix.md).

## 2. Priorities

- **P0:** required for the assignment submission. A P0 requirement is not dropped, deferred or reduced unless the owner approves a scope change and it is recorded as described in [03-scope-and-roadmap.md](03-scope-and-roadmap.md) §9. Nothing in any other document pre-authorizes cutting a P0 item.
- **P1:** should have; built only after every P0 item works.
- **P2:** later.

A priority is changed only by editing this document, stating the reason, with the owner's approval.

## 3. Requirement index

| ID | Requirement | Priority |
| --- | --- | --- |
| FR-AUTH-001 | Log in with institutional email | P0 |
| FR-AUTH-002 | Log out and session expiry | P0 |
| FR-AUTH-003 | First-time password and password change | P0 |
| FR-AUTH-004 | Role and ownership enforcement | P0 |
| FR-AUTH-005 | Account lockout | P0 |
| FR-AUTH-006 | Institutional email identity | P0 |
| FR-STU-001 | Student dashboard | P0 |
| FR-STU-002 | Student profile | P0 |
| FR-STU-003 | Edit own personal details and photo | P0 |
| FR-STU-004 | Settings | P0 |
| FR-LEG-001 | Privacy policy and terms of use | P0 |
| FR-ATT-001 | View own attendance | P0 |
| FR-ATT-002 | Low-attendance flag | P0 |
| FR-ATT-003 | Attendance calculator | P1 |
| FR-MRK-001 | View own marks | P0 |
| FR-MRK-002 | View semester results and CGPA | P0 |
| FR-TT-001 | View timetable | P0 |
| FR-NOT-001 | Notices and deadlines | P0 |
| FR-NOT-002 | Official document of a notice | P1 |
| FR-LIB-001 | Browse and filter resources | P0 |
| FR-LIB-002 | Search resources | P0 |
| FR-LIB-003 | Preview and download | P0 |
| FR-LIB-004 | Bookmarks | P0 |
| FR-LIB-005 | Report a resource | P1 |
| FR-LIB-006 | Previous-year question papers (PYQs) | P1 |
| FR-CMP-001 to FR-CMP-004 | Student complaints: public, private and secure | P1 |
| FR-NTE-001 to FR-NTE-003, FR-AST-001, FR-ROB-001 | Notes, the Quick Assistant and the floating robot | P1 |
| FR-LIB-006 | Question paper analyzer | P2 |
| FR-QZ-001 | Start a Quick Exam | P0 |
| FR-QZ-002 | Timer and submission | P0 |
| FR-QZ-003 | Scoring and result | P0 |
| FR-QZ-004 | Attempt history | P0 |
| FR-PRG-001 | Unit-wise strengths and weaknesses | P0 |
| FR-PRG-002 | Revision recommendations | P0 |
| FR-PLN-001 | Study tasks | P1 |
| FR-PLN-002 | Exam countdown | P1 |
| FR-COD-001 to FR-COD-005 | Coding portfolio and leaderboard | P1 |
| FR-AI-001 | Explain a topic | P0 |
| FR-AI-002 | Summarize text | P0 |
| FR-AI-003 | Generate practice questions | P0 |
| FR-AI-004 | Usage limits and failure handling | P0 |
| FR-AI-005 | Summarize a library PDF | **Dropped** (out of scope, 2026-10-24) |
| FR-AI-006 | Flowchart generation | P1 |
| FR-AI-007 | Flashcards | P2 |
| FR-ADM-001 | Provision and manage student accounts | P0 |
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
| FR-UX-002 | Public landing page | P1 |

## 4. Authentication and accounts

> **Changed on 2026-10-11 at the owner's request.** Students now log in with their institutional email address instead of their roll number, and a newly provisioned student's first password is the roll number. FR-AUTH-001, FR-AUTH-003, FR-AUTH-005 and FR-ADM-001 were revised and FR-AUTH-006 was added. The decision, and the security exception it contains, are recorded in [ADR 0008](decisions/0008-institutional-email-login.md). The change is logged in [03-scope-and-roadmap.md](03-scope-and-roadmap.md) §9.

### FR-AUTH-001 Log in with institutional email (P0)

- **Story:** As a student, I want to log in with my institutional email address and my password so that I reach my own dashboard. As an admin, I want to log in with my administrator email and password.
- **Preconditions:** An active account exists. A student's login email is their roll number followed by `@` and the official student domain, for example `23cse001@university.example`. The domain is configuration, not code.
- **Acceptance criteria:**
  1. The login form asks for an email address and a password. A roll number on its own is not accepted as the identifier.
  2. Correct credentials create a session and open the dashboard for the user's role, or the change-password page if the password is temporary (FR-AUTH-003).
  3. The email is trimmed and compared in lower case, so letter case and surrounding spaces do not matter.
  4. A wrong password, an email with no account and a deactivated account all get the same response and the same message: "Incorrect email or password."
  5. Nothing in any login response shows whether an account exists for an email address.
  6. An admin never lands on the student dashboard, and the reverse.
  7. The login request carries the CSRF token like every other state-changing request.
- **Failure states:**

  | Situation | What the user sees |
  | --- | --- |
  | A field is empty | "Enter your email and password." The request is not sent. |
  | The text is not an email address | "Enter a valid email address." The same for any text, whoever it belongs to. |
  | Wrong password, unknown email or deactivated account | "Incorrect email or password." |
  | Too many failed attempts for that email | "Too many attempts. Try again in a few minutes." (FR-AUTH-005) |
  | Too many attempts from one network address | "Too many requests. Please wait a moment." |
  | The server cannot be reached | "You appear to be offline." with Retry |

- **Depends on:** FR-AUTH-006.

### FR-AUTH-002 Log out and session expiry (P0)

- **Story:** As a user, I want to log out so that nobody else can use my session on a shared computer.
- **Acceptance criteria:**
  1. Log out ends the session on the server and returns to the login page.
  2. After log out, the back button does not reveal protected data.
  3. A session idle for 30 minutes expires.
  4. When an expired session calls the API, the page shows "Your session has expired. Please log in again." and returns to login.
- **Edge cases:** The longest test (20 minutes) is shorter than the idle limit, so a test cannot expire the session by itself. If a session does end during a test, unsaved answers are lost.
- **Depends on:** FR-AUTH-001.

### FR-AUTH-003 First-time password and password change (P0)

- **Story:** As a newly provisioned student, I log in once with my temporary password and must immediately choose my own, so that the temporary password stops working.
- **Preconditions:** The account has a temporary password. For a newly provisioned student it is the student's roll number (FR-ADM-001). After an admin reset, and for the first admin, it is a different, non-predictable password.
- **Acceptance criteria:**
  1. After a successful login with a temporary password, the user is taken to the change-password page.
  2. **Until the password has been changed, the server refuses every other protected function.** Only these work: seeing who is logged in, changing the password, and logging out. Calling any other endpoint directly returns "password change required". Opening any other page redirects to the change-password page.
  3. The user gives the current (temporary) password and enters the new password twice. The two entries must match.
  4. A new password has 8 to 72 characters and, ignoring letter case, is not the user's roll number, not the user's email address, not the part of the email before the `@`, and not the current password.
  5. On success: the "must change password" flag is cleared, the user's other sessions are ended, the current session gets a new identifier, and the user is taken to their dashboard.
  6. From that moment the old temporary password does not work. A roll number can never be chosen as a password at any later change either.
  7. Any user can change their password later in the same way.
  8. The change is recorded in the audit log with no password and no hash (FR-ADM-009).
- **Edge cases:** A wrong current password is rejected and counts toward lockout. If the session expires on the change-password page, the user logs in again with the temporary password.
- **Depends on:** FR-AUTH-001.

### FR-AUTH-004 Role and ownership enforcement (P0)

- **Story:** As a student, I want my records to be visible only to me and to administrators.
- **Acceptance criteria:**
  1. Every protected request is checked on the server for login, role and ownership.
  2. A student requesting another student's record, by any ID in the URL or body, gets "not found".
  3. A student calling any admin function gets "access denied".
  4. Hiding a button is never the only protection.
  5. Logging in with an email address instead of a roll number gives no additional permission. The session still carries only the account's internal ID and role, and no request can change a role.
- **Depends on:** FR-AUTH-001.

### FR-AUTH-005 Account lockout (P0)

- **Story:** As the college, I want repeated password guessing to be blocked.
- **Acceptance criteria:**
  1. Five consecutive failed logins for the same email address block further attempts for that email for 15 minutes.
  2. **The block works the same way whether or not an account exists for the email.** The message "Too many attempts. Try again in a few minutes." therefore says nothing about the account.
  3. A successful login resets the counter.
  4. An admin can unlock an account by resetting its password (available from milestone M2, when FR-ADM-001 exists).
  5. Separately, one network address may make at most 20 login attempts in 10 minutes.
- **Edge cases:** A correct password during the block still fails with the same message.
- **Depends on:** FR-AUTH-001.

### FR-AUTH-006 Institutional email identity (P0)

- **Story:** As the college, I want each account identified by one institutional email address, kept separate from the roll number, so that identity and academic records are never mixed up.
- **Preconditions:** The official student email domain has been configured.
- **Acceptance criteria:**
  1. Every account has exactly one login email. It is stored trimmed and in lower case, and it is unique across all accounts.
  2. A student's login email is generated by the system: the roll number in lower case, an `@`, and the configured domain. Nobody types it in. (Whether this is the institution's real naming convention is open decision OQ-15.)
  3. The domain comes from configuration (`STUDENT_EMAIL_DOMAIN`). It is not written into code or tests, and development and production each set their own value.
  4. The roll number stays in the student profile as the academic identifier and is unique. Academic records refer to the student, never to the email address. Changing how a student logs in changes no roll number and no academic record.
  5. Neither the login email nor the roll number can be changed through the application once the account exists (OQ-16).
  6. The profile shows the institutional email. No second email address is stored for a student.
  7. **The email address is an identifier only.** The application sends no email and makes no claim that an address has been verified or that its mailbox belongs to the student.
  8. Uniqueness of the email and of the roll number is enforced by the database, not only by the application.
- **Edge cases:** Two roll numbers that differ only in letter case are the same roll number and would give the same email; the second is rejected.
- **Depends on:** nothing.

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
- **Changed on 2026-10-24 at the owner's request:** the Results card shows the CGPA on top and the SGPA of every published semester below it (criterion 2); deadlines are shown for the next **30** days (criterion 5), so a fee deadline appears in time; the revision-recommendations card is removed from the dashboard (criterion 6; recommendations stay on the Progress page); and an "Upcoming exams" card lists the next exams, soonest first (FR-PLN-002).
- **Edge cases:** Each card has its own empty state. A failure in one card does not blank the others. The recommendations card stays in its empty state until Quick Exam is built (milestone M4).
- **Depends on:** FR-ATT-001, FR-MRK-002, FR-TT-001, FR-NOT-001, FR-PRG-002.

### FR-STU-002 Profile (P0)

- **Story:** As a student, I want to check that the college has my details right.
- **Acceptance criteria:** Shows name, roll number, institutional email, branch, semester, section, admission year and phone. The name, roll number, email, branch, semester, section and admission year are read-only for the student.
- **Edge cases:** A missing phone number shows "Not provided".

> **Changed on 2026-10-14 at the owner's request.** The profile was fully read-only. A student may now change their phone number, a short text about themselves and a photo (FR-STU-003). A settings page (FR-STU-004) and two legal pages (FR-LEG-001) were added. The decision and its security controls are in [ADR 0009](decisions/0009-student-personal-details-and-photo.md), and the change is logged in [03-scope-and-roadmap.md](03-scope-and-roadmap.md) §9.

### FR-STU-003 Edit own personal details and photo (P0)

- **Story:** As a student, I want to keep my own contact number, a few lines about myself and my photo up to date.
- **Acceptance criteria:**
  1. A student can change their phone number and a text of up to 280 characters about themselves. Both are optional and can be cleared.
  2. A student can add, replace and remove a profile photo: JPEG or PNG, at most 2 MB.
  3. The stored photo is a new picture drawn by the server. The uploaded file, and anything hidden in it such as the place it was taken, is not kept.
  4. A student cannot change their name, roll number, email, branch, semester, section or admission year, by the page or by a direct request.
  5. Every change applies to the student who is logged in. No request names a student.
  6. The photo is shown through a link that lasts at most five minutes and is given only to its owner.
  7. **Added on 2026-10-18 at the owner's request:** a student can also keep a personal email, links to their LinkedIn, GitHub and portfolio, and a short list of skills. All optional. A link must be https with no user name or password in it; a LinkedIn or GitHub link must be on that site. Links open in a new tab and never pass on the page they came from.
- **Edge cases:** A file that is not a picture is refused with a clear message. If file storage is unavailable, the page says so and the rest of the profile still works.
- **Depends on:** FR-STU-002, the storage bucket of FR-LIB-003.

### FR-STU-004 Settings (P0)

- **Story:** As a user, I want one place to change my password and adjust how the pages behave.
- **Acceptance criteria:**
  1. Students and administrators each have a Settings page.
  2. It offers the password change of FR-AUTH-003, with the same rules and messages.
  3. A "Reduce motion" choice turns the page animations off. It is remembered in the browser, not in the account.
  4. It shows the account's name, login email and role, and links to the privacy policy and the terms.
- **Depends on:** FR-AUTH-003.

### FR-LEG-001 Privacy policy and terms of use (P0)

- **Acceptance criteria:**
  1. A privacy policy and the terms of use can be read without logging in, and are linked from the login page and from Settings.
  2. Each describes what the application actually does. The privacy policy names what is kept, what is not, who can see it, where it is stored, how long, and what the user can do.
  3. Each states that the project is a demonstration on invented students, and that the college must review and adopt the text before real records are used.
  4. The pages contain no script.

### FR-ATT-001 View own attendance (P0)

- **Story:** As a student, I want subject-wise attendance so that I know where I stand.
- **Preconditions:** Logged in as a student.
- **Acceptance criteria:**
  1. For each subject of the current semester: classes held, classes attended and percentage to two decimals.
  2. An overall percentage across subjects.
  3. Selecting a subject shows the dated history, newest first.
- **Edge cases:** A subject with no classes recorded shows "No classes recorded yet", not 0%.
- **Changed on 2026-10-22 at the owner's request:** the college gives each student a total, not subject-wise records, so the page shows only the total attendance (from the college's report when there are no subject-wise records) and the planner of FR-ATT-003. Criteria 1 and 3 are no longer shown on the page; the subject-wise API stays for later.
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
- **Built on 2026-10-22, on the total instead of a subject:** for 70%, 75%, 80% and 85% the page shows how many classes to attend in a row to reach each, or how many can be missed in a row while staying at or above it, and a "what if" box for the coming classes. 100% is not offered.

### FR-MRK-001 View own marks (P0)

- **Acceptance criteria:** For each subject, list published assessments with marks obtained, maximum marks and percentage. Unpublished assessments are not shown.
- **Edge cases:** Marked absent shows "Absent", not 0.
- **Changed on 2026-10-22 at the owner's request:** the student Results page no longer shows marks; it shows only semester results (FR-MRK-002). The marks API and the admin marks page stay.
- **Depends on:** FR-ADM-003.

### FR-MRK-002 View semester results and CGPA (P0)

- **Acceptance criteria:**
  1. Each published semester shows SGPA, credits registered, credits earned and result status.
  2. CGPA is the average of the published SGPAs weighted by the credits **registered** in each semester, shown to two decimals. Withheld semesters are left out. The exact rule is in [08-database-design.md](08-database-design.md) §4.8. It is the default until the college's regulation is confirmed (OQ-3).
- **Edge cases:** No published results shows "Results not published yet".
- **Changed on 2026-10-18 for the college's real results:** each semester also lists its subjects with their grades, and a subject cleared in a supplementary examination shows both grades. While a subject is failed the college publishes no SGPA for that semester, so the SGPA is shown as "published once every subject is cleared", and no CGPA is shown until every published semester has an SGPA, as the college does. The rule is in [08-database-design.md](08-database-design.md) §4.8. **For the owner to confirm.**
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

### FR-NOT-002 Official document of a notice (P1)

Added on 2026-10-22 at the owner's request: the college's circular carries its stamp and signature, so students should see the real document, not only a summary.

- **Acceptance criteria:**
  1. An admin can attach one official document to a notice (a PDF, or a JPEG or PNG photo or scan, up to 10 MB), replace it or remove it. A text-only notice stays possible.
  2. A notice with a document is marked "Official notice attached" in the list. Opening it shows a short summary and the document itself: a picture inline, a PDF in a viewer, with "Open full size" and "Download".
  3. A student reaches the document only through a five-minute signed link, and only for a notice they may see (published and meant for them); otherwise "not found".
  4. A picture is drawn again as a new JPEG (at most 2,400 pixels on its longer side), so nothing but the pixels is stored.
- **Edge cases:** A file that is not a real PDF or picture, or is too small to read, is refused and nothing is stored. Deleting the notice or replacing the document removes the old file.
- **Depends on:** FR-NOT-001, FR-ADM-006.

## 5b. Student complaints

Added on 2026-10-23 at the owner's request. The decision and its reasons are in [ADR 0013](decisions/0013-student-complaints.md).

### FR-CMP-001 Raise a complaint (P1)

- **Story:** As a student, I want to raise a problem and choose who sees it.
- **Acceptance criteria:**
  1. A student chooses a type, a category (academic, exams, facilities, faculty, administration, other), a title of 5 to 120 characters and a description of 20 to 2,000 characters, as plain text.
  2. **Public:** every student and the administrator can read it. The student chooses whether other students see their roll number; if not, it shows "Anonymous". The administrator can always see who raised it.
  3. **Private:** only the administrator reads it, and sees the student.
  4. **Secure:** only the administrator reads it, and never sees the student, in any screen, response or audit entry.
  5. A student can raise 5 complaints in 24 hours.
- **Edge cases:** "Show my roll number" is ignored on a private or secure complaint.

### FR-CMP-002 Public board and support (P1)

- **Acceptance criteria:**
  1. Students list public complaints that are not rejected, newest first or most supported first, filtered by category and status.
  2. A student can support a public complaint once, take the support back, and cannot support their own or a resolved one. The page shows the number of supporters.
  3. From 5 supporters a complaint is marked a group complaint. The administrator sees the supporters.

### FR-CMP-003 Follow and withdraw (P1)

- **Acceptance criteria:** A student lists their own complaints of every type with status and the administrator's response, and can delete one while it is Open. Another student's complaint, a private or secure one, or a rejected public one is "not found".

### FR-CMP-004 Handle complaints (P1)

- **Acceptance criteria:** The administrator lists every complaint (open first), filters by type and status, opens one and sets a status (Open, In review, Resolved, Rejected) with an optional response. Each change is audited with the status only. A rejected public complaint leaves the public board.

## 5c. Notes, the Quick Assistant and the floating robot

Added on 2026-10-23 at the owner's request, as a port of the owner's Opportunity Radar features into this application's architecture. The decision and its reasons are in [ADR 0014](decisions/0014-notes-quick-assistant-and-robot.md). Written down here after they were built, from the code, during the 2026-10-23 audit remediation (CC-10).

### FR-NTE-001 Notes and folders (P1)

- **Story:** As a student, I want to write and organise my own notes next to my college records.
- **Acceptance criteria:**
  1. A student creates, edits, pins, archives, tags, duplicates and links notes, in folders, with a rich-text editor. Changes save automatically; a failed save is tried again, and unsaved text is sent when the page is hidden or closed.
  2. Pictures (stored as new JPEGs) and PDFs can be attached. Note HTML is rebuilt from an allow-list on the server.
  3. Deleting moves a note to the Trash; only "delete permanently" or emptying the Trash destroys it with its files. Clearing a note's text moves it to the Trash, never destroys it.
  4. A note and its files are the student's own: another student's note or file is "not found".

### FR-NTE-002 Sharing notes (P1)

- **Acceptance criteria:**
  1. The owner shares a note with named classmates (by roll number or college email), to read or to edit its title and text, and can share it by link, read-only, for any signed-in student who has the link.
  2. The owner sees recipients by roll number only, never by name.
  3. A file of a shared note opens for its uploader, the note's owner, a named recipient, or a student who presents the share link; never by its number alone, and for nobody but the owner while the note is in the Trash.

### FR-NTE-003 Quick Note (P1)

- **Acceptance criteria:** From the robot, a student writes a note in a small composer; it is filed in the "Quick Notes" folder with the same editor and autosave as FR-NTE-001.

### FR-AST-001 Quick Assistant chat (P1)

- **Acceptance criteria:**
  1. From the robot, a student chats with the AI assistant, under the same limits and rules as FR-AI-004 and docs/13.
  2. The questions and answers are stored so the student can read them again: only the six newest chats, read by that student only (never by an administrator), deletable by the student, and nothing is stored when an answer fails. The privacy policy says so.

### FR-ROB-001 Floating robot (P1)

- **Acceptance criteria:** Every student page shows a draggable 3D robot (with a plain badge until the scene is drawn, or for good if it cannot load) that opens the Quick Note (triple tap, Enter) and the Quick Assistant (double tap, A). Its scene loads after the page is usable and never from a CDN.

## 6. Learning resource hub

### FR-LIB-001 Browse and filter (P0)

- **Story:** As a student, I want to narrow resources to exactly what I am studying.
- **Acceptance criteria:**
  1. Filter by subject, unit, semester and type (notes, syllabus, lab manual, question paper, model paper, important questions).
  2. Question papers also filter by exam year and exam type.
  3. Results are paginated, 20 per page, and show title, subject, unit, type and upload date.
- **Edge cases:** No match shows "No resources match these filters" with a clear-filters action.
- **Changed on 2026-10-22 at the owner's request:** without a search or filter, the hub shows one folder per subject (newest semester first, with how many resources each has). Opening a folder lists that subject's resources grouped as whole-subject material and then unit by unit.
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

### FR-LIB-006 Previous-year question papers (P1)

Added on 2026-10-18 at the owner's request. The decision is [ADR 0012](decisions/0012-previous-year-question-papers.md).

- **Story:** As a student, I want to find the previous-year papers of a subject quickly, open or download them, and keep the ones I need.
- **Acceptance criteria:**
  1. Papers are organised by branch and subject name, and each has its year, month, kind (regular, supplementary or mid) and, when known, its regulation.
  2. A student can browse subjects with their paper counts, search by subject, course code or year (several words, in any order), filter by branch, year, exam and regulation, and sort by newest, oldest, most downloaded or subject.
  3. A student can open a paper in the page, download it under a readable name, and save or unsave it. Saved papers are listed. A hidden paper is "not found" to students, including in their saved list.
  4. The papers a student opened are listed under "Recently opened", kept only in their own browser.
  5. A student sees papers only through links that last five minutes. No student can add, change or remove a paper.
  6. An admin can add a paper with its details or import an organised folder (`BRANCH/Subject/2025(December)-supply.pdf`), correct a paper's details, hide it from students, and remove it. A file already in the library, or a sitting already in the library, is refused with a clear message.
- **Edge cases:** Importing again skips what is already there. A file that is not a real PDF, or is over 10 MB, is refused and nothing is stored. If file storage is unavailable, the import stops and says so.
- **Depends on:** FR-ADM-007 for the upload checks and the storage rules.

### FR-LIB-005 Report a resource (P1)

- **Acceptance criteria:** A student can report a resource with a short reason. Admins see open reports.
- **As built (2026-10-24):** "Report a problem" on a resource's page; the reason is 5 to 500 characters; one open report per student and resource. Admins see open reports at the top of the Resources page, with the reporter's roll number, and resolve them (audited).

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
- **Changed on 2026-10-24 at the owner's request:** a test is started for a **whole subject**; the student chooses the subject and the length only, and the questions are drawn at random from all the subject's published questions, so each test differs. A question's unit is optional. The college's course question banks (40 to 50 questions each for Advanced Data Structures, OOP through Java and DBMS, written from the course notes and previous-year papers) are bundled with the application and loaded once into the subjects with those codes. Progress groups whole-subject answers under the subject.
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
  3. The result offers "Retake" and "Ask AI to explain". "Ask AI to explain" is delivered with the AI study assistant (milestone M5); until then the result offers "Retake" only.
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

Built on 2026-10-24 (T7-02), on the Planner page (`/student/planner.html`) and the dashboard.

### FR-PLN-001 Study tasks (P1)

- **Acceptance criteria:** A student can add a task with an optional subject and due date, mark it done, edit it and delete it. Tasks are private to the student.
- **As built (T7-02):** a task has a title (1 to 200 characters), optional notes (up to 1,000), an optional subject of the student's own, and an optional due date. The list has four views: To do (pending, soonest due first, undated last), Overdue (pending with a due date before today), Done (latest completed first) and All, each with its count. A done task can be reopened. Deleting asks first and is permanent. A new due date must be from today to five years ahead; an existing date may be kept when a task is edited, so an overdue task can still be renamed.

### FR-PLN-002 Exam countdown (P1)

- **Acceptance criteria:** The dashboard shows the days remaining until the next notice in the "Exam" category that has a due date.
- **Extended at the owner's request (T7-02, 2026-10-24):** a student can also add, edit and delete exam dates of their own (a name of 1 to 120 characters, an optional subject, a date from today to five years ahead). The countdown lists the student's own exams and the college's published Exam notices with a due date for the student's class (read-only, linked to the notice) together, soonest first; the student's own past exams are listed apart, latest first. The dashboard shows the next of them, whichever kind it is.
- **Exam papers (2026-10-24, owner's request):** an Exam notice can list its papers (date and subject, up to 30), entered by the admin on the notice form. Each paper still to come is one college exam in the countdown, named by its subject; an Exam notice without papers still counts by its due date. The dashboard lists up to eight upcoming exams. The notice page shows the papers. Days are counted from the campus date; an exam is "Today" on its day and past from the next day.

## 8b. Coding portfolio and leaderboard

Added on 2026-10-30 at the owner's request ([ADR 0017](decisions/0017-coding-portfolio-and-leaderboard.md)). Pages: Portfolio (`/student/portfolio.html`), Coding leaderboard (`/student/leaderboard.html`) and the admin Coding leaderboard (`/admin/leaderboard.html`).

### FR-COD-001 Link a coding account (P1)

- **Story:** As a student, I want to link my accounts on coding sites so that my work is in one place.
- **Acceptance criteria:**
  1. A student can link one account on each of GitHub, LeetCode, Codeforces, CodeChef, GeeksforGeeks and HackerRank, by handle or by pasting the profile link.
  2. A new link is *pending* and shows a verification code. The student puts the code in a text field they control on that site (bio, about me, display name, designation or organisation) and presses Verify; the server reads the public profile and verifies the link only if the code is there.
  3. A handle that another student has already verified on the same site is refused.
  4. A student can remove a link; its numbers and history go with it.
- **Edge cases:** an unknown handle, a site that does not answer and a missing code each have their own message. Verify can be pressed again after 20 seconds.

### FR-COD-002 Portfolio (P1)

- **Acceptance criteria:** The Portfolio page shows, for each verified account, the numbers that site publishes: problems solved (and by difficulty on LeetCode), contest rating and highest rating, rank or stars, score, and for GitHub public repositories, stars and followers; the total problems solved across sites; and a chart of the total over time. Each card says when its numbers were last read and has a Refresh button (at most every 15 minutes per account).

### FR-COD-003 Daily refresh (P1)

- **Acceptance criteria:** Every night the server reads each verified account again, one at a time, and keeps one snapshot per account per day. A site that fails leaves the last good numbers in place, marked with their date.

### FR-COD-004 Students' leaderboard (P1)

- **Acceptance criteria:**
  1. Students are ranked by total problems solved across their verified accounts (GitHub has no problem count and adds nothing). Equal totals share a rank.
  2. Only students who switched on "Show me on the leaderboard" appear, to every student of the college. The switch is off until the student turns it on.
  3. Each row shows the student's name, branch, semester and section, the total, the count per site and links to their profiles. The page also shows the student's own total and rank.

### FR-COD-005 Admin leaderboard (P1)

- **Acceptance criteria:** Admins see every student with a linked account, opted in or not, ranked the same way, with how many problems each solved in the last 30 days, pending links, and when their numbers were last read. Filters: branch, semester, section and a search by name or roll number. An admin can open any student's portfolio, read-only.

## 9. AI study assistant

Behaviour rules are in [13-AI-rules.md](13-AI-rules.md). Limits and formats are in [12-AI-features-spec.md](12-AI-features-spec.md).

**Acceptance needs a real provider.** FR-AI-001 to FR-AI-003 are accepted only when they work with a real AI provider. The mock provider is for development, automated tests and a demo-day outage. Accepting them with the mock provider only would be a scope change that the owner has not approved (OQ-9; see [03-scope-and-roadmap.md](03-scope-and-roadmap.md) §3).

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
  4. A daily limit across all users (default 500) protects the budget. When it is reached, the assistant shows as unavailable to everyone until the next day.
- **Edge cases:** A failed request does not consume the daily limit.

### FR-AI-005 Summarize a library PDF (Dropped)

- **Status:** Dropped by the owner on 2026-10-24; out of scope for this release. It is not outstanding work and is not counted in the completion figures. The text below is kept as the record of what was once planned. Students can still summarize text they paste (FR-AI-002).

- **Acceptance criteria:** From a resource page, "Summarize" produces a summary from the PDF's extracted text. Scanned PDFs without text show "This PDF has no readable text".

### FR-AI-006 Flowchart generation (P1)

- **Acceptance criteria:** A topic or process description becomes a rendered flowchart of at most 20 steps.
- **As built (2026-10-24):** a fourth tab, "Flowchart", on the AI assistant page. The student gives a topic or describes a process (not both). Java checks the graph the model returns and refuses any that does not hold together; the page draws it as boxes and arrows, lists it as numbered steps, and offers Mermaid text to copy. It counts as one AI request. See [12-AI-features-spec.md](12-AI-features-spec.md) §8.

### FR-AI-007 Flashcards (P2)

- **Acceptance criteria:** Notes become question-and-answer cards the student can flip through.

## 10. Admin and content management

### FR-ADM-001 Provision and manage student accounts (P0)

- **Story:** As an admin, I create accounts for the verified student roster so that students can log in.
- **Preconditions:** Logged in as an admin. The official student email domain is configured. There is no self-registration: students cannot create accounts.
- **Acceptance criteria:**
  1. **Create** a student from roster details: roll number, name, branch, semester, section, admission year and, optionally, a phone number. In one transaction the system creates the login account and the student profile linked to it.
  2. The system generates the login email (FR-AUTH-006) and sets the temporary password to the student's roll number as stored (upper case). Only a salted hash is stored. The account is marked "must change password".
  3. The temporary password is never shown, returned or logged. The response confirms the generated email and that a password change is required.
  4. A record is **rejected**, with the reason, when the roll number is missing, contains anything other than letters and digits, or already exists; when the generated email already exists; or when a required field is missing. Nothing is saved for a rejected record.
  5. **Search** by roll number, name or email. **Edit** profile fields other than the roll number.
  6. The list shows each account's state: awaiting first login, active, locked, or deactivated.
  7. **Deactivate** ends the student's sessions at once and blocks login. **Reactivate** restores the account with the password it had. Students are never deleted.
  8. **Reset password** issues a random temporary password, shown to the admin once. It ends the student's sessions, unlocks the account and forces a change at the next login. A reset never sets the password back to the roll number (OQ-16).
  9. **Admin accounts are not created here.** They are provisioned separately, with their own credentials, and never receive a roll-number password. The first admin is created from configuration with a strong password chosen by the owner.
  10. Creating, deactivating, reactivating and resetting are audited (FR-ADM-009).
- **Edge cases:** A duplicate roll number is rejected with "A student with this roll number already exists". The roster's accuracy is the admin's responsibility; the application checks format and uniqueness, not identity.
- **Security note:** Using the roll number as the first password is predictable, because the email address contains the same roll number. This is an accepted exception for the assignment and demonstration, with synthetic data only. It must be reviewed by the owner before any use with real students. See [ADR 0008](decisions/0008-institutional-email-login.md).
- **Depends on:** FR-AUTH-006.

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

- **Acceptance criteria:** Record SGPA (0 to 10), credits registered, credits earned and status for a student and semester, and publish it. Credits earned cannot exceed credits registered. Changes are audited.

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

- **Story:** As the college, I want a record of who changed official data so that a wrong or dishonest change can be traced and corrected.
- **Preconditions:** The change is made by a logged-in admin, by a user changing their own password, or by the system when it creates the first admin.
- **Acceptance criteria:**
  1. Each of these writes one audit entry: a change to saved marks; a correction to saved attendance; a created or changed semester result; an account created, activated or deactivated; an edited student profile; a password reset by an admin; a password change by a user.
  2. An entry records who did it, the action, which record, when, and from which IP address.
  3. For data changes, the entry also records the old and new values of the fields that changed.
  4. **For password events the entry records only that the event happened.** No entry ever contains a password, a temporary password, a password hash, or any part of one, in any field.
  5. The entry is saved in the same transaction as the change. If either fails, neither is saved.
  6. Entries cannot be edited or deleted through the application, and the application's own database credential is not permitted to change or delete them.
  7. **Viewer (P1, built 2026-10-17, task `T7-03`):** an admin reads the entries newest first on the Audit log page, filtered by action and by kind of record. Each row names the actor and, where known, the account or roll number it is about. The viewer has no way to change an entry.
- **Edge cases:** Saving a sheet in which nothing changed writes no entries. A bulk save writes one entry per changed row.
- **Depends on:** FR-ADM-001 to FR-ADM-004, FR-AUTH-003.
- **Priority split:** Writing entries is **P0**. A screen for admins to browse and filter entries is **P1**. Until that screen exists, the P0 behaviour is shown by the automated tests and by a read-only database query run by the owner.

The permitted contents and the protections are specified in [08-database-design.md](08-database-design.md) §12.

### FR-ADM-010 CSV bulk import (P1)

- **Acceptance criteria:** Upload a CSV of students. Every row follows the same provisioning rules as FR-ADM-001. The file is validated completely first; any invalid, duplicate or ambiguous row stops the import and all problems are listed by row number.
- **Built on 2026-10-14** (task `T7-07`). Limits chosen while building it: a `.csv` file in UTF-8, at most 1 MB and 100 students, so the request finishes inside Heroku's 30 seconds; the columns are listed in [09-api-contract.md](09-api-contract.md) §8.14. The file is read in memory and never stored. Every first password is the roll number, so the import is for synthetic data only until the owner answers OQ-17.

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

### FR-UX-002 Public landing page (P1)

Added on 2026-10-14 at the owner's request; the owner asked for it to be built last.

- **Acceptance criteria:**
  1. A visitor who opens the site's address sees what Campus Companion does for students and for the college, and how it protects their records, with a way to log in.
  2. It offers no sign-up and says that accounts are created by the college.
  3. A logged-in user who opens the address is sent on to their own dashboard without seeing the page.
  4. It shows no data about any person; the small previews on it are invented and hidden from screen readers.
  5. It links to the privacy policy and the terms, and contains no inline script.

## 12. Out of scope

Faculty portal, fee payment, admissions, hostel, library lending, mobile apps, discussion forums, voice assistant, OCR for scanned papers, AI answers grounded in the whole resource library, summarizing a library PDF (FR-AI-005, dropped on 2026-10-24), and any prediction of exam questions. See [03-scope-and-roadmap.md](03-scope-and-roadmap.md).
