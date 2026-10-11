# MCP and Agent Tooling Manifest

> Status: Initial proposal. Verify in the Antigravity UI/CLI after setup. This file is the project-level source of truth for tool selection.

## Policy

- Keep the always-on set small for an Apple Silicon Mac with 16 GB memory.
- Use one main planner/orchestrator at a time. GSD is the proposed primary planning workflow.
- Keep browser testing, database mutation, graph indexing and multi-agent orchestration on demand.
- Never commit API keys, OAuth tokens, database URLs with credentials, local home paths, or machine-specific secrets.
- Never configure this project against the existing `opportunity-radar` Supabase project. Wait for a dedicated Campus Companion project and an owner-approved project reference.
- Use the Antigravity MCP Store where possible for managed connectors. Workspace config must contain no secret values.

## Proposed baseline

| Tool | Purpose | Scope | Activation | Status |
|---|---|---|---|---|
| Context7 | Version-aware library documentation | Workspace remote MCP | Always-on if reachable; no key is committed | Configured in `.agents/mcp_config.json`; verify in Antigravity |
| GSD (Get Shit Done) | Spec-driven planning and small task execution | Project/local skill install where supported | Primary orchestrator | Not installed from this repository; follow `docs/tooling-setup.md` |
| GitHub MCP/connector | Repositories, issues, PRs, CI | Authenticated Antigravity connection | On demand | Install/connect via Antigravity MCP Store; use least permissions |
| Project skills | Project rules, Java implementation, code review, security review, tests | `.agents/skills/` | Automatic or invoke by skill name | Committed in this branch |
| Agency Agents | Specialist backend architect, security, QA/reality-check roles | Selective workspace copies | On demand; use only selected roles | Setup instructions provided; don't install full global roster |
| Playwright MCP | Browser end-to-end testing | Local MCP process | On demand during UI QA | Not in default MCP config |
| Chrome DevTools MCP | Console/network/performance debugging | Local/managed MCP | On demand | Not configured |
| Supabase MCP | Inspect project schema, migrations and logs | Strictly scoped to dedicated Campus Companion project | Read-only first | Blocked until a separate project ref is approved |
| Graphify | Code graph and impact analysis | Workspace | On demand after meaningful code exists | Deferred |
| Sequential Thinking | Complex decomposition/debugging | Managed or local MCP if needed | On demand | Deferred if current agent reasoning is sufficient |
| Ruflo | Swarm, memory and orchestration | Separate experiment workspace first | Off by default | Deferred pending Antigravity compatibility and resource validation |
| Reticle, Ponytail, OmniRoute, Memory MCP, Filesystem MCP, Fetch MCP | Additional verification/rules/routing/memory/file/research capability | Varies | Only if a demonstrated gap exists | Not required for first setup |

## Safety gates

1. All MCP endpoints and packages must be checked against their official source before install.
2. Do not run unreviewed remote shell scripts.
3. Don't give a database MCP write access during initial inspection.
4. No production database writes, migrations, account provisioning, secret changes or deployments without explicit owner approval.
5. Verify each MCP by checking its status and invoking a small, non-mutating health/read operation.
6. If a tool fails verification, disable it and report the exact blocker; do not pretend it works.
