# 19. Operations Runbook

> Status: Draft v0.2 · Owner: Laxman Sirvi · Last updated: 2026-10-11

What to do when the deployed system misbehaves. Each procedure lists symptoms, checks and fixes in the order to try them.

> **This runbook was written before the first deployment.** The commands are standard Heroku CLI and PostgreSQL tools, but they have not yet been run against this application. Task `T6-07` is to walk through each procedure on the live system and correct anything that differs.

Replace `<app>` with the Heroku application name.

## 0. First five minutes

1. **Is it down for everyone?** Open `https://<app>.herokuapp.com/actuator/health`.
2. **Look at the logs.**

   ```bash
   heroku logs --tail --app <app>
   ```

3. **Check the dyno.**

   ```bash
   heroku ps --app <app>
   ```

4. **What changed last?** A deploy, a config change, a migration, a key rotation?

   ```bash
   heroku releases --app <app>
   ```

5. **Check the providers.** Look at the Heroku, Supabase and AI provider status pages.
6. **Contain before you diagnose** if data or money is at risk: turn AI off (`AI_ENABLED=false`), or roll back.

Never paste log output containing secrets into an issue or a chat.

## 1. Application will not start

**Symptoms:** dyno state `crashed`; log shows `H10 App crashed` or a Java exception during startup; health URL returns an application error page.

| Check | If so |
| --- | --- |
| Log says a required property or environment variable is missing | Compare `heroku config` names with [18-production-plan.md](18-production-plan.md) §5 and set the missing one. |
| Log shows a Flyway error | Go to procedure 3. |
| Log shows a database connection error | Go to procedure 2. |
| Log shows `R10 Boot timeout` | The app did not bind to `PORT` in time. Confirm the `Procfile` passes `-Dserver.port=$PORT`. A slow first database connection can also cause this; restart once. |
| Log shows `R14 Memory quota exceeded` or `OutOfMemoryError` | Lower the heap in `JAVA_OPTS`, confirm the Hikari pool is 5, restart. |
| Log says a real AI provider is selected without a key | Set `AI_API_KEY`, or set `AI_PROVIDER=mock`. |
| Started failing right after a deploy | Roll back (procedure 10), then investigate. |

Restart after a fix:

```bash
heroku restart --app <app>
```

## 2. Database connection errors

**Symptoms:** health is `DOWN`; log shows connection refused, timeout, authentication failure, or "too many connections"; pages show "Something went wrong on our side".

1. Open the Supabase dashboard. **Is the project paused?** Free projects can pause after inactivity. Resume it and wait a few minutes.
2. **Authentication failed?** Read which role failed. The running application logs in as `cc_app` (`DB_PASSWORD`); Flyway logs in as `cc_migrator` at startup (`DB_MIGRATION_PASSWORD`). Set a new password for that role and update the matching variable. Do not switch the application to `postgres` to get it running.
3. **Wrong host or port?** Confirm `DB_URL` is the **session pooler** address on port 5432, not the transaction pooler.
4. **Too many connections?** Something else is using the pool (a SQL client left open, a second dyno). Close other clients; confirm one dyno; confirm Hikari maximum is 5.
5. **Timeouts only?** Check Supabase status; restart the dyno to rebuild the pool.
6. **"Permission denied" or "row-level security" errors for one table?** A migration created the table without its grants or policies for `cc_app`. Correct it with a new migration. Do not grant privileges by hand in production.
7. Confirm recovery with the health URL.

## 3. Failed database migration

**Symptoms:** startup log shows a Flyway exception such as "Migration ... failed" or "Validate failed: checksum mismatch".

**Do not edit or delete migration files that have already been applied.**

| Cause | Action |
| --- | --- |
| **SQL error in a new migration** | PostgreSQL runs each migration in a transaction, so it was normally rolled back. Roll the application back to the previous release (procedure 10) so the site works. Fix the SQL **in a new commit of the same file only if it has never been applied successfully anywhere**; otherwise add a corrective migration. Redeploy. |
| **Checksum mismatch** | An applied migration file was edited. Restore the file to its original content from Git history. If the change is needed, add it as a new migration. |
| **Permission denied while migrating** | Flyway is not connecting as `cc_migrator`, or that role no longer owns schema `app`. Check `DB_MIGRATION_USERNAME`. Changing ownership needs the owner, signed in as `postgres`. |
| **Migration left in a failed state** | Inspect `flyway_schema_history`. With the owner's approval, remove the failed row (Flyway "repair") after confirming the schema matches the last successful version. |
| **Migration applied but data is wrong** | Stop. Decide between a corrective migration and a restore (procedure 9). Do not improvise SQL against production. |

Any manual SQL against production needs the owner's approval and a backup first.

## 4. File upload or download errors

**Symptoms:** uploads fail with "Upload failed"; previews show "This file is temporarily unavailable"; API returns `503 STORAGE_UNAVAILABLE`.

1. **All files or one?**
   - One file: its object may be missing. Compare the row's `storage_path` with the bucket contents. Re-upload the file, or delete the orphaned row through the admin screen.
   - All files: continue.
2. **Check configuration:** `SUPABASE_URL`, `SUPABASE_STORAGE_BUCKET` names correct; the bucket exists.
3. **Check the key:** log shows 401 or 403 from storage means `SUPABASE_SERVICE_KEY` is wrong or was rotated. Set the current key.
4. **Check Supabase status** and storage quota in the dashboard. A full quota blocks uploads.
5. **Upload-specific:**
   - `413` means the file is over 10 MB. This is correct behaviour.
   - `415` means the file is not a valid PDF. Open it locally to confirm.
   - `H12 Request timeout` in the log means the upload took over 30 seconds; retry on a faster connection or reduce the file size.
6. **Preview blank but download works:** the browser may be blocking the frame. Check the browser console for a Content Security Policy error and confirm `frame-src` contains the production storage host.
7. **Links fail after a few minutes:** expected; signed links last five minutes. Reload the resource page.

## 5. AI provider rate limits and outages

**Symptoms:** assistant shows "unavailable"; API returns `503 AI_UNAVAILABLE`, `504 AI_TIMEOUT` or `502 AI_INVALID_RESPONSE`; log shows provider 429 or 5xx.

1. **Is it only AI?** Confirm the rest of the application works. It should.
2. **Check the provider's status page** and your account's usage and limits.
3. By symptom:

   | Symptom | Action |
   | --- | --- |
   | Provider returns 401 or 403 | Key invalid or revoked. Set a new `AI_API_KEY`. |
   | Provider returns 429 | Account rate or spend limit reached. Wait, or raise the limit deliberately. Consider lowering `AI_DAILY_LIMIT_PER_USER`. |
   | Timeouts | Provider slow. Nothing to fix locally; if persistent, switch off as below. |
   | `AI_INVALID_RESPONSE` on every request | The model or its output format changed. Check `AI_MODEL`; run the behaviour cases; a prompt or parser fix may be needed. |
   | Every user sees "reached today's limit for everyone" | The application-wide ceiling (`AI_DAILY_LIMIT_GLOBAL`) was reached. Check `ai_request_logs` for the accounts responsible before raising it. It resets at midnight, India time. |

4. **Switch AI off** if it cannot be fixed quickly or costs are rising:

   ```bash
   heroku config:set AI_ENABLED=false --app <app>
   ```

   The assistant then shows its "unavailable" message and everything else keeps working.
5. **Unexpected cost:** switch AI off first, then review usage at the provider, then check `ai_request_logs` for the accounts responsible.

## 6. Authentication and session problems

| Symptom | Likely cause | Action |
| --- | --- | --- |
| Everyone was logged out at once | Dyno restart; sessions are in memory | Expected. Users log in again. |
| "Too many attempts" for one user | Lockout after five failures | Wait 15 minutes, or reset the password from the admin screen. |
| A user forgot their password | | An admin confirms who they are, uses Reset password and gives them the random temporary password in person. A reset never restores the roll number. |
| A new student cannot log in with their roll number | They typed the roll number without the domain; or the domain is not the one they were told; or the account has already been claimed | Check they entered the full email address. Check `STUDENT_EMAIL_DOMAIN` matches the address they were given. If the account is no longer awaiting first login and the student did not change the password, follow the next row. |
| A student says their password was changed before they ever logged in | Someone else used the roll-number temporary password (security exception EX-AUTH-01) | Reset the password and hand the new one over in person. Read the `PASSWORD_CHANGED` audit row for the time and IP address. Check the other accounts still awaiting first login. Record it as an incident. |
| Newly provisioned students have the wrong email domain | `STUDENT_EMAIL_DOMAIN` was wrong when they were provisioned | Correct the setting. That does not rewrite existing accounts; fix those with an owner-approved data migration (database design §8) and tell the students. |
| The application will not start and the log names `STUDENT_EMAIL_DOMAIN` | The variable is missing or is not a host name | Set it and restart. |
| Login succeeds, then every action fails with 403 | CSRF token missing | Hard-refresh the page. If it persists for everyone, check that the `XSRF-TOKEN` cookie is being set and that `api.js` sends the header. |
| Login loops back to the login page in production only | Session cookie not stored | Confirm the site is opened over HTTPS and `server.forward-headers-strategy=native` is set, so the `Secure` cookie is accepted. |
| The only admin is locked out | | Wait for the lock to expire. If the password is lost, the owner takes a backup, then updates that user's `password_hash` directly in the database with a newly generated BCrypt hash and sets `must_change_password` to true. Record the action. |
| Suspected account compromise | | Deactivate the account, reset its password, review the audit log for its actions. |

## 7. Inspecting logs

```bash
heroku logs --tail --app <app>
```

```bash
heroku logs --num 1500 --app <app>
```

- Lines from the application are tagged `app[web.1]`; lines from the router are `heroku[router]`.
- Router error codes worth knowing: `H10` app crashed, `H12` request timeout (30 s), `H14` no web dynos running, `R14` memory quota exceeded.
- To follow one failed request, take the `traceId` from the error the user saw and search the logs for it.
- Heroku keeps only a limited recent window of logs. For anything you need to keep, save it to a private file at the time.
- Logs must not contain secrets. If you find one, treat it as exposed (procedure 8).

## 8. Rotating compromised credentials

Treat a credential as compromised if it appeared in Git, a log, a screenshot, a chat or an issue. **Rotate first, investigate second.**

| Credential | Steps |
| --- | --- |
| **Runtime database password** (`cc_app`) | 1. Signed in as `postgres`, set a new password for `cc_app`. 2. Set `DB_PASSWORD` on Heroku. 3. Confirm health. 4. Update local `.env`. |
| **Migration database password** (`cc_migrator`) | The same steps with `DB_MIGRATION_PASSWORD`. This role can change the schema and the audit triggers, so also review recent schema changes and the audit log. |
| **`postgres` administrative password** | Reset it in the Supabase dashboard. It is not in the application's configuration, so nothing needs redeploying. Review the audit log and the schema. |
| **Supabase service key** | 1. Rotate the key in the Supabase dashboard (**verify** the current procedure there). 2. Set `SUPABASE_SERVICE_KEY`. 3. Test an upload and a preview. |
| **AI API key** | 1. Revoke it in the provider console. 2. Create a new key. 3. Set `AI_API_KEY`. 4. Review provider usage for unexpected spend. |
| **Bootstrap admin password** | Change the admin's password in the application; remove `ADMIN_BOOTSTRAP_PASSWORD`. |
| **A user's password** | Admin resets it; the user must change it at next login. |

Then:

1. If the secret was committed, it remains in Git history even after deletion. Rotation is what makes it harmless. Do not rewrite history without deciding to deliberately.
2. Check the audit log and provider usage for misuse during the exposure.
3. Write a short incident note: what leaked, where, when rotated, what was checked.

## 9. Restoring from backups

**Before restoring, be sure a restore is the right fix.** It discards everything written since the backup.

### Take a backup (before risky changes and before the demo)

```bash
pg_dump "<connection-string>" --format=custom --file=campus-companion-YYYYMMDD.dump
```

Keep the file outside the repository. It contains password hashes; treat it as sensitive.

### Restore

1. Tell users the system is unavailable; scale the dyno down so nothing writes during the restore.

   ```bash
   heroku ps:scale web=0 --app <app>
   ```

2. Choose the source: a `pg_dump` file, or a Supabase managed backup (**verify** what the current plan provides and how to restore it from the dashboard).
3. Restore the dump into the database, connected as `cc_migrator` so that it remains the owner of every object. Afterwards confirm that the grants and policies for `cc_app` are present; a dump taken with default options includes them.

   ```bash
   pg_restore --clean --if-exists --no-owner --dbname "<connection-string>" campus-companion-YYYYMMDD.dump
   ```

4. Confirm `flyway_schema_history` matches the code version you are about to run.
5. Scale the dyno back up and check health.

   ```bash
   heroku ps:scale web=1 --app <app>
   ```

6. Run the smoke test.

### Files

Storage objects are not in the database backup. If files are lost, re-upload the originals from the private demo folder. Rows pointing to missing files can be removed from the admin screen.

## 10. Rolling back a faulty release

1. List releases and note the last good one.

   ```bash
   heroku releases --app <app>
   ```

2. Roll back.

   ```bash
   heroku releases:rollback v<N> --app <app>
   ```

3. Check health and run the smoke test.
4. **If the faulty release included a migration,** the schema is still the new one. If the old code cannot run on it, fix forward instead (see [18-production-plan.md](18-production-plan.md) §12).
5. A rollback also restores that release's config vars. Re-check any variable changed since.
6. Open an issue describing what went wrong, and fix it on a branch before deploying again.

## 11. Before a demo

- [ ] Open the Supabase dashboard; confirm the project is active, not paused.
- [ ] Open the site 10 minutes early to wake the dyno.
- [ ] Log in as the demo student and the demo admin, with their email addresses.
- [ ] Keep one freshly provisioned student, never logged in, to show the first-time password change.
- [ ] Preview one resource and run one AI request.
- [ ] Have `AI_PROVIDER=mock` ready as a fallback if the provider is down on the day. This is a contingency for the demonstration only. It does not replace acceptance with the real provider (OQ-9).
- [ ] Keep a recent backup and the demo PDFs at hand.
