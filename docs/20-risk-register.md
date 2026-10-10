# 20. Risk Register

> Status: Draft v0.1 · Owner: Laxman Sirvi · Last updated: 2026-10-10

Review this register at every milestone gate. Update the status column, add new risks, and close those that no longer apply.

## Scales

- **Probability and impact:** Low (1), Medium (2), High (3).
- **Severity:** probability × impact. 1 to 2 is Low, 3 to 4 is Medium, 6 to 9 is High.
- **Status:** Open, Mitigating, Accepted, Closed.

The owner is Laxman Sirvi for every risk unless another role is named. "Agent" means the coding agent doing the work, which is responsible for following the mitigation, not for accepting the risk.

## Register

| ID | Risk | P | I | Severity | Mitigation | Detection signal | Contingency | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| R01 | **Scope creep.** New features are added and nothing is finished by the deadline. | 3 | 3 | High (9) | Fixed P0 list; phase gates; new ideas become issues, not code; cut order in the roadmap §9. | A milestone slips; work starts on something not in the PRD. | Apply the cut order; demo with the mock AI provider. | Open |
| R02 | **Deadline unknown or too close** (OQ-5). | 2 | 3 | High (6) | Get the date; build in releases that are each demonstrable. | No date by the end of Phase 1. | Submit R1 (core platform) alone. | Open |
| R03 | **AI API cost** exceeds a student budget. | 2 | 2 | Medium (4) | Low-cost model; input and output caps; per-user and application limits; kill switch; provider spending limit; mock in all tests. | Provider usage dashboard; rising `ai_request_logs` counts. | `AI_ENABLED=false`; revoke the key. | Open |
| R04 | **Incorrect AI answers** mislead students. | 3 | 2 | High (6) | "AI-generated" label on every answer; basis shown; AI rules R5 to R9; never used for scoring or records; official explanation shown first on quiz results. | Behaviour cases fail; wrong answers noticed in the demo. | Tighten the prompt and bump its version; change model; disable the affected feature. | Open |
| R05 | **Unauthorized access to student records** (IDOR, missing role check). | 2 | 3 | High (6) | `/me` endpoints; ownership in queries; deny by default; authorization matrix; generated tests Z1 and Z2; scenarios Z3 to Z14 as a release gate. | A Z-test fails; an endpoint missing from the matrix. | Take the endpoint out of service; fix; add the missing test; review the audit log. | Open |
| R06 | **Student alters marks or attendance.** | 1 | 3 | Medium (3) | No student write endpoints for official data; role checks at two layers; audit log. | Audit entry with a non-admin actor; test Z6 fails. | Correct the data from the audit log's old values; fix; rotate sessions. | Open |
| R07 | **File-upload risks:** malicious or oversized files. | 2 | 2 | Medium (4) | Admin-only upload; size, type, signature and parse checks; server-generated paths; files served from the storage host only; private bucket. | Upload rejections in the log; parser errors. | Delete the file; hide the resource; update PDFBox. | Open |
| R08 | **Database misconfiguration:** RLS not enabled, Data API exposes tables, or bucket public. | 2 | 3 | High (6) | RLS in every migration; repository test checks every table; release checklist verifies the Data API returns nothing and unsigned URLs are refused. | Supabase security advisor warnings; the RLS test fails. | Enable RLS immediately; make the bucket private; rotate keys if exposure is suspected. | Open |
| R09 | **Wrong pooler mode** causes Hibernate prepared-statement errors. | 2 | 2 | Medium (4) | Session pooler specified in the TRD, production plan and `.env.example`. | "prepared statement already exists" errors in the log. | Switch `DB_URL` to the session pooler; restart. | Open |
| R10 | **Deployment failure** on Heroku (build, boot timeout, memory). | 2 | 3 | High (6) | Pin Java in `system.properties`; verify versions in `T1-01`; test the `prod` profile locally; deploy early in Phase 6, not on the last day. | Build fails; `H10`, `R10` or `R14` in the log. | Roll back; follow runbook procedure 1; demo locally if hosting cannot be fixed in time. | Open |
| R11 | **Secret leaked** to GitHub (public repository). | 2 | 3 | High (6) | `.gitignore` for `.env`; secret scanning and push protection; agent rules forbid reading or writing secrets; review every diff. | GitHub secret-scanning alert; secret visible in a diff. | Rotate immediately (runbook procedure 8); review usage. | Open |
| R12 | **Coding agent makes unsafe or unwanted changes** (destructive migration, disabled tests, invented architecture). | 2 | 3 | High (6) | AGENTS.md; AI agent security rules; small tasks; approval gates; CI on every pull request; the owner reviews diffs. | Large diffs; skipped tests; new dependencies; changes outside the task. | Reject the change; revert; restate the task with tighter scope. | Open |
| R13 | **Owner cannot explain the code** in the viva because an agent wrote it. | 2 | 3 | High (6) | Core rules as plain Java; no Lombok or hidden generation; owner reviews and can re-derive each core class; viva guide. | Owner cannot walk through a core service unaided. | Pair through the class line by line; simplify it; rewrite it by hand if needed. | Open |
| R14 | **Free-tier limits:** Supabase project paused or quota reached; dyno asleep at demo time. | 3 | 2 | High (6) | Pre-demo checklist; backup of data and PDFs; verify plan limits (OQ-7). | Dashboard shows paused; first request slow. | Resume the project; wake the dyno; demo locally as a fallback. | Open |
| R15 | **Sessions lost on dyno restart** annoy users or interrupt a test. | 3 | 1 | Medium (3) | Accepted for the first release; documented; database-backed sessions in the P1 backlog. | Users report sudden logouts. | Log in again; retake the practice test. | Accepted |
| R16 | **Version incompatibility** between Java 21, Spring Boot, Flyway, Testcontainers and the Heroku buildpack. | 2 | 2 | Medium (4) | Verify and record versions in `T1-01`; use versions managed by the Spring Boot parent. | Build or startup errors after scaffolding. | Step back to the previous supported release line; record the decision. | Open |
| R17 | **Real personal data** is loaded or pasted. | 1 | 3 | Medium (3) | Synthetic-only rule; privacy notice on the assistant; no bulk import in the first release. | Recognisable real names or roll numbers in data. | Delete the data; review backups; remind contributors. | Open |
| R18 | **Prompt injection** makes the assistant misbehave. | 3 | 1 | Medium (3) | No tools, no secrets and no records in prompts; structured output; text-only rendering; no memory. | Behaviour cases B11, B12, B16 fail. | Tighten the template; bump its version. | Open |
| R19 | **Tests need Docker** and it is unavailable locally or in CI. | 2 | 2 | Medium (4) | CI runners provide Docker; document the requirement; report rather than skip. | Integration tests not executed. | Run them in CI only; never substitute H2. | Open |
| R20 | **Quiz answers leak** to the browser before submission. | 1 | 2 | Low (2) | Separate DTOs without answer fields; test Z13 inspects the JSON. | Z13 fails. | Fix the DTO; retire exposed questions if needed. | Open |
| R21 | **Documents and code drift apart.** | 3 | 2 | High (6) | "Update documentation with code" rule; contract tests against `openapi.yaml`; migrations must match the database design. | A contract test fails; a reviewer finds a mismatch. | Fix whichever is wrong in the same change. | Open |
| R22 | **Documentation overload:** many documents written before any code delays the start. | 2 | 2 | Medium (4) | Documents are drafts; approve Priority 1 documents first; refine the rest when their phase begins. | Phase 1 has not started a week after the documents were written. | Start `T1-01` once documents 01 to 11 are approved. | Open |

## Top risks to watch now

1. **R01 Scope creep** and **R02 Deadline**: the project is large for an assignment.
2. **R13 Explaining the code**: the grade depends on it.
3. **R05 Unauthorized access** and **R08 Database misconfiguration**: the repository and the deployment are public.
4. **R11 Secret leak**: the repository is public.
