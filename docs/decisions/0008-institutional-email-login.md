# 0008. Institutional email login, with the roll number as a first-time password

- Status: Accepted by the owner's change request of 2026-10-11, **as a security exception for the assignment and demonstration, with synthetic data only**. Not approved for real students (open decision OQ-17).
- Date: 2026-10-11
- Deciders: Laxman Sirvi
- Clarifies: [0004](0004-session-cookie-authentication.md)

## Context

The first design had students log in with their roll number, and gave each new account a random temporary password shown once to the admin. The owner now requires:

1. Students log in with their institution-issued email address, of the form `<roll number>@<official domain>`.
2. The official domain is configuration, not code. The domain given in the request (`university.edu.com`) was an example and is not verified.
3. The roll number remains a separate academic identifier.
4. A new student's temporary password is their roll number, and it must be changed at first login before anything else can be done.

The fourth point conflicts with an existing rule (SR-03: a predictable value must not be a default password). The owner asked for the conflict to be documented as an explicit, approved exception rather than quietly replaced with a different mechanism.

## Options considered

1. **Keep roll-number login and random temporary passwords.** Does not meet the requirement.
2. **Email login with random temporary passwords handed over in person.** Meets points 1 to 3 and is safer, but it is not what was asked for point 4, and it needs every student to be given a password individually.
3. **Email login with the roll number as the temporary password, forced change, and a recorded exception.** What the owner asked for.
4. **Replace authentication with Supabase Auth, JWT or institutional single sign-on.** Out of scope; contradicts [0001](0001-spring-boot-as-sole-backend.md), [0002](0002-supabase-postgresql-and-storage.md) and [0004](0004-session-cookie-authentication.md).

## Decision

Adopt option 3.

- **Identity.** Every account logs in with one email address, stored trimmed and in lower case in `users.login_email` and unique in the database. A student's email is generated as the roll number in lower case, `@`, and the value of `STUDENT_EMAIL_DOMAIN`. The roll number stays in `student_profiles.roll_no`. There is no `username` and no second email column.
- **Architecture unchanged.** Spring Security, server-side sessions, BCrypt, CSRF tokens. Only the identifier and the first-credential policy change.
- **Provisioning.** An admin provisions students from the verified roster (a bulk import is P1). There is no self-registration. Admin accounts are provisioned separately and never get a roll-number password.
- **First password.** A newly provisioned student's temporary password is the roll number as stored. Only its salted hash is stored. `must_change_password` is set, and the server refuses everything except changing the password until it has been changed.
- **Password change.** Current password plus the new one twice. The new password must not be the roll number, the email, the part of the email before the `@`, or the current password. On success the flag is cleared, other sessions end and the session identifier is renewed.
- **Reset.** An admin reset issues a random temporary password, never the roll number.
- **No disclosure.** Login and lockout responses are the same whether or not an account exists.
- **Not verified.** The application sends no email and does not claim an address is verified.

## Security exception EX-AUTH-01

Using the roll number as a first password is **weak, and known to be weak**. The email address contains the roll number, and roll numbers are not secret, so anyone who knows a roll number holds the whole credential until the student changes it. They could read that student's records and lock the student out. Lockout offers no protection because the first guess is right. The full statement, the eight mandatory conditions and the recommended practices are in [11-security-plan.md](../11-security-plan.md) §3.4.

It is accepted because the owner requires this onboarding for the assignment and demonstration, and because all data is synthetic.

**It is not approved for real students.** Before any deployment that holds real student data, the owner must carry out an explicit review and record a new decision that either confirms or replaces this policy.

## Trade-offs

- Onboarding is simple: no passwords to distribute. The price is that every unclaimed account is open to anyone with a class list.
- The temporary password cannot be kept out of logs and lists in any meaningful sense, because it is also an identifier.
- Generating the email from the roll number assumes that is the institution's real naming convention (OQ-15). If it is not, provisioning must change.
- Making the roll number and the email unchangeable through the application keeps identity stable but means a typing mistake is fixed by deactivating and re-provisioning (OQ-16).
- Blocking failed attempts for emails that have no account needs a small in-memory tracker in addition to the counters in the database.

## Consequences

- Schema: `users.username` becomes `users.login_email`; `student_profiles.email` is removed. No migration has run anywhere, so `V1__core.sql` is simply written this way. The contingency for legacy accounts is in [08-database-design.md](../08-database-design.md) §8.
- API: login takes `email`; the current-user response returns `email`; change-password takes the new password twice; provisioning a student returns no password.
- Configuration: `STUDENT_EMAIL_DOMAIN` is added and `ADMIN_BOOTSTRAP_USERNAME` becomes `ADMIN_BOOTSTRAP_EMAIL`. The seed password variable is removed, because seeded students are provisioned like any other.
- Requirements: FR-AUTH-001, FR-AUTH-003, FR-AUTH-005 and FR-ADM-001 are revised and FR-AUTH-006 is added.
- Rules: `SECURITY_RULES.md` SR-02, SR-03 and SR-10 are revised and section 11 is added.
- Open decision OQ-13 (does the lockout response reveal an account) is resolved: it must not.
- New open decisions: OQ-15 (the official domain and naming convention), OQ-16 (reset behaviour and correcting a roll number), OQ-17 (real students).
