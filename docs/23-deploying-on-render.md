# 23. Deploying on Render (free plan, no card)

> Status: Draft v0.1 · Owner: Laxman Sirvi · Last updated: 2026-10-17

How to put the application on the internet with Render's free web service and a free Supabase project. Statements about Render's and Supabase's behaviour come from third-party reports and have not yet been tried on this project; steps marked **Check** are the ones to confirm on the first deployment. The general rules are in [18-production-plan.md](18-production-plan.md).

**What runs where:** the Java application (pages and API) runs on Render from the `Dockerfile`. The database and the uploaded files are on Supabase. The AI assistant runs on the mock provider until the owner chooses a real one.

**Free-plan facts to plan around** (from [18-production-plan.md](18-production-plan.md) §13): the service sleeps after about 15 minutes without traffic and takes about a minute to wake; everyone is logged out when it wakes; memory is 512 MB and the application was measured at about 330 to 380 MB. Open the site a minute before a demonstration.

## 1. Supabase (once)

> **State on 2026-10-18 (checked and set up with the owner's permission).** The project `campus-companion` already exists. It has the roles `cc_app` and `cc_migrator` (neither bypasses Row Level Security), the schema `app` with all 19 tables, and migrations up to V6 applied. It holds 6 demo students and no administrator. The private bucket `learning-resources` was created that day (10 MB limit, PDF and JPEG only, no public policy). So steps 3 and 4 below are done, and what remains is step 5: copying the connection details and the secret key from the dashboard. A security notice remains open for `app.audit_logs_reject_change` (its search path is not fixed); it is low risk and can be fixed in a later migration. Do not use the other project in the account, `opportunity-radar`, for anything here.

1. Create a Supabase project on the free plan. Choose a region near you and save the database password where only you can read it.
2. **Switch the Data API off:** Project settings, Data API (or API), disable it. The application talks to the database directly.
3. **Create the roles and the schema.** In the SQL editor, run the statements in [08-database-design.md](08-database-design.md) §11.7, with two strong passwords of your own in place of `<set-by-owner>`. Keep the two passwords for step 2 below.
4. **Create the storage bucket:** Storage, New bucket, name `learning-resources`, **private** (public switched off). Do not add any policy.
5. **Collect the connection details** (Connect, then the **Session pooler** string, not the Transaction pooler):
   - the pooler host, for example `aws-0-<region>.pooler.supabase.com`;
   - the project reference (the short code in the project URL);
   - the project URL (`https://<project-ref>.supabase.co`);
   - the service key: Project settings, API keys, the **secret** (server) key. It gives full storage access, so it only ever goes into Render's secret fields.

## 2. Render (once)

1. Sign in to Render with your GitHub account. The free web service should not ask for a card; **Check** on the sign-up page.
2. New, **Blueprint**, choose the `student-management-system` repository. Render reads [render.yaml](../render.yaml) and proposes the service `campus-companion` on the Free plan.
3. Fill the values it asks for:

   | Name | Value |
   | --- | --- |
   | `DB_URL` | `jdbc:postgresql://<pooler-host>:5432/postgres?sslmode=require` |
   | `DB_USERNAME` | `cc_app.<project-ref>` |
   | `DB_PASSWORD` | the password you chose for `cc_app` |
   | `DB_MIGRATION_USERNAME` | `cc_migrator.<project-ref>` |
   | `DB_MIGRATION_PASSWORD` | the password you chose for `cc_migrator` |
   | `SUPABASE_URL` | `https://<project-ref>.supabase.co` |
   | `SUPABASE_SERVICE_KEY` | the secret key from step 5 above |

   The values already in the file are: profile `prod`, email domain `gcet.edu.in`, mock AI, bucket `learning-resources`.
4. Apply. Render builds the image (a few minutes) and starts it. On its first start the application creates every table by itself (Flyway), as `cc_migrator`. This is the one time it writes to your Supabase database; it needs your approval, and applying the Blueprint is that approval.

## 3. Check it works

1. Open `https://<service-name>.onrender.com/actuator/health`. Expect `{"status":"UP"}`.
2. Open `https://<service-name>.onrender.com/`. The landing page appears; Log in leads to the login page.
3. In Render, Logs: look for `Started CampusCompanionApplication` and no red errors.
4. **Check** that the page does not redirect in a loop (see the first row below).

## 4. Data and accounts

- **Demonstration with invented students:** in Render, Environment, add `DEMO_SEED_ENABLED=true`, let it restart once so the demo students, subjects, records and questions appear, then **remove the variable**. Their accounts are listed in [demo-and-viva-guide.md](demo-and-viva-guide.md). Use this on a database that will never hold real students.
- **First administrator:** set `ADMIN_BOOTSTRAP_EMAIL` and `ADMIN_BOOTSTRAP_PASSWORD` (a strong password that is not a roll number), restart, log in, change the password, then remove both variables. Without an administrator the admin screens cannot be used.
- **Real students:** follow [22-loading-real-data.md](22-loading-real-data.md) and ADR 0010, in a separate real-data deployment.

## 5. If something goes wrong

| Symptom | Likely cause and fix |
| --- | --- |
| The browser says "redirected you too many times" | Render's proxy connects from an address Tomcat does not trust, so the application does not see HTTPS. Add the variable `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` with the value `.*`, redeploy, and note it in [18-production-plan.md](18-production-plan.md) §4. This trusts forwarded headers from any sender, which is acceptable only because all traffic reaches the service through Render's proxy. |
| The service restarts in a loop and the log names a property | A required value is missing or misspelled: `DB_URL`, the four database login values, or `STUDENT_EMAIL_DOMAIN`. |
| The log shows a database connection or authentication error | Wrong user-name format (it must end in `.<project-ref>`), the transaction pooler was used instead of the session pooler, or a wrong password. |
| The log shows a Flyway error | Do not edit a migration. Read [19-operations-runbook.md](19-operations-runbook.md) §3. |
| The service is slow the first time | It was asleep. Wait about a minute. |
| Everyone is logged out | The service restarted or woke from sleep. Sessions are kept in memory. |
| Photo or PDF upload says "unavailable" | `SUPABASE_URL`, `SUPABASE_SERVICE_KEY` or the bucket name is missing or wrong. |
| Out of memory | Lower the heap with `JAVA_OPTS`, or move to a plan with more memory. |

## 6. Stopping

To stop using it: in Render, suspend or delete the service; in Supabase, pause the project after a final backup ([08-database-design.md](08-database-design.md) §10).
