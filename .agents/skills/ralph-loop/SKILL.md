---
name: ralph-loop
description: Optional, on-demand bounded iteration for one small Campus Companion task with explicit acceptance criteria. Runs verify-fix cycles up to a fixed limit, then stops and reports. Never start it unless the owner asks for it by name.
---

# Ralph Loop (bounded iteration)

A written procedure, not an installed program. It starts nothing in the background and grants no extra permissions.

## When to use

Only when the owner explicitly asks for a Ralph loop on a named task. Use it for one small, well-specified task from `docs/16-implementation-plan.md` where the check is automatic (a test command). Do not use it for design work, for anything touching security rules, or while another workflow is driving the same task.

## Before the first iteration

State these and get the owner's confirmation:

1. The task ID and its acceptance criteria, copied from the implementation plan.
2. The single verification command (for example `./mvnw test -Dtest=AttendanceServiceTest`).
3. The files that may be changed. Anything else is out of bounds.
4. The iteration limit. Default 5, maximum 8.

## Each iteration

1. Read the notes file (below) and the last verification output.
2. Make one small change aimed at the first failing check.
3. Run the verification command and record the exact result.
4. Append to `.agents/ralph/<task-id>.md`: iteration number, what changed, command, result, next step. This file is the memory between iterations.

## Stop conditions

Stop immediately, and report, when any of these is true:

- The verification command passes and every acceptance criterion is met.
- The iteration limit is reached.
- The same failure appears in two consecutive iterations with no new idea.
- Progress would need a file outside the agreed list, a new dependency, a schema or API change, or an open owner decision.
- A rule in `SECURITY_RULES.md` or `AI_AGENT_SECURITY_RULES.md` would have to be broken.

## Never

- Disable, skip, delete or weaken a test or a security control to get a pass.
- Use any permission bypass or unattended mode.
- Run destructive commands, touch a hosted database, deploy, commit or push. Each needs the owner's approval, as always.
- Run alongside GSD or any other orchestrator on the same task.

## Final report

Use the report format in `AGENTS.md` section 13, and add: iterations used out of the limit, the result of each, and, if it did not pass, the blocker in one sentence. A truthful "did not pass in 5 iterations" is a valid outcome.
