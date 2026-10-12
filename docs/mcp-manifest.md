# MCP and Agent Tooling Manifest

> Status: actual results of the tooling pass on 2026-10-11. This file is the project's inventory of agent tooling. Update it whenever a tool is added, removed, switched on or switched off.

The setup was carried out from Claude Code, not from inside Antigravity. Configuration files were edited and servers were tested by starting them directly. **Nothing here has been confirmed in Antigravity's own Skills or MCP panels.** Items that need that confirmation say so.

## Status meanings

| Status | Meaning |
| --- | --- |
| VERIFIED | Exercised and seen working, with the evidence stated |
| CONFIGURED BUT NOT VERIFIED | Files are in place; not yet seen working inside Antigravity |
| DEFERRED | Deliberately not set up yet |
| BLOCKED | Cannot proceed until something outside this repository changes |
| REQUIRES OWNER APPROVAL | Waiting for a decision or an action only the owner can take |

## Policy

- Keep the always-on set small: this is an Apple Silicon Mac with 16 GB of memory.
- One orchestrator drives a task at a time.
- Browser, database, graph and multi-agent tools are switched on for a task and off again afterwards.
- No key, token, database URL or home-directory path is committed.
- Never point this project at the `opportunity-radar` Supabase project.
- Do not run a remote install script without reading it first.

## Environment (checked 2026-10-11)

| Item | Version | Status |
| --- | --- | --- |
| macOS, hardware | 26.5, arm64, 16 GB | VERIFIED |
| Antigravity IDE | 2.5.5 | VERIFIED (installed; not driven by this pass) |
| Git | 2.39.5 | VERIFIED |
| GitHub CLI | 2.97.0, logged in, admin on the repository | VERIFIED |
| Node.js / npm | 22.23.2 / 10.9.8 | VERIFIED |
| Java on `PATH` | Temurin 25.0.4 | VERIFIED. **Not the project's version** |
| Java 21 (Temurin) | 21.0.12.1, installed by the owner on 2026-10-12 | VERIFIED: `/usr/libexec/java_home -v 21` resolves to it. It is not the default; set `JAVA_HOME` for this project |
| Maven | 3.10.0 (Homebrew) | VERIFIED: with `JAVA_HOME` set to Java 21, `mvn -version` reports Java 21.0.12.1. Without it, Maven uses another JDK |
| Docker | CLI 29.8.0; Colima 0.10.3 **not running** | BLOCKED for Testcontainers until Colima is started |

## Skills

All skills are project-local, in `.agents/skills/<name>/SKILL.md`. Every file has valid `name` and `description` front matter and the name matches its folder (checked by script). None has been confirmed in Antigravity's Skills panel.

| Skill | Source | Purpose | Use | Status |
| --- | --- | --- | --- | --- |
| `campus-companion-workflow` | This repository | Project rules, reading order, stop conditions | Always | CONFIGURED BUT NOT VERIFIED |
| `java-spring-boot-engineering` | This repository | Java and Spring Boot practices for this project | When writing backend code | CONFIGURED BUT NOT VERIFIED |
| `independent-code-review` | This repository | Review of a change before it is committed, with severity, file and line, scenario, impact and fix | Before every commit | CONFIGURED BUT NOT VERIFIED |
| `security-review` | This repository | Security checklist tied to `SECURITY_RULES.md` | Before committing security-relevant changes | CONFIGURED BUT NOT VERIFIED |
| `test-and-verify` | This repository | Tests from acceptance criteria; real command output | With every task | CONFIGURED BUT NOT VERIFIED |
| `ralph-loop` | This repository (written here; no package installed) | Bounded verify-fix iteration, default limit 5 | Only when the owner asks for it by name | CONFIGURED BUT NOT VERIFIED |
| `agency-backend-architect` | Agency Agents, `engineering/engineering-backend-architect.md` | Design second opinion | On demand | CONFIGURED BUT NOT VERIFIED |
| `agency-appsec-engineer` | Agency Agents, `security/security-appsec-engineer.md` | Threat modelling before building | On demand | CONFIGURED BUT NOT VERIFIED |
| `agency-api-tester` | Agency Agents, `testing/testing-api-tester.md` | API test-case design | On demand | CONFIGURED BUT NOT VERIFIED |
| `agency-reality-checker` | Agency Agents, `testing/testing-reality-checker.md` | Evidence check at a gate | On demand | CONFIGURED BUT NOT VERIFIED |

Agency Agents: https://github.com/msitarzewski/agency-agents, commit `f99f6aa910a442b0197b768ce0ea7751e35e2060`, MIT License. Only these four files were copied; the upstream installer, which installs the whole roster globally, was not run. Each copy starts with a preamble stating that this repository's rules win, because the upstream text is generic and mentions things this project does not allow (microservices, JWT, Redis, Kubernetes).

**To remove a skill:** delete its folder under `.agents/skills/`.

## Orchestrators

| Tool | Source and version | Status | Detail |
| --- | --- | --- | --- |
| GSD Core | https://github.com/open-gsd/gsd-core, npm `@opengsd/gsd-core` 1.16.0 | **REQUIRES OWNER APPROVAL. Not installed** | Every release since 1.11.0 requires Node.js 24 or newer; this machine has Node.js 22.23.2 and npm reports the engine as unsupported. Options: install Node.js 24 (a system-wide change), or pin GSD 1.10.0, the last release that supports Node.js 22 (2026-08-08). The installer's `--dry-run` listed no file changes to review. Its README names "Antigravity CLI", not Antigravity IDE |
| Ralph Loop | Project skill above | CONFIGURED BUT NOT VERIFIED | Off unless requested. No autonomous loop was started |
| Ruflo | https://github.com/ruvnet/ruflo, npm `ruflo` 3.56.4 | DEFERRED. Not installed | Its README does not mention Antigravity. Its package is published from the `claude-flow` repository. Needs an isolated trial before any use here |

Until GSD is installed, `campus-companion-workflow` plus `docs/16-implementation-plan.md` is the planning workflow. GSD and Ruflo must never drive the same task together.

**Correction:** the earlier version of the setup notes named `gsd-open/get-shit-done-redux` and `@opengsd/get-shit-done-redux`. The first is an unrelated zero-star fork and the second is a superseded package name. The original `gsd-build/get-shit-done` repository is archived and redirects to `open-gsd/gsd-core`.

## MCP servers

| Server | Source and version | Transport | Scope | Use | Status |
| --- | --- | --- | --- | --- | --- |
| Context7 | https://github.com/upstash/context7, server 4.3.0 | Remote HTTP, `https://mcp.context7.com/mcp` | Workspace: `.agents/mcp_config.json` | Always on | VERIFIED at protocol level: `initialize` returned Context7 4.3.0 with HTTP 200. Not confirmed in Antigravity |
| Context7 (second entry) | npm `@upstash/context7-mcp` 4.3.0 | stdio through `npx` | Global Antigravity config | Always on | VERIFIED at protocol level: `initialize` and `tools/list` returned `resolve-library-id` and `query-docs`. **Duplicate** of the workspace entry; one of the two can be removed |
| Playwright | https://github.com/microsoft/playwright-mcp, npm `@playwright/mcp` 0.0.83 | stdio through `npx` | Global | On demand, once a UI exists | CONFIGURED BUT NOT VERIFIED. Set to `"disabled": true` on 2026-10-11. The package starts (`--version` gave 0.0.83). No browser test was run. Whether Antigravity honours the `disabled` key must be confirmed in its MCP panel |
| Sequential Thinking | npm `@modelcontextprotocol/server-sequential-thinking` 2026.8.31 | stdio through `npx` | Global | On demand | CONFIGURED BUT NOT VERIFIED. Set to `"disabled": true` on 2026-10-11 |
| n8n-mcp | npm `n8n-mcp` | stdio through `npx` | Global | Not used by this project | Left untouched at the owner's instruction |
| GitHub MCP | https://github.com/github/github-mcp-server | | | | DEFERRED. The MCP Store cannot be operated from outside Antigravity, and no token was placed in any file. The GitHub CLI covers repository, issue, pull-request and CI work for now |
| Chrome DevTools MCP | https://github.com/ChromeDevTools/chrome-devtools-mcp, npm `chrome-devtools-mcp` 1.10.1 | | | | DEFERRED until there is a UI to debug |
| Supabase MCP | https://github.com/supabase/mcp | | | | **BLOCKED** until the owner creates a dedicated Campus Companion project and approves its exact project reference. Then read-only first |
| Graphify | | | | | DEFERRED until the codebase is large enough to need it |
| Reticle, Ponytail, Memory MCP, Filesystem MCP, Fetch MCP, a general database MCP | | | | | Not installed; no demonstrated need |
| OmniRoute | npm `omniroute` 3.8.50, already installed globally before this pass | | | | Not configured for this project and not removed |

### Global configuration change

- File: `~/.gemini/antigravity/mcp_config.json`
- Backup: `~/.gemini/antigravity/mcp_config.backup-2026-10-11.json`
- Change: `"disabled": true` added to the `playwright` and `sequential-thinking` entries. Nothing else was altered; the entries, their arguments and `n8n-mcp` are intact.
- To switch a server back on: remove that line, or set it to `false`, and refresh the MCP panel.
- To undo everything: copy the backup over the file.

### Health checks

| Check | How to run it |
| --- | --- |
| Context7 remote | Send an MCP `initialize` request to `https://mcp.context7.com/mcp`; expect HTTP 200 and `serverInfo.name` of `Context7` |
| Context7 stdio | `npx -y @upstash/context7-mcp`, send `initialize` then `tools/list`; expect two tools |
| Playwright package | `npx -y @playwright/mcp@latest --version` |
| Any server, inside Antigravity | Open the MCP Servers panel, refresh, and confirm the server shows as connected with its tools listed |

## Known risks and limits

1. Nothing has been seen working inside Antigravity. The first step after this pass is to reload the workspace and check both panels.
2. Java 25 is still the default `java` on the `PATH`. Builds for this project must set `JAVA_HOME` to Java 21 until the Maven Wrapper and `pom.xml` enforce it in `T1-01`.
3. Colima is stopped, so integration tests that need Docker cannot run yet.
4. GSD cannot be installed as requested on Node.js 22.
5. Context7 is configured twice.
6. The four Agency Agents skills are long (260 to 546 lines). They cost context only when invoked.
7. Whether `"disabled": true` is honoured by Antigravity 2.5.5 is unconfirmed.
