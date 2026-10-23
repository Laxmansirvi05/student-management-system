# 10. Authorization Matrix

> Status: Draft v0.2 · Owner: Laxman Sirvi · Last updated: 2026-10-11

This document defines who can read, create, update and delete each kind of data. Every row is a test case (see [17-testing-strategy.md](17-testing-strategy.md)).

## 1. Actors

| Actor | Meaning |
| --- | --- |
| **Visitor** | Not logged in. |
| **Student** | Logged in with role `STUDENT`. |
| **Admin** | Logged in with role `ADMIN`. |
| **System** | The backend itself: scoring, audit logging, scheduled jobs, startup tasks. |

There is no faculty role in the approved scope. Adding one requires updating this document first.

## 2. The essential rule

> A student can reach their own protected records and nobody else's. Changing an ID in a URL, a query parameter or a request body must never reveal or change another student's data.

How this is guaranteed:

1. Student endpoints for personal data end in `/me` and accept **no student identifier**.
2. The service takes the user ID from the server-side session and looks up the student profile from it.
3. Where an ID of an owned object is needed (a quiz attempt), the query includes the owner: "find attempt by ID **and** student ID". If nothing matches, the answer is `404 NOT_FOUND`, the same as for an ID that does not exist.
4. The role is read from the session. A role sent by the browser is ignored.

These checks run in the Spring Boot application, which is the **only** authorization boundary for users. The database sees one runtime role for every request and cannot apply these rules itself ([08-database-design.md](08-database-design.md) §11).

## 3. Legend

| Symbol | Meaning |
| --- | --- |
| ✅ | Allowed |
| 🔒 own | Allowed only for records belonging to the logged-in student |
| 📢 published | Allowed only for records that are published and targeted at the user |
| ❌ | Denied |
| — | Not applicable |

A denied Visitor gets `401`. A denied Student gets `403` for admin functions and `404` for another student's records.

## 4. Matrix

### 4.1 Accounts and profiles

| Data | Action | Visitor | Student | Admin | System |
| --- | --- | --- | --- | --- | --- |
| Login | Authenticate | ✅ | ✅ | ✅ | — |
| Own session | Read, end | ❌ | ✅ | ✅ | — |
| Own password | Change | ❌ | ✅ | ✅ | — |
| Self-registration | Create an account for oneself | ❌ | — | — | — |
| Student account | Provision (account and profile together) | ❌ | ❌ | ✅ | The demo seeder, when the owner enables it |
| Student accounts | Import many from a CSV file (P1), each provisioned by the same rules | ❌ | ❌ | ✅ | — |
| Student account | Deactivate, activate, reset password | ❌ | ❌ | ✅ | — |
| Admin account | Create, change role | ❌ | ❌ | ❌ | Bootstrap only |
| Student profile | Read | ❌ | 🔒 own | ✅ | — |
| Student profile | Update the college's fields (name, branch, semester, section, admission year) | ❌ | ❌ | ✅ | — |
| Own personal details (phone, text about themselves) | Update | ❌ | 🔒 own | ❌ | — |
| Own profile photo | Add, replace, remove, get a signed link | ❌ | 🔒 own | ❌ | Issues the link |
| Student list and search | Read | ❌ | ❌ | ✅ | — |
| Password hashes | Read through any API | ❌ | ❌ | ❌ | Internal comparison only |
| Login email, roll number | Change after the account exists | ❌ | ❌ | ❌ | ❌ |
| Role of any account | Change | ❌ | ❌ | ❌ | ❌ |
| Anything except changing the password, while a password change is required | Use | — | ❌ | ❌ | — |

In the first release, further admin accounts are created only by the owner directly in the database. There is no API for it. An admin account has its own separately provisioned credentials and never receives the roll-number temporary password.

**Email login changes the identifier, not the permissions.** Logging in with an institutional email address instead of a roll number alters nothing in this matrix. The session still carries only the internal account ID and the role, the student is still derived from that account ID, and no request can set or change a role. While an account's password is temporary, the account has **fewer** permissions than its role normally has: only the four requests listed in [09-api-contract.md](09-api-contract.md) §2 are accepted.

### 4.2 Academic records

| Data | Action | Visitor | Student | Admin | System |
| --- | --- | --- | --- | --- | --- |
| Attendance | Read | ❌ | 🔒 own | ✅ | — |
| Attendance | Create, correct | ❌ | ❌ | ✅ | — |
| Attendance reports (college totals for a period) | Read | ❌ | 🔒 own | ✅ by import | — |
| Attendance reports | Import, correct by importing again | ❌ | ❌ | ✅ | — |
| Assessments | Read | ❌ | 📢 published | ✅ | — |
| Assessments | Create, update, publish | ❌ | ❌ | ✅ | — |
| Marks | Read | ❌ | 🔒 own, and only for published assessments | ✅ | — |
| Marks | Create, update | ❌ | ❌ | ✅ | — |
| Semester results | Read | ❌ | 🔒 own, published only | ✅ | — |
| Semester results | Create, update | ❌ | ❌ | ✅ | — |
| CGPA | Read | ❌ | 🔒 own | ✅ | Calculated |
| Subjects and units | Read | ❌ | ✅ own branch | ✅ | — |
| Subjects and units | Create, update, deactivate | ❌ | ❌ | ✅ | — |
| Timetable | Read | ❌ | 🔒 own branch, semester, section | ✅ | — |
| Timetable | Create, update, delete | ❌ | ❌ | ✅ | — |

**A student can never create, update or delete any official academic record.** There is no student endpoint that writes to these tables.

### 4.3 Notices

| Data | Action | Visitor | Student | Admin | System |
| --- | --- | --- | --- | --- | --- |
| Notices | Read | ❌ | 📢 published, for all or for own branch and semester | ✅ including drafts | — |
| Notices | Create, update, publish, unpublish, delete | ❌ | ❌ | ✅ | — |
| Notice official document | Get a signed link | ❌ | 📢 only for a notice they may read | ✅ including drafts | Issues the link |
| Notice official document | Attach, replace, remove | ❌ | ❌ | ✅ | Stores and removes the file |

### 4.3b Complaints

| Data | Action | Visitor | Student | Admin | System |
| --- | --- | --- | --- | --- | --- |
| Complaint | Raise, withdraw own open one | ❌ | ✅ own | ❌ | — |
| Public complaint | Read, support | ❌ | ✅ unless rejected | ✅ | — |
| Private complaint | Read | ❌ | 👤 own only | ✅ with the student | — |
| Secure complaint | Read | ❌ | 👤 own only | ✅ **without** the student | — |
| Complaint | Set status and response | ❌ | ❌ | ✅ | Audited |

### 4.3c Notes and the Quick Assistant

| Data | Action | Visitor | Student | Admin | System |
| --- | --- | --- | --- | --- | --- |
| Note, folder, link, attachment | Create, read, change, delete | ❌ | 👤 own only | ❌ | — |
| Note shared with a classmate | Read (view) or read and edit text (edit) | ❌ | ✅ named recipient | ❌ | — |
| Note shared by link | Read | ❌ | ✅ any signed-in student with the link, read-only | ❌ | — |
| Attachment file | Open through a signed link | ❌ | 👤 owner; a named recipient; or a student who presents the note's share link (`share=<slug>`). Not while the note is in the Trash, except the owner | ❌ | — |
| Assistant chat | Send, read, delete | ❌ | 👤 own only | ❌ (never read) | Usage logged as numbers only |

### 4.4 Learning resources

| Data | Action | Visitor | Student | Admin | System |
| --- | --- | --- | --- | --- | --- |
| Resource metadata | Read | ❌ | 📢 published | ✅ including hidden | — |
| Resource subject folders | Read (counts of published resources) | ❌ | ✅ | ✅ | — |
| Resource file | Get a signed link | ❌ | 📢 published | ✅ | Issues the link |
| Resource file | Direct read from storage without a signed link | ❌ | ❌ | ❌ | ✅ with service key |
| Resource | Upload, update, hide, delete | ❌ | ❌ | ✅ | — |
| Bookmarks | Read, add, remove | ❌ | 🔒 own | ❌ | — |
| Previous-year papers (FR-LIB-006) | Browse, search, get a signed link | ❌ | 📢 published | ✅ via the admin list | Issues the link |
| Previous-year papers | Save and unsave | ❌ | 🔒 own | ❌ | — |
| Previous-year papers | Add, import, edit, hide, delete | ❌ | ❌ | ✅ | — |
| Resource reports (P1) | Create | ❌ | ✅ | — | — |
| Resource reports (P1) | Read, resolve | ❌ | ❌ | ✅ | — |

Students may read resources of any subject, not only their branch. Study material is not personal data.

### 4.5 Quick Exam and progress

| Data | Action | Visitor | Student | Admin | System |
| --- | --- | --- | --- | --- | --- |
| Question bank | Read with correct answers | ❌ | ❌ | ✅ | ✅ for scoring |
| Question bank | Create, update, publish, retire | ❌ | ❌ | ✅ | — |
| Questions in a running attempt | Read without answers | ❌ | 🔒 own attempt | ❌ | — |
| Quiz attempt | Start, submit | ❌ | 🔒 own | ❌ | — |
| Quiz result | Read (answers revealed after submission) | ❌ | 🔒 own | ❌ | — |
| Quiz score | Set or change | ❌ | ❌ | ❌ | ✅ |
| Progress and recommendations | Read | ❌ | 🔒 own | ❌ | Calculated |

Admins do not see individual students' practice attempts in the first release. Practice is private. Aggregate statistics are P2.

### 4.6 AI assistant

| Data | Action | Visitor | Student | Admin | System |
| --- | --- | --- | --- | --- | --- |
| AI features | Use | ❌ | ✅ within limits | ❌ | — |
| Own AI usage count | Read | ❌ | 🔒 own | — | — |
| AI request metadata log | Read | ❌ | ❌ | ❌ | ✅ |
| AI provider key | Read | ❌ | ❌ | ❌ | ✅ |

The AI component can read no table except its own usage log. It cannot look up marks, attendance or profiles.

### 4.7 Planner (P1)

| Data | Action | Visitor | Student | Admin | System |
| --- | --- | --- | --- | --- | --- |
| Study tasks | Read, create, update, delete | ❌ | 🔒 own | ❌ | — |

### 4.8 Audit and operations

| Data | Action | Visitor | Student | Admin | System |
| --- | --- | --- | --- | --- | --- |
| Audit log | Create | ❌ | ❌ | ❌ | ✅ |
| Audit log | Read (P1 screen, built 2026-10-17) | ❌ | ❌ | ✅ | — |
| Audit log | Update, delete | ❌ | ❌ | ❌ | ❌ |
| Health endpoint | Read status only | ✅ | ✅ | ✅ | — |
| Other actuator endpoints | Any | ❌ | ❌ | ❌ | — |

"Update, delete" is denied to the System as well. The runtime database role has no such privilege on `audit_logs`, and triggers reject those statements for every role ([08-database-design.md](08-database-design.md) §12). No audit row contains password material.

### 4.9 Static pages

| Path | Visitor | Student | Admin |
| --- | --- | --- | --- |
| `/login.html`, `/css/**`, `/js/**`, `/img/**` | ✅ | ✅ | ✅ |
| `/` and `/index.html` (landing page) | ✅ | ✅ sent on to the student dashboard | ✅ sent on to the admin dashboard |
| `/privacy.html`, `/terms.html` | ✅ | ✅ | ✅ |
| `/student/**` | ❌ redirect to login | ✅ | ❌ redirect to admin dashboard |
| `/admin/**` | ❌ redirect to login | ❌ redirect to student dashboard | ✅ |
| `/change-password.html` | ❌ | ✅ | ✅ |

Protecting pages is a convenience. The API rules above are what protect the data.

## 5. Where each rule is enforced

| Layer | What it enforces |
| --- | --- |
| **Spring Security URL rules** | Visitor versus logged-in; `/api/v1/admin/**` and `/admin/**` need `ADMIN`; `/student/**` and student-only API groups need `STUDENT`; the must-change-password restriction; CSRF. |
| **Service layer** | Ownership (🔒 own): the student is derived from the session and included in every query. Visibility (📢 published): status and targeting filters are part of the query, not applied afterwards in the browser. |
| **Method security** | `@PreAuthorize` on admin service methods as a second check, so a mis-mapped URL cannot expose them. |
| **Database** | Foreign keys and constraints keep data valid. The runtime role `cc_app` has only the minimum row privileges and cannot change the schema or the audit log. The Data API is off, the schema is not exposed, and Row Level Security admits only `cc_app`. **None of this decides which student may see which row:** the backend connects as one role for every user, so ownership stays in the service layer. See [08-database-design.md](08-database-design.md) §11. |
| **Storage** | The bucket is private. No public URLs exist. The only access path is a signed link issued after the checks above, valid for at most five minutes. |
| **Frontend** | Nothing. It hides controls the user cannot use, for clarity only. |

## 6. Rules for changing this matrix

1. A new endpoint is added here before it is implemented.
2. Any change that widens access needs the owner's approval.
3. Each new row gets a test for the denied actors as well as the allowed one.
