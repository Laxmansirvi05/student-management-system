# 09. API Contract

> Status: Draft v0.1 · Owner: Laxman Sirvi · Last updated: 2026-10-10

This document explains the REST API conventions and gives examples. The machine-readable contract is [openapi.yaml](openapi.yaml), which is authoritative for the paths, fields and status codes of the endpoints it lists. It currently covers every student-facing P0 endpoint and the main admin endpoints; the simpler admin CRUD endpoints in section 7.6 are added to it by the task that implements them. If the two files disagree, fix both in the same change.

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
| 401 | `AUTH_INVALID_CREDENTIALS` | Login failed. Same response for unknown user, wrong password and inactive account. |
| 403 | `ACCESS_DENIED` | Logged in, but the role is not allowed. |
| 403 | `AUTH_PASSWORD_CHANGE_REQUIRED` | The account must change its password first. |
| 403 | `CSRF_INVALID` | CSRF token missing or wrong. |
| 404 | `NOT_FOUND` | The record does not exist, **or belongs to someone else**. |
| 409 | `CONFLICT` | Duplicate or state conflict, for example a duplicate roll number. |
| 409 | `QUIZ_NOT_ENOUGH_QUESTIONS` | Fewer published questions than requested. Includes `available`. |
| 409 | `QUIZ_ALREADY_SUBMITTED` | The attempt was already submitted. |
| 409 | `QUIZ_ATTEMPT_EXPIRED` | Submitted after the time limit and grace period. |
| 413 | `UPLOAD_TOO_LARGE` | File over 10 MB. |
| 415 | `UPLOAD_INVALID_TYPE` | Not a valid PDF. |
| 422 | `AI_REQUEST_DECLINED` | The assistant declined the request (off-topic or unsafe). `detail` gives the reason. |
| 423 | `AUTH_ACCOUNT_LOCKED` | Too many failed logins. |
| 429 | `RATE_LIMITED` | Too many requests. Includes a `Retry-After` header. |
| 429 | `AI_QUOTA_EXCEEDED` | Daily AI limit reached. |
| 500 | `INTERNAL_ERROR` | Unexpected failure. |
| 502 | `AI_INVALID_RESPONSE` | The AI provider returned output that failed validation. |
| 503 | `AI_UNAVAILABLE` | AI provider error, or AI switched off. |
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
| JSON request body | 64 KB |
| File upload | 10 MB, PDF only |
| Login attempts | 5 consecutive failures lock the account for 15 minutes; 20 attempts per IP address per 10 minutes |
| AI requests | 30 per user per day; 5 per user per minute |
| Other API calls | 120 per session per minute |
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
| GET | `/students/me/dashboard` | Student | Dashboard summary. |
| GET | `/attendance/me` | Student | Subject-wise attendance. |
| GET | `/attendance/me/subjects/{subjectId}` | Student | Dated history for one subject (paginated). |
| GET | `/attendance/me/calculator` | Student | Classes needed for a target (P1). |
| GET | `/marks/me` | Student | Published assessment marks. Filters: `subjectId`, `semester`. |
| GET | `/results/me` | Student | Semester results and CGPA. |
| GET | `/timetable/me` | Student | Weekly timetable. Filter: `day`. |
| GET | `/notices` | Any | Published notices (paginated). Filter: `category`. |
| GET | `/notices/{id}` | Any | One published notice. |
| GET | `/subjects` | Any | Subjects (a student sees their branch). Filter: `semester`. |
| GET | `/subjects/{id}/units` | Any | Units of a subject. |

### 7.3 Learning resources

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| GET | `/resources` | Any | Search published resources. Filters: `subjectId`, `unitId`, `semester`, `type`, `q`. Sort: `createdAt`, `title`. |
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
| GET, POST | `/admin/students` | Search and create students. |
| GET, PUT | `/admin/students/{id}` | Read and update a student. |
| POST | `/admin/students/{id}/deactivate`, `/activate` | Change account status. |
| POST | `/admin/students/{id}/reset-password` | Issue a temporary password (returned once). |
| GET, POST | `/admin/subjects` | List and create subjects. |
| PUT | `/admin/subjects/{id}` | Update or deactivate a subject. |
| POST | `/admin/subjects/{id}/units` | Add a unit. |
| PUT, DELETE | `/admin/units/{id}` | Update or remove a unit that is not in use. |
| GET | `/admin/attendance` | Attendance sheet for `subjectId`, `section`, `classDate`, `periodNo`. |
| PUT | `/admin/attendance` | Save a whole attendance sheet (create or correct). |
| GET, POST | `/admin/assessments` | List and create assessments. |
| PUT | `/admin/assessments/{id}` | Update an assessment, including `published`. |
| GET, PUT | `/admin/assessments/{id}/marks` | Read and save the marks sheet. |
| PUT | `/admin/semester-results` | Create or update one student's semester result. |
| GET, POST | `/admin/timetable` | List and create timetable entries. |
| PUT, DELETE | `/admin/timetable/{id}` | Update or delete an entry. |
| GET, POST | `/admin/notices` | List all notices (including drafts) and create. |
| PUT, DELETE | `/admin/notices/{id}` | Update or delete a notice. |
| POST | `/admin/notices/{id}/publish`, `/unpublish` | Change status. |
| GET, POST | `/admin/resources` | List all resources and upload (multipart). |
| PUT, DELETE | `/admin/resources/{id}` | Update details or status; delete. |
| GET, POST | `/admin/quiz-questions` | List and create questions. |
| PUT | `/admin/quiz-questions/{id}` | Update a question, including `status`. |
| GET | `/admin/audit-logs` | Read the audit log (P1). |
| POST | `/admin/imports/students` | CSV import (P1). |

### 7.7 Health

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| GET | `/actuator/health` | Public | Returns `{"status":"UP"}` or `DOWN`. No details are exposed. |

## 8. Examples

### 8.1 Log in

`POST /api/v1/auth/login`

```json
{ "username": "23CSE001", "password": "<password>" }
```

`200 OK`

```json
{
  "username": "23cse001",
  "displayName": "Demo Student One",
  "role": "STUDENT",
  "mustChangePassword": false
}
```

Failures: `401 AUTH_INVALID_CREDENTIALS`, `423 AUTH_ACCOUNT_LOCKED`, `429 RATE_LIMITED`.

Validation: `username` 1 to 50 characters; `password` 1 to 72 characters.

### 8.2 Change password

`POST /api/v1/auth/change-password`

```json
{ "currentPassword": "<current>", "newPassword": "<new>" }
```

`204 No Content`. Validation: `newPassword` 8 to 72 characters, different from the username and from the current password.

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
  ]
}
```

A subject with no classes has `classesHeld: 0` and `percent: null`.

### 8.4 Own results

`GET /api/v1/results/me`

```json
{
  "cgpa": 8.02,
  "semesters": [
    { "semester": 1, "sgpa": 7.8, "creditsEarned": 22, "resultStatus": "PASS" },
    { "semester": 2, "sgpa": 8.24, "creditsEarned": 22, "resultStatus": "PASS" }
  ]
}
```

`cgpa` is `null` and `semesters` is empty when nothing is published.

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
      "unitId": 31,
      "unitNo": 3,
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

The response never contains the correct option or the explanation.

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

Questions left out or sent as `null` count as wrong. A `questionId` that is not part of the attempt gives `400`.

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

## 9. AI endpoints

Behaviour is specified in [12-AI-features-spec.md](12-AI-features-spec.md). This section is the wire contract.

Common to all three:

- Time budget 25 seconds. On expiry: `504 AI_TIMEOUT`.
- Failures: `400`, `422 AI_REQUEST_DECLINED`, `429 AI_QUOTA_EXCEEDED`, `429 RATE_LIMITED`, `502 AI_INVALID_RESPONSE`, `503 AI_UNAVAILABLE`, `504 AI_TIMEOUT`.
- Every success response includes `basis`, `notice` and `promptVersion`.
- `basis` is `GENERAL_KNOWLEDGE`, `PROVIDED_TEXT` or `MIXED`.
- All text fields are plain text. The frontend renders them as text.

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
{ "dailyLimit": 30, "usedToday": 4, "remainingToday": 26, "resetsAt": "2026-10-10T18:30:00Z", "enabled": true }
```

The day resets at midnight in `Asia/Kolkata`.

## 10. Changing the API

1. Update `openapi.yaml` and this document in the same commit as the code.
2. Add or update the controller test for the endpoint.
3. Update [10-authorization-matrix.md](10-authorization-matrix.md) if access changes.
4. Removing or renaming a field, or changing its type, is a breaking change.
