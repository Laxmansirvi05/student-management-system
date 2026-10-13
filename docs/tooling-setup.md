# Antigravity Skills and MCP Setup

> Status: what was actually done on 2026-10-11, and what remains. The inventory with versions and statuses is [mcp-manifest.md](mcp-manifest.md). Environment: macOS on Apple Silicon, 16 GB memory, Antigravity IDE 2.5.5.

The setup was performed from Claude Code. It could edit files and start servers directly, but it could not see Antigravity's Skills or MCP panels. Section 2 lists what the owner must confirm there.

## 1. What was done

| Step | Result |
| --- | --- |
| Documentation changes | Committed as `78d20e4`, now part of `main` |
| Branches | The documentation and tooling work was brought onto `main` on 2026-10-12 and pushed. The two side branches were removed after confirming `main` contained them. `main` is now the only branch |
| Eclipse Temurin JDK 21 | Installed by the owner on 2026-10-12 (21.0.12.1). Verified: Maven reports Java 21 when `JAVA_HOME` points to it. Java 25 remains the default on the `PATH` |
| Maven | Installed with Homebrew: 3.10.0. Homebrew also installed its own OpenJDK 27 and supporting libraries as Maven's dependencies. Java 25 was not touched |
| Java version pin | `.java-version` containing `21` was added. The pin in `pom.xml` and the Maven Wrapper belong to task `T1-01` |
| Global Antigravity MCP configuration | Backed up, then `playwright` and `sequential-thinking` set to disabled. `n8n-mcp` untouched |
| GitHub access | The GitHub CLI is used. GitHub MCP was not configured and no token was written anywhere |
| GSD Core | **Not installed.** It needs Node.js 24; the machine has 22. See section 3 |
| Agency Agents | Four selected skills copied into `.agents/skills/` with a project-rules preamble |
| Ralph Loop | Written as a project skill with an iteration limit. No package installed, nothing started |
| Ruflo | Not installed. Deferred |
| Supabase MCP | Not configured. Blocked until a dedicated project exists |
| Existing project skills | Reviewed against the current rules (two-role database model, email login, AI limits). They already matched. One line was added to `test-and-verify` pointing at the traceability matrix and test IDs |

Nothing was deployed or built, and no application code was written.

## 2. What the owner must confirm inside Antigravity

1. Reload the workspace on `main`.
2. Open the Skills view. All ten skills in the manifest should be listed. Ask the agent to "use the `campus-companion-workflow` skill" and check that it reads `AGENTS.md`.
3. Open the MCP Servers panel and refresh.
   - Context7 should be connected and list `resolve-library-id` and `query-docs`.
   - `playwright` and `sequential-thinking` should show as disabled. If they still start, Antigravity does not honour the `disabled` key in this version; switch them off with the panel's own toggle instead.
4. Decide which of the two Context7 entries to keep (workspace remote, or global `npx`).
5. Tell the agent the results so the manifest can be updated from CONFIGURED BUT NOT VERIFIED to VERIFIED.

## 3. What is waiting on the owner

### Java 21

Done on 2026-10-12. To confirm both versions are present:

```bash
/usr/libexec/java_home -V
```

To use Java 21 in a terminal for this project:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

Java 25 stays installed. `.java-version` already records 21 for tools that read it.

### GSD Core and Node.js

Official project: https://github.com/open-gsd/gsd-core. Package: `@opengsd/gsd-core`. The requested command is:

```bash
npx @opengsd/gsd-core@latest --antigravity --local
```

It was **not** run, because releases 1.11.0 and later require Node.js 24 or newer and this machine has Node.js 22.23.2. Choose one:

| Option | Effect |
| --- | --- |
| A. Install Node.js 24 | A system-wide change. `n8n`, `omniroute`, `vercel` and other global tools would then run on Node.js 24 and should be checked afterwards |
| B. Pin GSD 1.10.0 | The last release that supports Node.js 22 (published 2026-08-08). Two months behind and will not get fixes |
| C. Wait | Use `campus-companion-workflow` and the implementation plan, which already break the work into small verified tasks |

Before running either install: the installer's `--dry-run` reported no file list, so run the real install on a clean working tree and review `git status` and the full diff before keeping anything. Do not pass any permission-bypass option. Note that the README names "Antigravity CLI" as the target, so check after installing that Antigravity IDE actually shows the GSD commands.

### Docker

Integration tests use Testcontainers, which needs a running Docker engine. Colima is installed but stopped. Start it only when those tests are needed:

```bash
colima start
```

and stop it afterwards to free memory:

```bash
colima stop
```

### GitHub repository protections

Still off, as recorded in the implementation plan task `T0-04`: secret scanning, push protection, Dependabot alerts and private vulnerability reporting.

### Supabase

Done on 2026-10-13. The dedicated project `campus-companion` exists, the Data API is switched off, the roles `cc_migrator` and `cc_app` and the schema `app` are created, and the application has started against it. The `opportunity-radar` project must never be used for this project. Supabase's GitHub integration must stay off: the database is changed only by Flyway.

## 4. Using the tools day to day

| Need | Use |
| --- | --- |
| Start any task | `campus-companion-workflow`, then the task in `docs/16-implementation-plan.md` |
| Write backend code | `java-spring-boot-engineering` |
| Tests and evidence | `test-and-verify` |
| Before a commit | `independent-code-review`, and `security-review` for anything security-relevant |
| Challenge a design, threat-model a feature, design API tests, check a gate | The four `agency-` skills, on demand |
| Library documentation | Context7 |
| Issues and CI | The GitHub CLI (`gh`) |
| Browser testing | Switch Playwright on for the task, then off |
| A stubborn small task with an automatic check | `ralph-loop`, only when asked for by name |

Keep the everyday set to the project skills, Context7 and the GitHub CLI. Do not run several agents on overlapping files at once.

## 5. Rules for adding a tool later

1. Confirm the official repository and package, its licence and that it is maintained.
2. Read the installer before running it. Never pipe a remote script into a shell.
3. Prefer a project-local install. Back up any global file before editing it.
4. Start with the fewest permissions; database tools start read-only.
5. Test the real connection, not the presence of a config file.
6. Record it in the manifest with source, version, transport, scope, permissions, how to stop it, and whether it is always on.
7. If it fails, try one safe correction, then switch it off and record the blocker.

## 6. Before task `T1-01`

- [ ] Skills and MCP servers confirmed inside Antigravity (section 2).
- [x] Java 21 installed.
- [ ] GSD decision made (section 3), or option C accepted.
- [x] Tooling changes committed and on `main`.
- [ ] Owner decisions needed before Phase 1 answered: OQ-4, OQ-10, OQ-12, OQ-14, and `T0-05` (see the charter §11 and the implementation plan).
- [ ] GitHub protections switched on (`T0-04`).
- [ ] Owner's approval to start `T1-01`.

A Supabase project and Docker are not needed for `T1-01` itself; they are needed from `T1-02` and `T1-03`.
