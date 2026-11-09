# Audit V2: issue log (completed 2026-11-09; see AUDIT_REPORT_V2.md)

Baseline was `main` at `5c0134e`; final state is `main` at `9d33dca` plus PR #4.

## Baseline evidence
- Local `./mvnw -B verify` with Docker (Colima), Java 21: BUILD SUCCESS. 385 unit (1 skipped: opt-in SampleFilesTest), 347 integration, 0 failures.
- JS tests: 86 pass at the end of the audit.
- Azure: health 200, login 200. Last deployment 2026-10-30 (OneDeploy). Render: health and login timed out after 25 s.

## Issues
| ID | Sev | What | Evidence | Status |
| --- | --- | --- | --- | --- |
| A01 | P0 | CI on main fails on most pushes since 2026-10-23: PlannerIT:133, ResourceReportIT:94. Linux clock has nanoseconds, PostgreSQL stores microseconds, so a returned timestamp changes after saving. Macs hide it. | `gh run view 41341918416 --log-failed` | Open. Planned fix: microsecond clock in `ClockConfig` plus a test |
| A02 | P1 | CI runs only on push to main, so a pull request gets no checks | `.github/workflows/ci.yml` | Open |
| A03 | P1 | Whole-term rule (48 total -> 32 for 65%, skippable = total - that) does not exist. `plan()` only answers "next N in a row"; its arithmetic checked by hand is correct | `static/js/student-attendance.js:28` | Open |
| A04 | P1 | Planner targets are 70, 75, 80, 85: 65 is not offered, and a required percent outside the list gets no row | `student-attendance.js:11` | Open |
| A05 | Decision | Host `ATTENDANCE_REQUIRED_PERCENT` is 75, the audit brief says 65. Which is the college rule? | `az webapp config appsettings` | Owner |
| A06 | Low | `percent` rounds before comparing, so 64.996% counts as 65.00 and not below. Needs more than about 1000 classes at 65% | `AttendanceCalculator.java` | Open |
| A07 | P1 | Hosted schema history shows V23 applied on 2026-11-02, while AGENTS.md says "local only". Who applied it is unknown; Azure's last deployment is 2026-10-30 | Read-only query of `app.flyway_schema_history` | Investigate |
| A08 | Info | `flyway_schema_history` is the one table of 42 in `app` without RLS. No grants to anon, authenticated or public on `app` | Hosted catalog query | Review |
| A09 | Decision | Azure hosts do not have `AI_VISION_ROUTES` set, so pictures and scans will be refused after deploy until it is set | Setting names | Owner |

## Process notes
- The hosted check asked for a read-only session, but `transaction_read_only` came back `off`: the connection pooler dropped the option. Only SELECTs were sent, so nothing changed, but read-only was NOT enforced. Use `begin read only` explicitly next time.
- The Supabase connector in this session lists only careroute projects, a different app. Do not use it.
- Approved by the owner on 2026-11-07: deploy with V23, read-only hosted checks plus pg_dump backup, live AI eval on Groq and Ollama with synthetic data, Render workspace holding campus-companion. Reconfirm the deploy approval on resume given A07.
- Scratch helper: `hosted.sh` in the session scratchpad.



## Final status (2026-11-09)
- A01, A02 fixed: PR #1, commit 4139017. CI green.
- A03, A04 fixed: PR #2, commit e86f243.
- A10 (dependency advisories, found later) fixed and deployed: PR #3, commit 9d33dca, Azure and Render.
- A12 README refresh: PR #4.
- A05, A06, A07, A08, A09, A11, A13, A14, A15: open, see AUDIT_REPORT_V2.md section 3.
- Hosted read-only note: the session did not enforce read-only on the first query; only SELECT was sent.

## Round 2 (2026-11-09 evening)
- B01 dashboard crash for a missing CGPA (19 hosted students affected): PR #9, deployed.
- B02 seven unindexed foreign keys: PR #8 (V24), applied on the hosted database.
- B03 stale documents removed: PR #7.
- B04 Dependabot alerts, security updates, secret scanning and push protection enabled.
- A05 closed (policy decided). PDF and Word reading removed: PR #6.
