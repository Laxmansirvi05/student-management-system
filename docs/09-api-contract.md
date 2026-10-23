# 09. API Contract

> Status: Draft v0.2 · Owner: Laxman Sirvi · Last updated: 2026-10-11

This document explains the REST API conventions and gives examples. The machine-readable contract is [openapi.yaml](openapi.yaml), which is authoritative for the paths, fields and status codes of the endpoints it lists. It covers every P0 endpoint in section 7. Endpoints marked P1 in section 7 are added to it by the task that implements them. The health endpoint in section 7.7 is outside `/api/v1` and is not in the file. If the two files disagree, fix both in the same change.

## 1. Conventions

| Topic | Rule |
| --- | --- |
| Base path | `/api/v1` |
| Versioning | The major version is in the path. Additive changes (new fields, new endpoints) stay in `v1`. A breaking change needs `v2` and a decision record. |
| Format | JSON, UTF-8, `camelCase` fields. Requests with a body send `Content-Type: application/json`, except file upload (`multipart/form-data`). |
| Dates | `YYYY-MM-DD`. Timestamps are ISO 8601 in UTC, for example `2026-10-10T08:30:00Z`. Times of day are `HH:mm`. |
| Numbers | Marks, percentages and grade points are JSON numbers with at most two decimals. |
| IDs | Integers. An ID is never proof of permission. |
| Enums | Upper-case strings, for example `PRESENT`. |
| Nulls | Optional fields that have no value are returned as `null`, not omitted. |
| Resource names | Plural nouns. `/me` means "the logged-in user's own". |

## 2. Authentication and CSRF

- Authentication is a session cookie (`JSESSIONID`: `HttpOnly`, `Secure`, `SameSite=Lax`). There are no bearer tokens.
- Every `POST`, `PUT`, `PATCH` and `DELETE` must send the header `X-XSRF-TOKEN` with the value of the `XSRF-TOKEN` cookie.
- The frontend gets the token by calling `GET /api/v1/auth/csrf` once when a page loads.
- A missing or wrong token gives `403` with code `CSRF_INVALID`.
- The login identifier is the account's institutional email address. The session holds only the internal account ID and the role.

### While a password change is required

When the logged-in account has a temporary password (`mustChangePassword` is `true`), the server accepts only these four requests:

| Method | Path |
| --- | --- |
| GET | `/auth/csrf` |
| GET | `/auth/me` |
| POST | `/auth/change-password` |
| POST | `/auth/logout` |

Every other request under `/api/v1` returns `403` with code `AUTH_PASSWORD_CHANGE_REQUIRED`, whatever the role. Protected pages redirect to the change-password page. The restriction is enforced by a server-side filter and cannot be skipped by calling the API directly.

## 3. Error format

Errors use RFC 9457 problem details with media type `application/problem+json`.

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/admin/students",
  "code": "VALIDATION_FAILED",
  "traceId": "8f3c2a1d9b7e",
  "errors": [
    { "field": "rollNo", "message": "must not be blank" },
    { "field": "currentSemester", "message": "must be between 1 and 8" }
  ]
}
```

- `code` is stable and is what the frontend switches on.
- `detail` is safe to show to the user.
- `errors` is present only for validation failures.
- `traceId` matches the server log line.
- Responses never contain stack traces, SQL, class names or file paths.

## 4. Status and error codes

| HTTP | `code` | When |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED` | A field is missing, malformed or out of range. |
| 401 | `AUTH_REQUIRED` | No valid session. |
| 401 | `AUTH_INVALID_CREDENTIALS` | Login failed. The same response for an email with no account, a wrong password and a deactivated account. |
| 403 | `ACCESS_DENIED` | Logged in, but the role is not allowed. |
| 403 | `AUTH_PASSWORD_CHANGE_REQUIRED` | The account must change its password first. |
| 403 | `CSRF_INVALID` | CSRF token missing or wrong. |
| 404 | `NOT_FOUND` | The record does not exist, **or belongs to someone else**. |
| 409 | `CONFLICT` | Duplicate or state conflict, for example a duplicate roll number. |
| 409 | `QUIZ_NOT_ENOUGH_QUESTIONS` | Fewer published questions than requested. Includes `available`. |
| 409 | `QUIZ_ALREADY_SUBMITTED` | The attempt was already submitted. |
| 409 | `QUIZ_ATTEMPT_EXPIRED` | Submitted after the time limit and grace period. |
| 413 | `UPLOAD_TOO_LARGE` | File over 10 MB, or a profile photo over 2 MB. |
| 413 | `REQUEST_TOO_LARGE` | A JSON request body over 64 KB (section 6). |
| 415 | `UPLOAD_INVALID_TYPE` | Not a valid PDF. |
| 422 | `AI_REQUEST_DECLINED` | The assistant declined the request (off-topic or unsafe). `detail` gives the reason. |
| 423 | `AUTH_ACCOUNT_LOCKED` | Too many failed logins for that email address. Returned in the same way whether or not an account exists. |
| 429 | `RATE_LIMITED` | Too many requests. Includes a `Retry-After` header. |
| 429 | `AI_QUOTA_EXCEEDED` | Daily AI limit reached. |
| 500 | `INTERNAL_ERROR` | Unexpected failure. |
| 502 | `AI_INVALID_RESPONSE` | The AI provider returned output that failed validation. |
| 503 | `AI_UNAVAILABLE` | AI provider error, AI switched off, or the application-wide daily ceiling reached. |
| 503 | `STORAGE_UNAVAILABLE` | File storage could not be reached. |
| 504 | `AI_TIMEOUT` | The AI provider did not answer within the time budget. |

Success codes: `200` for reads and updates that return a body, `201` for creation (with a `Location` header), `204` for success with no body.

## 5. Pagination, sorting and filtering

List endpoints accept:

| Parameter | Default | Rule |
| --- | --- | --- |
| `page` | `0` | Zero-based. |
| `size` | `20` | 1 to 100. |
| `sort` | endpoint-specific | `field,asc` or `field,desc`. Only fields listed for that endpoint; anything else gives 400. |

Response shape:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 57,
  "totalPages": 3
}
```

Filters are query parameters named after the field (`subjectId`, `type`, `examYear`). Free-text search uses `q` (2 to 100 characters).

## 6. Limits

| Limit | Value |
| --- | --- |
| JSON request body | 64 KB. A larger body is refused with `413 REQUEST_TOO_LARGE` before it is read; a body sent without a length is cut off at the limit |
| File upload | 10 MB, PDF only |
| Student CSV import | 1 MB and 100 students per file. Each new account needs a password hash of about 0.1 s, and a request must finish within Heroku's 30 seconds |
| Login attempts | 5 consecutive failures for an email address block it for 15 minutes, whether or not an account exists; 20 attempts per IP address per 10 minutes |
| AI requests | Per-user, per-minute and application-wide limits are defined once, in [12-AI-features-spec.md](12-AI-features-spec.md) §2 |
| Other API calls | 120 per session per minute. Over the limit: `429 RATE_LIMITED` with `Retry-After`. A request without a session is refused as `401` before it is counted |
| Search text `q` | 100 characters |

AI input limits are in section 9.

## 7. Endpoint summary

Access: **Public**, **Any** (any logged-in user), **Student**, **Admin**. Detailed permissions are in [10-authorization-matrix.md](10-authorization-matrix.md).

### 7.1 Authentication

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| GET | `/auth/csrf` | Public | Get a CSRF token. |
| POST | `/auth/login` | Public | Log in. |
| POST | `/auth/logout` | Any | Log out. |
| GET | `/auth/me` | Any | Current user. |
| POST | `/auth/change-password` | Any | Change own password. |

### 7.2 Student academic data

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| GET | `/students/me` | Student | Own profile. |
| PUT | `/students/me` | Student | Change own phone number, the text about themselves, personal email, LinkedIn, GitHub and portfolio links, and skills. Every field is optional and a blank one is cleared. Links must be https; LinkedIn and GitHub links must be on those sites; an invalid one is a `400` naming the field. No other field is accepted. |
| PUT | `/students/me/photo` | Student | Add or replace own photo: multipart part `file`, JPEG or PNG, at most 2 MB. The server stores a redrawn 512 by 512 JPEG. |
| DELETE | `/students/me/photo` | Student | Remove own photo. Idempotent. |
| GET | `/students/me/photo-url` | Student | Signed link to own photo, valid five minutes. `404` when there is none. |
| GET | `/students/me/dashboard` | Student | Dashboard summary. |
| GET | `/attendance/me` | Student | Subject-wise attendance. |
| GET | `/attendance/me/subjects/{subjectId}` | Student | Dated history for one subject (paginated). |
| GET | `/attendance/me/calculator` | Student | Classes needed for a target (P1). |
| GET | `/marks/me` | Student | Published assessment marks. Filters: `subjectId`, `semester`. |
| GET | `/results/me` | Student | Semester results and CGPA. |
| GET | `/timetable/me` | Student | Weekly timetable. Filter: `day`. |
| GET | `/notices` | Any | Published notices (paginated). Filter: `category`. |
| GET | `/notices/{id}` | Any | One published notice. `documentType` is `application/pdf`, `image/jpeg` or null. |
| GET | `/notices/{id}/document` | Any | Five-minute signed link to the notice's official document: `{url, expiresAt, contentType}`. `download=true` saves it as `Notice - <title>.pdf/.jpg`. 404 when the notice is not visible to the user or has no document. |
| GET | `/subjects` | Any | Subjects (a student sees their branch). Filter: `semester`. |
| GET | `/subjects/{id}/units` | Any | Units of a subject. |

### 7.3 Learning resources

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| GET | `/resources` | Any | Search published resources. Filters: `subjectId`, `unitId`, `semester`, `type`, `q`. Sort: `createdAt`, `title`. |
| GET | `/resources/subjects` | Any | Subject folders: `[{subjectId, subjectCode, subjectName, semester, resources}]` for subjects with published resources, newest semester first, then by name. |
| GET | `/resources/{id}` | Any | Resource details. |
| GET | `/resources/{id}/download-url` | Any | Short-lived signed link. |
| GET | `/question-papers` | Any | Search papers. Filters: `subjectId`, `examYear`, `examType`, `q`. |
| GET | `/bookmarks/me` | Student | Own bookmarks (paginated). |
| PUT | `/bookmarks/{resourceId}` | Student | Add a bookmark (idempotent). |
| DELETE | `/bookmarks/{resourceId}` | Student | Remove a bookmark (idempotent). |

### 7.4 Quick Exam and progress

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| POST | `/quiz-attempts` | Student | Start a test. |
| POST | `/quiz-attempts/{id}/submit` | Student | Submit answers. |
| GET | `/quiz-attempts/{id}` | Student | Result of own attempt. |
| GET | `/quiz-attempts/me` | Student | Own history (paginated). |
| GET | `/progress/me` | Student | Unit performance and recommendations. |

### 7.5 AI study assistant

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| POST | `/ai/explain` | Student | Explain a topic. |
| POST | `/ai/summarize` | Student | Summarize pasted text. |
| POST | `/ai/generate-quiz` | Student | Generate practice questions. |
| GET | `/ai/usage/me` | Student | Requests used and remaining today. |
| POST | `/ai/summarize-resource` | Student | Summarize a library PDF (P1). |
| POST | `/ai/flowchart` | Student | Generate a flowchart (P1). |

### 7.6 Administration

All paths start with `/admin` and require the `ADMIN` role.

| Method | Path | Purpose |
| --- | --- | --- |
| GET, POST | `/admin/students` | Search (filters `branch`, `semester`, `section`; free text `q` on roll number, name or email) and provision students. |
| GET, PUT | `/admin/students/{id}` | Read and update a student. |
| POST | `/admin/students/{id}/deactivate`, `/admin/students/{id}/activate` | Change account status. Deactivating ends the student's sessions. |
| POST | `/admin/students/{id}/reset-password` | Issue a random temporary password (returned once) and end the student's sessions. |
| GET, POST | `/admin/subjects` | List and create subjects. |
| PUT | `/admin/subjects/{id}` | Update or deactivate a subject. |
| POST | `/admin/subjects/{id}/units` | Add a unit. |
| PUT, DELETE | `/admin/units/{id}` | Update or remove a unit that is not in use. |
| GET | `/admin/attendance` | Attendance sheet for `subjectId`, `section`, `classDate`, `periodNo`. |
| PUT | `/admin/attendance` | Save a whole attendance sheet (create or correct). |
| GET, POST | `/admin/assessments` | List and create assessments. |
| PUT | `/admin/assessments/{id}` | Update an assessment, including `published`. |
| GET, PUT | `/admin/assessments/{id}/marks` | Read and save the marks sheet. |
| GET, PUT | `/admin/semester-results` | Read every result of one student (`studentId`), published or not; create or update one result. |
| GET, POST | `/admin/timetable` | List and create timetable entries. |
| PUT, DELETE | `/admin/timetable/{id}` | Update or delete an entry. |
| GET, POST | `/admin/notices` | List all notices (including drafts) and create. |
| PUT, DELETE | `/admin/notices/{id}` | Update or delete a notice. |
| POST | `/admin/notices/{id}/publish`, `/admin/notices/{id}/unpublish` | Change status. |
| POST, DELETE, GET | `/admin/notices/{id}/document` | Attach or replace the official document (multipart part `file`: PDF, JPEG or PNG, at most 10 MB; 413 too large, 415 not a valid file, 503 storage unavailable), remove it (404 when there is none), or get a signed link (drafts included). POST and DELETE return the notice. |
| GET, POST | `/admin/resources` | List all resources and upload (multipart). |
| PUT, DELETE | `/admin/resources/{id}` | Update details or status; delete. |
| GET, POST | `/admin/quiz-questions` | List and create questions. |
| PUT | `/admin/quiz-questions/{id}` | Update a question, including `status`. |
| GET | `/pyqs/overview`, `/pyqs/subjects`, `/pyqs`, `/pyqs/saved` | Previous-year papers (P1). Student only. See 8.17. |
| GET | `/pyqs/{id}/link` | A five-minute link to open or download a paper. |
| PUT, DELETE | `/pyqs/{id}/save` | Save or unsave a paper. |
| GET, POST | `/admin/pyqs` | List all papers and add one (multipart). See 8.18. |
| GET | `/admin/pyqs/{id}/link` | A five-minute link to any paper, hidden ones too. |
| PUT, DELETE | `/admin/pyqs/{id}` | Edit details or visibility; delete. |
| POST, GET | `/complaints` | Raise a complaint; list the public board. Student only. See 8.19. |
| GET | `/complaints/mine`, `/complaints/{id}` | Own complaints; one complaint. |
| DELETE | `/complaints/{id}` | Withdraw own open complaint. |
| PUT, DELETE | `/complaints/{id}/support` | Support or unsupport a public complaint. |
| GET, POST | `/notes` | List notes of a view; create a note. Student only. See 8.21. |
| GET, PATCH, DELETE | `/notes/{id}` | Read, change (autosave, pin, archive, move, restore), trash or destroy a note. |
| POST | `/notes/{id}/duplicate`, `/notes/bulk` | Copy a note; one action on many notes. |
| GET, POST, DELETE | `/notes/{id}/links`, `/notes/{id}/links/{linkId}`; GET `/notes/link-targets` | Links from a note, and the picker's search. |
| GET, PATCH, POST, DELETE | `/notes/folders`, `/notes/folders/{id}` | Folders. |
| GET, POST, DELETE | `/notes/attachments`, `/notes/attachments/{id}`; GET `/notes/attachments/{id}/file` | Pictures and files; the file link redirects to a signed link. |
| GET, PUT, DELETE, POST | `/notes/{id}/share`, `/notes/{id}/share/recipients[/{recipientId}]`; GET `/notes/shared/{slug}` | Sharing with classmates and by link. |
| POST, GET, DELETE | `/assistant/chat`, `/assistant/conversations[/{id}[/messages]]` | The robot's Quick Assistant. Student only. See 9.5. |
| GET | `/admin/complaints`, `/admin/complaints/{id}` | List and read complaints. See 8.20. |
| PUT | `/admin/complaints/{id}` | Set status and response. |
| GET | `/admin/audit-logs` | Read the audit log, newest first (P1). See 8.15. |
| GET | `/admin/audit-logs/actions` | The actions that can appear, for the filter. |
| POST | `/admin/imports/students` | Create many students from one CSV file (P1). See 8.14. |
| POST | `/admin/imports/attendance-reports` | The college's attendance totals for a period, from a CSV file. See 8.16. |

### 7.7 Health

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| GET | `/actuator/health` | Public | Returns `{"status":"UP"}` or `DOWN`. No details are exposed. Not part of `openapi.yaml`, because it is outside `/api/v1`. |

## 8. Examples

### 8.1 Log in

`POST /api/v1/auth/login`

```json
{ "email": "23cse001@university.example", "password": "<password>" }
```

| Field | Rule |
| --- | --- |
| `email` | Required. A syntactically valid email address of at most 254 characters. The server trims it and converts it to lower case before looking it up. |
| `password` | Required, 1 to 72 characters. Never trimmed or altered. |

`200 OK`

```json
{
  "email": "23cse001@university.example",
  "displayName": "Demo Student One",
  "role": "STUDENT",
  "mustChangePassword": true
}
```

The response sets a new session cookie. When `mustChangePassword` is `true`, the frontend opens the change-password page and the server enforces the restriction described in section 2.

| Case | Response |
| --- | --- |
| `email` missing, or not an email address | `400 VALIDATION_FAILED` naming the field. Decided from the text alone, before any lookup |
| Email with no account, wrong password, or deactivated account | `401 AUTH_INVALID_CREDENTIALS`, identical in status, body and headers apart from `traceId` |
| Further attempts for an email after five consecutive failures | `423 AUTH_ACCOUNT_LOCKED`, identical whether or not an account exists |
| Too many attempts from one IP address | `429 RATE_LIMITED` |
| No CSRF token | `403 CSRF_INVALID` |

No response to this request contains a password or a hash, and no failure response differs according to whether an account exists.

### 8.2 Change password

`POST /api/v1/auth/change-password`

```json
{ "currentPassword": "<current>", "newPassword": "<new>", "confirmNewPassword": "<new>" }
```

`204 No Content`. The response replaces the session cookie with a new session identifier. The user's other sessions are ended, and `mustChangePassword` becomes `false`.

Validation failures return `400 VALIDATION_FAILED` naming the field:

| Field | Rule |
| --- | --- |
| `currentPassword` | Must be the account's current password. A wrong value counts toward lockout. |
| `newPassword` | 8 to 72 characters. |
| `newPassword` | Ignoring letter case, not the user's roll number, not the user's email address, not the part of the email before the `@`, and not the current password. |
| `confirmNewPassword` | Equal to `newPassword`. |

After a successful change, logging in with the old password returns `401 AUTH_INVALID_CREDENTIALS`.

### 8.3 Own attendance

`GET /api/v1/attendance/me`

```json
{
  "requiredPercent": 75,
  "overallPercent": 81.25,
  "subjects": [
    {
      "subjectId": 12,
      "subjectCode": "CS301",
      "subjectName": "Database Management Systems",
      "classesHeld": 40,
      "classesAttended": 29,
      "percent": 72.5,
      "belowRequired": true
    },
    {
      "subjectId": 13,
      "subjectCode": "CS302",
      "subjectName": "Operating Systems",
      "classesHeld": 40,
      "classesAttended": 36,
      "percent": 90,
      "belowRequired": false
    }
  ],
  "report": {
    "fromDate": "2026-07-27",
    "toDate": "2026-09-30",
    "classesHeld": 211,
    "classesAttended": 163,
    "percent": 77.25,
    "belowRequired": false
  }
}
```

A subject with no classes has `classesHeld: 0` and `percent: null`. `report` is the college's latest attendance report for the student (the one whose period ends last), or `null`. When no class has been recorded subject by subject, `overallPercent` is the report's percentage.

### 8.4 Own results

`GET /api/v1/results/me`

```json
{
  "cgpa": 7.12,
  "semesters": [
    {
      "semester": 1, "sgpa": 7.8, "creditsRegistered": 22, "creditsEarned": 22, "resultStatus": "PASS",
      "subjects": [
        { "subjectCode": "CS101", "subjectName": "Programming", "credits": 3, "grade": "B", "gradePoint": 6,
          "exam": "I Year I Sem Supplementary, July 2026", "attempts": 2, "firstGrade": "F", "passed": true }
      ]
    },
    { "semester": 2, "sgpa": 6.5, "creditsRegistered": 24, "creditsEarned": 20, "resultStatus": "FAIL", "subjects": [] }
  ]
}
```

`subjects` lists each subject of the semester with its current grade (the latest attempt) and its `gradePoint` on the college scale (O 10, A+ 9, A 8, B+ 7, B 6, C 5, F and Ab 0; `null` for S or U). `cgpaToDate` is the CGPA over that semester and the ones before it, as printed on the memorandum of grades, or `null` while any of them has no SGPA. `firstGrade` is the first attempt's grade when there was more than one attempt, otherwise `null`; `credits` is `null` for a course without credits. `sgpa` is `null` for a semester the college published without an SGPA (a subject is failed); `cgpa` is then `null` as well.

`cgpa` is weighted by `creditsRegistered`, not by `creditsEarned`: (7.8 × 22 + 6.5 × 24) ÷ 46 = 7.12. The rule is in [08-database-design.md](08-database-design.md) §4.8. `cgpa` is `null` and `semesters` is empty when nothing is published.

### 8.5 Search resources

`GET /api/v1/resources?subjectId=12&type=NOTES&q=normal&page=0&size=20&sort=createdAt,desc`

```json
{
  "content": [
    {
      "id": 204,
      "title": "Unit 3: Normalization notes",
      "description": "1NF to BCNF with worked examples.",
      "subjectId": 12,
      "subjectCode": "CS301",
      "subjectName": "Database Management Systems",
      "unitId": 31,
      "unitNo": 3,
      "unitTitle": "Normalization",
      "type": "NOTES",
      "fileSizeBytes": 1482291,
      "createdAt": "2026-09-14T06:10:00Z",
      "bookmarked": true,
      "questionPaper": null
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

For a question paper, `questionPaper` is `{ "examYear": 2025, "examType": "SEMESTER", "maxMarks": 70 }`.

### 8.6 Signed download link

`GET /api/v1/resources/204/download-url`

```json
{ "url": "https://<project>.supabase.co/storage/v1/object/sign/...", "expiresAt": "2026-10-10T08:35:00Z" }
```

The link shows the file in the page. With `?download=true` the link makes the browser save the file under the name it was uploaded with. Both kinds last five minutes.

Failures: `404 NOT_FOUND` (missing or hidden), `503 STORAGE_UNAVAILABLE`.

### 8.7 Start a test

`POST /api/v1/quiz-attempts`

```json
{ "subjectId": 12, "unitId": 31, "questionCount": 10 }
```

Validation: `questionCount` is 5, 10 or 20.

`201 Created`

```json
{
  "attemptId": 501,
  "startedAt": "2026-10-10T08:30:00Z",
  "expiresAt": "2026-10-10T08:40:00Z",
  "durationSeconds": 600,
  "questions": [
    {
      "questionId": 9001,
      "position": 1,
      "text": "Which normal form removes transitive dependencies?",
      "options": [
        { "key": "C", "text": "Third normal form" },
        { "key": "A", "text": "First normal form" },
        { "key": "D", "text": "Boyce-Codd normal form" },
        { "key": "B", "text": "Second normal form" }
      ]
    }
  ]
}
```

The response never contains the correct option or the explanation. The options are in random order; `key` is the option's own letter and is what the browser sends back.

`expiresAt` is `startedAt` plus 60 seconds for each question. The server keeps this limit; nothing the browser sends can move it.

`409 QUIZ_NOT_ENOUGH_QUESTIONS` adds `"available": 7` to the problem detail.

### 8.8 Submit a test

`POST /api/v1/quiz-attempts/501/submit`

```json
{
  "answers": [
    { "questionId": 9001, "selectedOption": "C" },
    { "questionId": 9002, "selectedOption": null }
  ]
}
```

Questions left out or sent as `null` count as wrong. A `questionId` that is not part of the attempt gives `400`, and so does a second answer for the same question. The request has no field for a score.

An attempt is submitted once: a second submission gives `409 QUIZ_ALREADY_SUBMITTED`. A submission that arrives more than 15 seconds after `expiresAt` gives `409 QUIZ_ATTEMPT_EXPIRED` and closes the attempt as `EXPIRED` with a score of 0.

`200 OK`

```json
{
  "attemptId": 501,
  "status": "SUBMITTED",
  "score": 7,
  "questionCount": 10,
  "percent": 70,
  "submittedAt": "2026-10-10T08:37:12Z",
  "items": [
    {
      "questionId": 9001,
      "position": 1,
      "text": "Which normal form removes transitive dependencies?",
      "options": [
        { "key": "A", "text": "First normal form" },
        { "key": "B", "text": "Second normal form" },
        { "key": "C", "text": "Third normal form" },
        { "key": "D", "text": "Boyce-Codd normal form" }
      ],
      "selectedOption": "C",
      "correctOption": "C",
      "correct": true,
      "explanation": "3NF requires that no non-key attribute depends transitively on a key."
    }
  ]
}
```

`GET /api/v1/quiz-attempts/501` returns the same body. Another student's attempt ID gives `404`.

### 8.9 Save an attendance sheet (admin)

`PUT /api/v1/admin/attendance`

```json
{
  "subjectId": 12,
  "section": "A",
  "classDate": "2026-10-09",
  "periodNo": 2,
  "entries": [
    { "studentId": 1001, "status": "PRESENT" },
    { "studentId": 1002, "status": "ABSENT" }
  ]
}
```

`200 OK` with `{ "saved": 2, "changed": 1 }`. The whole sheet is saved in one transaction. A future `classDate`, or a student who is not in that section, gives `400` and nothing is saved.

### 8.10 Save a marks sheet (admin)

`PUT /api/v1/admin/assessments/77/marks`

```json
{
  "entries": [
    { "studentId": 1001, "marksObtained": 18.5, "absent": false },
    { "studentId": 1002, "marksObtained": null, "absent": true }
  ]
}
```

`200 OK` with `{ "saved": 2, "changed": 2 }`. If any row is invalid, the response is `400` with one error per row (`entries[1].marksObtained`) and nothing is saved.

### 8.11 Upload a resource (admin)

`POST /api/v1/admin/resources` as `multipart/form-data` with two parts:

- `file`: the PDF.
- `metadata`: JSON.

```json
{
  "title": "CS301 Semester Exam 2025",
  "description": "Regular examination paper.",
  "subjectId": 12,
  "unitId": null,
  "type": "QUESTION_PAPER",
  "questionPaper": { "examYear": 2025, "examType": "SEMESTER", "maxMarks": 70 }
}
```

`201 Created` with the resource body from 8.5. `questionPaper` is required when `type` is `QUESTION_PAPER` or `MODEL_PAPER`, and must be `null` otherwise.

Failures: `413 UPLOAD_TOO_LARGE`, `415 UPLOAD_INVALID_TYPE`, `503 STORAGE_UNAVAILABLE`.

### 8.12 Provision a student (admin)

`POST /api/v1/admin/students`

```json
{
  "rollNo": "23CSE001",
  "fullName": "Demo Student One",
  "branch": "CSE",
  "currentSemester": 3,
  "section": "A",
  "admissionYear": 2023,
  "phone": null
}
```

`201 Created`

```json
{
  "id": 1001,
  "rollNo": "23CSE001",
  "email": "23cse001@university.example",
  "fullName": "Demo Student One",
  "branch": "CSE",
  "currentSemester": 3,
  "section": "A",
  "admissionYear": 2023,
  "phone": null,
  "active": true,
  "mustChangePassword": true,
  "locked": false,
  "lastLoginAt": null
}
```

- The request has **no** `email` and **no** `password` field. Sending either is rejected as an unknown field (`400`).
- `email` in the response was generated from the roll number and the configured domain.
- The temporary password is the roll number. It is not returned.
- `400 VALIDATION_FAILED` when the roll number is missing or has characters other than letters and digits. `409 CONFLICT` when the roll number or the generated email already exists.
- `PUT /api/v1/admin/students/{id}` takes the same fields except `rollNo`, which cannot be changed.

### 8.13 Reset a student's password (admin)

`POST /api/v1/admin/students/1001/reset-password`

`200 OK`

```json
{ "temporaryPassword": "<random, shown once>" }
```

The password is random, not the roll number. The student's sessions are ended, the account is unlocked, and `mustChangePassword` becomes `true`. This is the only response in the API that contains a password, and it is never logged or audited.

### 8.14 Import students from a CSV file (admin)

`POST /api/v1/admin/imports/students` as `multipart/form-data` with one part, `file`:

```text
rollNo,fullName,branch,currentSemester,section,admissionYear,phone
23CSE101,Demo Student,CSE,3,A,2023,
23CSE102,"Demo, Second",CSE,3,A,2023,+91 90000 00000
```

`201 Created`

```json
{ "created": 2 }
```

- The columns may come in any order and any letter case; `phone` may be left out. An unknown, repeated or missing column is refused. Empty columns with no name, which spreadsheets often add after the last one, are ignored, but a value in such a column is refused.
- Every line follows the same rules as 8.12, through the same service: the email is generated, the first password is the roll number, and nothing is returned about either.
- The whole file is checked before anything is saved. If any line is wrong, nothing is created and the answer is `400 VALIDATION_FAILED` with every problem, at most 100. The field name carries the line number of the file:

```json
{
  "code": "VALIDATION_FAILED",
  "errors": [
    { "field": "rows[3].fullName", "message": "Full name is required." },
    { "field": "rows[4].currentSemester", "message": "\"three\" is not a whole number." },
    { "field": "rows[7].rollNo", "message": "Roll number 23CSE101 is also on line 2." },
    { "field": "rows[8].rollNo", "message": "A student with roll number 23CSE001 already exists." }
  ]
}
```

- `header` names a problem with the first line and `file` one with the whole file: empty, not UTF-8, a quote that is never closed, or more than 100 students.
- `409 CONFLICT` if an account appears while the students are being created, for example a generated email that another account already has. The transaction is undone, so no student of the file is left behind.
- `413 UPLOAD_TOO_LARGE` over 1 MB; `415 UPLOAD_INVALID_TYPE` when the file name does not end in `.csv`.
- The file is read in memory and never stored.

### 8.15 Read the audit log (admin)

`GET /api/v1/admin/audit-logs?action=PASSWORD_RESET&entityType=USER&page=0&size=20`

Both filters are optional; `size` is 1 to 100. The answer is a page (section 5), newest entry first:

```json
{
  "content": [
    {
      "id": 412,
      "createdAt": "2026-10-17T14:39:02Z",
      "action": "ACCOUNT_DEACTIVATED",
      "actor": "admin@university.example",
      "entityType": "USER",
      "entityId": "1001",
      "subject": "23cse001@university.example",
      "oldValue": { "active": true },
      "newValue": { "active": false },
      "ipAddress": "203.0.113.7"
    }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

- `actor` is an email, or `System` for actions taken by the application itself. `subject` is the email of an account or the roll number of a student, and is null when none is known.
- For `PASSWORD_RESET` and `PASSWORD_CHANGED`, `oldValue` and `newValue` are always null. No entry has a password or a hash in any field.
- The endpoint only reads. There is no way through the API to add, change or remove an entry.

### 8.16 Import an attendance report (admin)

`POST /api/v1/admin/imports/attendance-reports?fromDate=2026-07-27&toDate=2026-09-30` as `multipart/form-data` with one part, `file`:

```text
rollNo,classesHeld,classesAttended,percent
23CSE101,211,163,77.25
```

`200 OK`

```json
{ "created": 1, "corrected": 0, "unchanged": 0 }
```

- Columns in any order and letter case: `rollNo`, `classesHeld`, `classesAttended`, and optionally `percent` and `fullName` (which is ignored). When `percent` is given, it must agree with the two numbers to within 0.01, which catches typing slips.
- The period must not end in the future, and `fromDate` must not be after `toDate`.
- The whole file is checked first. Unknown or repeated roll numbers, numbers out of range and a disagreeing percent are listed as `rows[<line>].<field>`; nothing is saved. At most 1,000 lines and 1 MB.
- Importing the same period again replaces the numbers of the students in the file; each change is audited as `ATTENDANCE_CORRECTED`.

### 8.17 Previous-year papers (students)

All under `/api/v1/pyqs`, students only. A hidden paper is `404` to a student everywhere.

- `GET /overview` returns `{ "branches": [{ "branch": "CSE", "subjects": 70, "papers": 354 }], "years": [2026, 2025], "regulations": ["AR18", "AR20"], "totalPapers": 1243 }`.
- `GET /subjects?branch=CSE&q=learning` returns one entry per subject: `{ "branch": "CSE", "subject": "Machine Learning", "papers": 9, "firstYear": 2022, "latestYear": 2026 }`. Both filters are optional.
- `GET /?branch=&subject=&year=&kind=&regulation=&q=&sort=&page=0&size=20` returns a page (section 5) of papers. `kind` is `REGULAR`, `SUPPLY` or `MID`; `sort` is `newest` (default), `oldest`, `popular` or `subject`; `q` is split into words (at most three, any order); each word must appear in the subject, the course code or the year, case-insensitively, and `%` and `_` are ordinary characters. `size` is 1 to 100; `year` is 2000 to 2100.
- `GET /saved?page=&size=` returns the student's saved papers that are still published.
- `GET /{id}/link?download=true|false` returns `{ "url": "...", "expiresAt": "..." }`. With `download=true` the link saves the file as `CSE - Machine Learning - 2025 December Supplementary AR20.pdf`, and the download is counted. `503 STORAGE_UNAVAILABLE` if file storage is down.
- `PUT /{id}/save` and `DELETE /{id}/save` answer `204`, and repeating either changes nothing.

A paper:

```json
{
  "id": 412, "branch": "CSE", "subject": "Machine Learning", "year": 2025, "month": 12,
  "monthName": "December", "kind": "SUPPLY", "midNumber": null, "regulation": "AR20",
  "courseCode": null, "variant": 1, "sizeBytes": 213004, "downloads": 7, "bookmarked": false,
  "status": "PUBLISHED", "createdAt": "2026-10-18T14:30:00Z",
  "downloadName": "CSE - Machine Learning - 2025 December Supplementary AR20.pdf"
}
```

The storage path is never in a response.

### 8.18 Previous-year papers (admin)

- `POST /api/v1/admin/pyqs` as `multipart/form-data`: the part `file` (a PDF of at most 10 MB), and **either** a JSON part `metadata` (`branch`, `subject`, `year`, `month` 1 to 12, `kind`, optional `midNumber` 1 to 4 for a mid paper, `regulation` such as `AR20`, `courseCode`, `variant`) **or** a text field `path` with where the file sits in the organised folder, such as `CSE/Machine Learning/2025(December)-supply AR20 (2).pdf`. The file name is `year(Month)-regular|supply|mid|mid-1|mid-2`, then optionally a regulation or a course code, then optionally a question-set number in brackets. `201` with the paper.
- Failures: `400 VALIDATION_FAILED` naming `path` or the field (an unreadable path, a mid number on a regular paper); `409 CONFLICT` when the same file, or the same sitting, is already in the library (the message names it and says to give a second question set a set number); `413 UPLOAD_TOO_LARGE`; `415 UPLOAD_INVALID_TYPE`; `503 STORAGE_UNAVAILABLE`.
- `GET /api/v1/admin/pyqs?branch=&status=&q=&page=&size=` lists every paper, hidden ones included.
- `GET /api/v1/admin/pyqs/{id}/link` gives a five-minute link to any paper. It is not counted as a download.
- `PUT /api/v1/admin/pyqs/{id}` with `{ "metadata": {...}, "status": "PUBLISHED|HIDDEN" }` changes the details or the visibility. `409` if the new details are another paper's.
- `DELETE /api/v1/admin/pyqs/{id}` removes the saves, the row and then the file; if the file cannot be removed nothing is removed (`503`).

### 8.19 Complaints (students)

Student only. The student always comes from the session.

- `POST /complaints` with `{type: PUBLIC|PRIVATE|SECURE, category, title (5-120), description (20-2000), showIdentity}` returns 201 with the complaint. A sixth complaint in 24 hours returns 429 `RATE_LIMITED`.
- `GET /complaints?category=&status=&sort=newest|top&page=&size=` returns a page of public, non-rejected complaints (`size` 1 to 50).
- `GET /complaints/mine` returns the student's own complaints of every type.
- `GET /complaints/{id}` returns a public complaint on the board, or one of the student's own; otherwise 404.
- `DELETE /complaints/{id}` withdraws an own complaint: 204; 409 when it is no longer Open; 404 when it is not theirs.
- `PUT` and `DELETE /complaints/{id}/support` support or unsupport a public complaint and return it. 409 for your own or a resolved complaint; 404 for one that is not on the board. Repeating either changes nothing.
- A response has `id, type, category, title, description, status, adminResponse, respondedAt, createdAt, authorRollNo, supporters, group, supported, mine`. `authorRollNo` is set only on a public complaint whose author chose to show it. `group` is true from 5 supporters.

### 8.20 Complaints (admin)

- `GET /admin/complaints?type=&status=&page=&size=` lists every complaint, open first, then in review, then the rest.
- `GET /admin/complaints/{id}` returns one, with `supportedBy` for a public complaint.
- `PUT /admin/complaints/{id}` with `{status, response}` sets the handling and is audited as `COMPLAINT_UPDATED`.
- A response has `student` (`rollNo, fullName, branch, semester, section`), `identityProtected` and `hiddenFromStudents`. **For a `SECURE` complaint `student` is always null, `identityProtected` is true, and `createdAt` is cut to the day.**

### 8.21 Notes (students)

Student only ([ADR 0014](decisions/0014-notes-quick-assistant-and-robot.md)). The student always comes from the session; another student's note, folder or file is `404`. JSON is `camelCase`. A note has `id, title, content (sanitized HTML), source (manual|robot), folderId, tags, pinned, archived, deletedAt, createdAt, updatedAt, folder, shared, sharedPermission, sharedBy`.

- `GET /notes?view=all|recent|pinned|shared|attachments|trash|archived&sort=updated|created|alphabetical&q=&folder=<id>|unfiled&tag=&attachments=true` returns `{notes}`. Search treats `%` and `_` as plain text.
- `POST /notes` with `{title?, content?, source?, folderId?, tags?}` returns 201. It needs a title or content (`400`). A `robot` note with no folder is filed into `Quick Notes`.
- `PATCH /notes/{id}` with any of `{title, content, tags, pinned, archived, folderId, unfile, restore}` returns `{note}`, or `{deleted: true}` when the edit left the note empty. A classmate with edit permission may change `title` and `content` only. Content over 200,000 characters is `400`. This endpoint and `POST /notes` accept a body up to 1 MB.
- `DELETE /notes/{id}` moves to the Trash; `?permanent=true` destroys it with its files. `POST /notes/{id}/duplicate` returns 201.
- `POST /notes/bulk` with `{action: move|pin|unpin|archive|unarchive|trash|restore|delete|empty_trash, ids (at most 200), folderId?}` returns `{success, count}`.
- Folders: `GET /notes/folders` (with `noteCount` and up to five `previews`; defaults are created on first use), `POST` `{name, color?, icon?, parentId?}` (`409` for a used name), `PATCH /{id}` `{name, color, icon, parentId, toTopLevel, position}` (`400` for a loop), `PATCH /notes/folders` `{order: [{id, position, parentId?, setParent?}]}`, `DELETE /{id}` (its notes become unfiled).
- Attachments: `POST /notes/attachments?noteId=` (multipart part `file`, at most 10 MB) returns 201 with `{id, url, name, mimeType, sizeBytes, kind}`. A JPEG, PNG or GIF picture is stored as a new JPEG; a PDF is kept; other types are `415` unless switched on. `GET /notes/attachments?kind=` lists; `DELETE /{id}`; `GET /{id}/file[?download=true][&share=<slug>]` redirects (302) to a five-minute signed link, for the uploader or the note's owner (a picture a shared editor added belongs to the note), a named recipient of the note, or a student who presents the note's share link as `share` while that link is on. The attachment number alone opens nothing of someone else's, and while the note is in the Trash only the owner gets the file; everyone else gets 404.
- Links: `POST /notes/{id}/links` `{targetType: note|folder|resource, targetId}` returns `{links}`; `GET /notes/link-targets?q=&exclude=`.
- Sharing: `GET /notes/{id}/share` returns `{share}` (null when never shared), where `share` is `{slug, linkAccess, recipients: [{id, recipientId, rollNo, permission}]}`. A recipient is listed by roll number only, never by name, so sharing cannot be used to look up the name behind a roll number; `PUT` `{linkAccess: private|view}`; `POST /share/recipients` `{identifier (roll number or college email), permission: view|edit}`; `DELETE /share/recipients/{recipientId}`; `DELETE /share` stops all sharing. `GET /notes/shared/{slug}` opens a note read-only for any signed-in student while the link is on, otherwise `404`.

## 9. AI endpoints

Behaviour is specified in [12-AI-features-spec.md](12-AI-features-spec.md). This section is the wire contract.

Common to all three:

- Time budget 25 seconds. On expiry: `504 AI_TIMEOUT`.
- Failures: `400`, `422 AI_REQUEST_DECLINED`, `429 AI_QUOTA_EXCEEDED`, `429 RATE_LIMITED`, `502 AI_INVALID_RESPONSE`, `503 AI_UNAVAILABLE`, `504 AI_TIMEOUT`.
- Every success response includes `basis`, `notice` and `promptVersion`.
- `basis` is `GENERAL_KNOWLEDGE`, `PROVIDED_TEXT` or `MIXED`.
- All text fields are plain text. The frontend renders them as text.
- A `200` means the reply had the required structure and size. It does not certify that the content is correct or follows the AI rules, which is why every reply carries the AI-generated notice.

### 9.1 Explain

`POST /api/v1/ai/explain`

| Field | Type | Rule |
| --- | --- | --- |
| `topic` | string | Required, 3 to 200 characters. |
| `subjectId` | integer | Optional. Must be a subject the student can see. |
| `unitId` | integer | Optional. Must belong to `subjectId`. |
| `answerFormat` | enum | Required: `SIMPLE`, `FIVE_MARK`, `TEN_MARK`. |
| `context` | string | Optional, up to 2,000 characters of the student's own material. |

```json
{
  "topic": "Normalization in DBMS",
  "subjectId": 12,
  "unitId": 31,
  "answerFormat": "FIVE_MARK",
  "context": null
}
```

`200 OK`

```json
{
  "topic": "Normalization in DBMS",
  "answerFormat": "FIVE_MARK",
  "explanation": "Normalization is the process of organising a relational database to reduce redundancy...",
  "keyPoints": [
    "1NF: atomic values, no repeating groups.",
    "2NF: 1NF and no partial dependency on a composite key.",
    "3NF: 2NF and no transitive dependency."
  ],
  "example": "A table Student(roll, name, dept, deptHead) repeats deptHead for every student...",
  "basis": "GENERAL_KNOWLEDGE",
  "notice": "AI-generated. Check it against your course material.",
  "promptVersion": "explain-v1"
}
```

`example` may be `null`. `keyPoints` has 1 to 8 items.

### 9.2 Summarize

`POST /api/v1/ai/summarize`

| Field | Type | Rule |
| --- | --- | --- |
| `text` | string | Required, 200 to 12,000 characters. |
| `style` | enum | Required: `SHORT`, `REVISION_NOTES`. |
| `subjectId` | integer | Optional. |

`200 OK`

```json
{
  "summary": "The text describes how a deadlock arises when four conditions hold together...",
  "keyPoints": ["Mutual exclusion", "Hold and wait", "No preemption", "Circular wait"],
  "definitions": [
    { "term": "Deadlock", "meaning": "A state where each process waits for a resource held by another." }
  ],
  "basis": "PROVIDED_TEXT",
  "notice": "AI-generated. Check it against your course material.",
  "promptVersion": "summarize-v1"
}
```

`definitions` may be empty.

### 9.3 Generate practice questions

`POST /api/v1/ai/generate-quiz`

| Field | Type | Rule |
| --- | --- | --- |
| `topic` | string | 3 to 200 characters. Required if `sourceText` is absent. |
| `sourceText` | string | 200 to 8,000 characters. Required if `topic` is absent. |
| `subjectId`, `unitId` | integer | Optional. |
| `questionType` | enum | Required: `MCQ`, `SHORT`, `LONG`. |
| `count` | integer | Required, 1 to 10. |

`200 OK`

```json
{
  "questions": [
    {
      "type": "MCQ",
      "question": "Which condition is NOT required for deadlock?",
      "options": ["Mutual exclusion", "Preemption", "Hold and wait", "Circular wait"],
      "correctOption": "B",
      "answer": "Preemption",
      "explanation": "Deadlock requires no preemption; allowing preemption breaks it."
    }
  ],
  "requested": 5,
  "returned": 5,
  "basis": "GENERAL_KNOWLEDGE",
  "notice": "AI-generated practice questions. They are not official exam questions.",
  "promptVersion": "quiz-v1"
}
```

For `SHORT` and `LONG`, `options` and `correctOption` are `null`. `returned` can be less than `requested` when invalid questions were discarded. If none survive, the response is `502 AI_INVALID_RESPONSE`.

### 9.4 Usage

`GET /api/v1/ai/usage/me`

```json
{ "dailyLimit": 30, "usedToday": 4, "remainingToday": 26, "resetsAt": "2026-10-10T18:30:00Z", "enabled": true, "demo": false }
```

`demo` is `true` while the built-in mock provider answers (`AI_PROVIDER=mock`). The assistant page then shows a "Demo mode" note, because the answers are placeholders and not the work of an AI model.

The day resets at midnight in `Asia/Kolkata`. `enabled` is `false` when AI is switched off (`AI_ENABLED=false`) or when the application-wide daily ceiling has been reached. In both cases the AI endpoints return `503 AI_UNAVAILABLE`.

### 9.5 Quick Assistant chat

Student only. `POST /assistant/chat` with `{conversationId?, message (1-4000), mode: "quick"}` returns `{conversation, question, reply, notice}`; the reply is plain text of a few sentences. Up to twenty earlier messages of the chat are sent with it. It counts as one AI request against the same daily and per-minute limits; a failed answer stores nothing and does not count. Errors are those of section 9: `503 AI_UNAVAILABLE`, `504 AI_TIMEOUT`, `502 AI_INVALID_RESPONSE`, `429`. `GET /assistant/conversations?source=quick` lists the newest six chats; `GET /assistant/conversations/{id}/messages`; `DELETE /assistant/conversations/{id}` returns 204. A chat is read by its student only.

## 10. Changing the API

1. Update `openapi.yaml` and this document in the same commit as the code.
2. Add or update the controller test for the endpoint.
3. Update [10-authorization-matrix.md](10-authorization-matrix.md) if access changes.
4. Removing or renaming a field, or changing its type, is a breaking change.
