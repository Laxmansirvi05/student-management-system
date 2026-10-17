# 0010. Roll number as the first password, with real students

- Status: Accepted by the owner on 2026-10-17 (answers open decision OQ-17). Not yet run on a real deployment.
- Date: 2026-10-17
- Deciders: Laxman Sirvi
- Changes: security exception EX-AUTH-01 (from "synthetic data only" to "may be used with real students, with the conditions below"); security rule SR-59 is met by this record

## Context

[ADR 0008](0008-institutional-email-login.md) made the roll number, in capital letters, the first password of a new student. It was approved for the assignment and demonstration with invented students only, and OQ-17 asked the owner to decide before any real student data was loaded. The owner has now been asked how real students should get their first password, with random one-time passwords as the recommended alternative, and chose to keep the roll number.

## The risk, as stated to the owner

The login email contains the roll number, so until a student has logged in once, anyone who knows the roll number can log in as that student, read their records and choose a password that locks the student out. Roll numbers are not secret inside a college. The records at risk are attendance, marks, results and the student's phone number.

## Decision

The roll number in capital letters is the first password of every student, including real students, set by the single-student form and by the CSV import. The conditions that already hold stay, and four operating rules are added so the open window is as short as possible.

Already built and tested: the server refuses everything except the password change until it is done; only the hash is stored and the password is never returned; students only, never administrators; a reset gives a random password; five failed logins lock an email address; the first password cannot be kept as the new one.

Operating rules the owner accepts:

1. **Create accounts shortly before students first log in.** Import the file the day the students are told, not weeks earlier.
2. **Tell students the same day** how to log in and to change the password at once.
3. **Review accounts that are still "Awaiting first login"** a few days later (Admin, Students) and reset or deactivate any that should not be open.
4. **Treat a student's report that their password was already changed before they logged in as a takeover:** reset the account and read the audit entry (Admin, Audit log, filter "Password reset" or "Password changed").

## Consequences

- The deployment may hold real student data once the other release checks are done (the owner tasks in [21-security-review.md](../21-security-review.md) section 3).
- Until a student logs in, the account is as safe as the secrecy of the roll number. That is accepted.
- If this is ever not acceptable, the alternatives remain: random one-time passwords handed out in person, an activation step that needs a second fact, or the college's single sign-on. Each would be a new decision record.
- Imports and demo data must still never be mixed: real students are loaded only into the real deployment, never into the demonstration database.
