# 06. App Flow

> Status: Draft v0.2 · Owner: Laxman Sirvi · Last updated: 2026-10-11

This document describes how users move through the application and what happens at each step.

## 1. Screens

| Area | Path | Screen |
| --- | --- | --- |
| Public | `/login.html` | Login |
| Shared | `/change-password.html` | Change password |
| Student | `/student/dashboard.html` | Dashboard |
| Student | `/student/profile.html` | Profile |
| Student | `/student/attendance.html` | Attendance |
| Student | `/student/results.html` | Marks and results |
| Student | `/student/timetable.html` | Timetable |
| Student | `/student/notices.html` | Notices |
| Student | `/student/resources.html` | Resource hub |
| Student | `/student/resource.html?id=` | Resource detail and PDF preview |
| Student | `/student/bookmarks.html` | Bookmarks |
| Student | `/student/quiz.html` | Quick Exam: set up, take, result |
| Student | `/student/quiz-history.html` | Attempt history |
| Student | `/student/progress.html` | Progress and recommendations |
| Student | `/student/assistant.html` | AI study assistant |
| Admin | `/admin/dashboard.html` | Admin home |
| Admin | `/admin/students.html` | Students |
| Admin | `/admin/subjects.html` | Subjects and units |
| Admin | `/admin/attendance.html` | Attendance entry |
| Admin | `/admin/marks.html` | Assessments and marks |
| Admin | `/admin/results.html` | Semester results |
| Admin | `/admin/timetable.html` | Timetable |
| Admin | `/admin/notices.html` | Notices |
| Admin | `/admin/resources.html` | Resources |
| Admin | `/admin/questions.html` | Question bank |

## 2. Navigation rules

1. Without a session, every page except `/login.html` redirects to login.
2. Static files under `/student/` are served only to `STUDENT`, and under `/admin/` only to `ADMIN`. The other role is redirected to its own dashboard.
3. An account that must change its password is redirected to `/change-password.html` from every page.
4. The root path `/` redirects to the dashboard for the user's role, or to login.
5. After login, the user goes to their dashboard, not to a page named in a URL parameter. This avoids open redirects.

## 3. Login and logout (student and admin)

The flow is the same for both roles. Students enter their institutional email address; admins enter their administrator email address. Only the destination differs.

```mermaid
flowchart TD
    A["Open any page"] --> B{"Has valid session?"}
    B -- No --> C["Login page"]
    B -- Yes --> H
    C --> D["Enter institutional email and password"]
    D --> V{"Is the text an email address?"}
    V -- No --> V1["Show: Enter a valid email address"]
    V1 --> D
    V -- Yes --> E{"Credentials valid?"}
    E -- No --> F["Show: Incorrect email or password.<br/>Same message for unknown email,<br/>wrong password and deactivated account"]
    F --> D
    E -- "Blocked after 5 failures for this email" --> G["Show: Too many attempts.<br/>Try again in a few minutes"]
    E -- Yes --> H{"Must change password?"}
    H -- Yes --> I["Change password page.<br/>Nothing else is reachable"]
    I --> J
    H -- No --> J{"Role?"}
    J -- STUDENT --> K["Student dashboard"]
    J -- ADMIN --> L["Admin dashboard"]
    K --> M["Log out"]
    L --> M
    M --> N["Session ended on server"]
    N --> C
```

The login form has two labelled fields, "Institutional email" and "Password". It does not accept a roll number on its own, and it never says whether an email address has an account.

## 4. Account provisioning and first-time password

```mermaid
flowchart TD
    A["Admin enters roster details:<br/>roll number, name, branch, semester, section"] --> B{"Valid and not a duplicate?"}
    B -- No --> B1["Rejected with the reason.<br/>Nothing saved"]
    B -- Yes --> C["Account and profile created together.<br/>Email generated from roll number and configured domain.<br/>Temporary password is the roll number, stored only as a hash.<br/>Must change password is set"]
    C --> D["The college tells the student their email address.<br/>The application sends no email"]
    D --> E["Student logs in with the email and the roll number"]
    E --> F["Redirected to Change password.<br/>Every other page and API call is refused"]
    F --> G["Enter current password, new password, new password again"]
    G --> H{"Valid?<br/>8 to 72 characters, both entries match,<br/>not the roll number, not the email,<br/>not the current password"}
    H -- No --> H1["Field error next to the input"]
    H1 --> G
    H -- Yes --> I["Password saved and flag cleared.<br/>Other sessions ended, session identifier renewed.<br/>Audit entry written with no password data"]
    I --> J["Dashboard"]
    J --> K["The roll number no longer works as a password"]
```

There is no self-registration. Admin accounts do not go through this flow: they are provisioned separately and never get a roll-number password.

### Password reset and deactivation

```mermaid
flowchart TD
    A["Student tells an admin they cannot log in"] --> B["Admin confirms who they are, in person"]
    B --> C["Admin presses Reset password"]
    C --> D["Random temporary password shown to the admin once.<br/>Student's sessions ended, account unlocked,<br/>must change password set"]
    D --> E["Admin gives it to the student in person"]
    E --> F["Student logs in and must change it"]

    X["Admin presses Deactivate"] --> Y["Sessions ended at once.<br/>Login fails with the generic message"]
    Y --> Z["Reactivate restores the account<br/>with the password it had"]
```

A reset never restores the roll number as the password.

## 5. Student dashboard navigation

```mermaid
flowchart LR
    D["Dashboard"] --> AT["Attendance"]
    D --> RS["Marks and results"]
    D --> TT["Timetable"]
    D --> NO["Notices"]
    D --> RE["Resource hub"]
    D --> QZ["Quick Exam"]
    D --> PR["Progress"]
    D --> AI["AI assistant"]
    D --> PF["Profile"]
    RE --> RD["Resource detail"]
    RE --> BM["Bookmarks"]
    QZ --> QH["Attempt history"]
    QZ --> AI
    PR --> RD
    PR --> QZ
```

Every student page has the same navigation, so any screen is one step from any other.

## 6. Viewing profile and academic records

```mermaid
flowchart TD
    A["Open Attendance, Results or Profile"] --> B["Page requests the /me endpoint"]
    B --> C{"Response"}
    C -- "200 with data" --> D["Show data"]
    C -- "200, empty" --> E["Empty state, for example:<br/>Results not published yet"]
    C -- "401" --> F["Session expired message, go to login"]
    C -- "5xx or network error" --> G["Error state with Retry button"]
    D --> H["Select a subject"]
    H --> I["Show dated history for that subject"]
```

## 7. Admin updating attendance and marks

```mermaid
flowchart TD
    A["Admin opens Attendance entry"] --> B["Choose subject, section, date, period"]
    B --> C["System lists students of that section<br/>with any saved status"]
    C --> D["Mark present or absent"]
    D --> E["Save"]
    E --> F{"Valid?"}
    F -- "Future date or missing choice" --> G["Show error, nothing saved"]
    G --> D
    F -- Yes --> H["Sheet saved in one transaction,<br/>changes written to audit log"]
    H --> I["Success message"]

    J["Admin opens Marks"] --> K["Choose or create assessment"]
    K --> L["Enter marks or mark absent for each student"]
    L --> M["Save"]
    M --> N{"All rows valid?"}
    N -- No --> O["Highlight invalid rows, nothing saved"]
    O --> L
    N -- Yes --> P["Saved and audited"]
    P --> Q["Publish assessment"]
    Q --> R["Students can now see the marks"]
```

## 8. Uploading and finding learning materials

```mermaid
flowchart TD
    A["Admin: Resources, Upload"] --> B["Choose PDF, enter title, subject, unit, type"]
    B --> C{"File checks"}
    C -- "Over 10 MB" --> D["Error: File is larger than 10 MB"]
    C -- "Not a real PDF" --> E["Error: Only PDF files are accepted"]
    C -- OK --> F{"Stored?"}
    F -- "Storage failure" --> G["Error: Upload failed, please try again.<br/>Nothing saved"]
    F -- Yes --> H["Resource published"]

    S["Student: Resource hub"] --> T["Set filters, type a search"]
    T --> U{"Results?"}
    U -- None --> V["No resources match these filters,<br/>Clear filters"]
    U -- Some --> W["Open a resource"]
    W --> X["Request signed link"]
    X --> Y{"Link issued?"}
    Y -- No --> Z["This file is temporarily unavailable"]
    Y -- Yes --> AA["PDF preview in page"]
    AA --> AB["Bookmark or Download"]
```

## 9. Generating an AI summary

```mermaid
flowchart TD
    A["Open AI assistant, Summarize tab"] --> B["Paste text"]
    B --> C{"Length 200 to 12,000 characters?"}
    C -- No --> D["Inline message with the limit"]
    D --> B
    C -- Yes --> E["Press Summarize"]
    E --> F["Loading state, button disabled"]
    F --> G{"Result"}
    G -- "200" --> H["Show summary, key points, definitions,<br/>AI-generated label, requests remaining"]
    G -- "429 daily limit" --> I["You have used today's AI requests.<br/>Try again tomorrow"]
    G -- "503, 504 or 502" --> J["The assistant is unavailable right now.<br/>Retry button. Limit not consumed"]
    G -- "422 declined" --> K["Show the assistant's reason"]
    H --> L["Copy, or Generate questions from this text"]
```

## 10. Taking a Quick Exam

```mermaid
flowchart TD
    A["Open Quick Exam"] --> B["Choose subject, unit, 5, 10 or 20 questions"]
    B --> C{"Enough questions?"}
    C -- No --> D["Only N questions available.<br/>Offer a test of N"]
    D --> B
    C -- Yes --> E["Attempt created, timer starts on server"]
    E --> F["Answer questions, move back and forth"]
    F --> G{"Submit pressed or timer at zero?"}
    G -- No --> F
    G -- Yes --> H["Submit answers"]
    H --> I{"Accepted?"}
    I -- "Past time limit and grace" --> J["Attempt expired. Start a new test"]
    I -- "Already submitted" --> K["Open the existing result"]
    I -- Yes --> L["Result: score, correct answers, explanations"]
    L --> M["Retake"]
    L --> N["Ask AI to explain a question"]
    L --> O["View progress"]
    M --> B
```

## 11. Common failure handling

| Situation | What the user sees | What happens next |
| --- | --- | --- |
| Session expired | "Your session has expired. Please log in again." | Redirect to login after the message. |
| Access denied (403) | "You do not have permission to do this." | Stay on the page. |
| Record not found (404) | "We could not find that." with a link back to the list. | |
| Missing data | The empty state for that list or card. | No error styling. |
| Validation error | Messages next to the fields; the form keeps its values. | Focus moves to the first invalid field. |
| Failed upload | "Upload failed. Please try again." | The form keeps the metadata. |
| Server error (5xx) | "Something went wrong on our side. Please try again." and a reference code. | Retry button. |
| Offline or network failure | "You appear to be offline." | Retry button. |
| Rate limited (429) | "Too many requests. Please wait a moment." | Controls re-enable after the wait time. |
