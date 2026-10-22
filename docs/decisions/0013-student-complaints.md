# 0013. Student complaints: public, private and secure

- Status: Accepted by the owner's request of 2026-10-23
- Date: 2026-10-23
- Deciders: Laxman Sirvi
- Changes: adds FR-CMP-001 to FR-CMP-004 (P1); adds the tables `complaints`, `complaint_owners` and `complaint_supports` (migration V13); adds the audit action `COMPLAINT_UPDATED`

## Context

Students have no way to raise an issue (a classroom, an exam, a facility) inside the application. The owner asked for complaint registration with three kinds of complaint, each with a different promise about who sees it and who sees the student.

## Decision

A student raises a complaint of one **type**, chosen by them:

| Type | Who sees the complaint | Who sees the student |
| --- | --- | --- |
| **Public** | Every student and the administrator | The student chooses: roll number and name shown, or "Anonymous" to other students. **The administrator can always see who raised a public complaint.** |
| **Private** | Only the administrator | The administrator |
| **Secure** | Only the administrator | **Nobody on the administrator's side.** The administrator's screens and API never carry the student's identity. |

- **Group complaints.** Students can *support* a public complaint ("I face this too"), once each, and not their own. The page shows the number of supporters; from 5 supporters the complaint is marked a **group complaint**. The administrator sees the supporters. Support is stored apart from the complaint and carries no text.
- **Handling.** The administrator sets a status (Open, In review, Resolved, Rejected) and writes a response. A rejected public complaint disappears from the public board. The student always sees the status and response of their own complaints, including secure ones.
- **Withdrawing.** A student can delete their own complaint while it is still Open.
- **Limits.** Five new complaints per student per 24 hours; a title of at most 120 characters and a description of at most 2,000, plain text.
- **Where the identity lives.** The complaint row has no student column. The student is kept in `complaint_owners`, read only by the student's own endpoints and, for public and private complaints, by the administrator's service. The administrator's service never reads that table for a secure complaint, and a test asserts that no secure response contains an identity. A secure complaint's time is shown to the administrator as a date only, so it cannot be matched to a login time.
- **Honest limit.** "Secure" protects the student from the application's administrator. It does not stop the owner of the database from joining the two tables. This is said on the page.
- **Audit.** Each status change is audited as `COMPLAINT_UPDATED` with the status only, never the student, so the audit log cannot reveal a secure complaint's author.

## Options considered

1. **Store no owner for secure complaints.** Strongest anonymity, but the student could never see the response. Rejected: a complaint nobody can answer is not useful.
2. **One table with a student column and a flag.** Simpler, but every administrator query would carry the identity and one mistake would leak it. Rejected for the separate table.
3. **Chosen:** separate owner table, with the secure rule enforced in one service and tested.

## Consequences

- A migration and three tables, each with Row Level Security.
- An endpoint pair for students (`/api/v1/complaints/**`, students only) and one for administrators (`/api/v1/admin/complaints/**`).
- To be confirmed by the owner: whether the administrator should see the identity behind a *hidden* public complaint (decided: yes, and the form says so), and the group threshold of 5.
