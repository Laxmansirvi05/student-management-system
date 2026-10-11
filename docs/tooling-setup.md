# Antigravity Skills and MCP Setup

> Intended environment: macOS Apple Silicon, 16 GB memory. Review every command before running. Do not install global packs or autonomous hooks by default.

## 1. Before installing

From the repository root:

```bash
git status --short
git branch --show-current
java -version
git --version
node --version
npm --version
```

Use Java 21 for the application. Node.js is only tooling/runtime support for some MCP packages and installers; it is not an application backend. Don't install extra packages if the needed runtime is already present.

Do not create `.env` until a dedicated Campus Companion Supabase project is approved. Never reuse Opportunity Radar credentials or database.

## 2. Workspace skills included here

Antigravity discovers project skills in `.agents/skills/<skill-folder>/SKILL.md`. Restart/reload the workspace or refresh skills after pulling this branch. Inspect Customizations/Skills and verify these are discoverable:

- `campus-companion-workflow`
- `java-spring-boot-engineering`
- `independent-code-review`
- `security-review`
- `test-and-verify`

These are project-local and version-controlled. They complement, rather than replace, `AGENTS.md`, `SECURITY_RULES.md`, and the numbered docs.

## 3. Get Shit Done (GSD)

Official project: https://github.com/gsd-open/get-shit-done-redux

1. Read the current upstream README and security notes first.
2. In the Antigravity terminal, run the official interactive installer:

   ```bash
   npx @opengsd/get-shit-done-redux@latest
   ```

3. Select Antigravity/Gemini-compatible setup if offered, prefer project/workspace-local installation where supported, and choose a standard/core planning profile rather than an unnecessary full set of skills.
4. Do not enable permission bypasses such as `--dangerously-skip-permissions`. Keep human approval for destructive or credential-sensitive actions.
5. Restart Antigravity and verify the GSD commands/skills appear. Have it read `AGENTS.md` and the implementation plan before generating a plan. Use GSD as the primary orchestrator; don't have Ruflo orchestrate the same tasks concurrently.

If the installer does not offer an Antigravity-compatible target, stop and report it. Do not copy raw Claude-only command files into Antigravity.

## 4. Code review skill

The repository includes an independent review skill. Use it before every meaningful PR/merge. External skills may be added only after reviewing their source and comparing them to the project skill to avoid duplicate instructions. A review must identify findings with file/line, severity, reproduction, impact and recommended fix.

## 5. Agency Agents (selective, not full global install)

Official project: https://github.com/msitarzewski/agency-agents

1. Read the upstream Antigravity integration documentation and inspect the installer before executing it.
2. Do not run the full global roster installer as the default; it can install a large number of skills globally.
3. Use only selected specialist skills that add distinct value: backend architect, security reviewer, QA/test engineer, and reality checker. Prefer copies within this workspace's `.agents/skills/` after reviewing their content and license.
4. Avoid installing agents that duplicate the Java, code-review, security and QA skills already committed here.
5. Verify each copied skill's frontmatter and confirm that the actual skill appears in Antigravity.

## 6. Minimal MCP baseline

Antigravity IDE's MCP Servers UI can be used to install and inspect connectors. The workspace file `.agents/mcp_config.json` contains only the remote Context7 endpoint; no keys are committed. Open the MCP Servers panel, refresh, and verify Context7 is connected. If the endpoint requires an account/key in your environment, add it through the supported local configuration/secrets mechanism, never in a committed file.

Add the GitHub MCP/connector through Antigravity's MCP Store when needed, and grant only the repository permissions required. Confirm repository access before asking the coding agent to create issues or PRs.

### Playwright (on demand)

Official instructions: https://github.com/microsoft/playwright-mcp

Don't add it to the always-on config by default. When browser QA begins, install it through Antigravity's MCP Store or use its current official MCP setup instructions. The official local command uses Node.js 20 or newer and `@playwright/mcp@latest`. Verify startup and browser availability using a harmless test page.

### Supabase MCP (blocked until a project exists)

A connected Supabase account currently exposes an `opportunity-radar` project, which is not this project. Do not connect, query, migrate or mutate it on behalf of Campus Companion.

After the owner creates a dedicated Campus Companion Supabase project:
- Configure the MCP with that exact project reference only.
- Begin read-only.
- Enable only necessary tool groups.
- Verify that project name/ref before any operation.
- Never commit the Supabase access token or database credentials.
- Request separate approval before migrations or any mutation.

## 7. Ruflo

Official project: https://github.com/ruvnet/ruflo

Treat Ruflo as an optional experiment, not a prerequisite for the first implementation. Its current package describes a broad CLI/MCP/orchestration surface and much of its published workflow is oriented around other agent runtimes. Before installing it:
1. Verify current Antigravity integration support in the official repo.
2. Inspect the installer and the exact workspace changes it proposes.
3. Test in a separate temporary workspace/branch first.
4. Avoid starting a daemon/swarm or enabling autonomous hooks for the main project.
5. Compare its actual benefit against GSD + selected Agency Agents.
6. Only adopt it if the test demonstrates compatibility without excessive memory/context overhead.

Do not paste remote install scripts directly into a shell using `curl | bash`.

## 8. MCP verification checklist

For every installed tool, record:
- name and official source;
- installed version and transport;
- project/global scope;
- permissions granted;
- start/stop or disable method;
- successful health check or harmless read operation;
- resource and security risks;
- whether always-on or on-demand.

Never report a tool as installed merely because a config file exists. Confirm Antigravity lists it, connects successfully, and exposes the expected tools.

## 9. After tooling setup

1. Review and resolve the open owner decisions listed in the project charter and implementation plan.
2. Create the dedicated Campus Companion Supabase project after the owner checks organization/region/pricing.
3. Start with task `T1-01` only after the documentation gate is approved.
4. Build the empty Spring Boot/Maven skeleton and verify it before adding database connectivity or features.
5. Keep each task small; run tests, code review and security review before progressing.
