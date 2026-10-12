# Contributing

How work is done in this repository, for the owner, any other developer, and coding agents. Agents must also follow [AGENTS.md](AGENTS.md) and [AI_AGENT_SECURITY_RULES.md](AI_AGENT_SECURITY_RULES.md).

## 1. Before you start

1. Find or create a GitHub issue for the work. One issue per task in the [implementation plan](docs/16-implementation-plan.md), titled with the task ID.
2. Read the documents listed for that area in [AGENTS.md](AGENTS.md#4-required-reading).
3. If the work is not in the [PRD](docs/02-PRD.md), discuss it in an issue first. Do not build it.

## 2. One branch: `main`

Decided by the owner on 2026-10-12: this repository has a single branch, `main`. There are no feature branches and no pull requests.

- Every change is committed directly to `main`, one task per commit.
- `main` must build and pass its tests at every commit. Never commit work that is half done or failing.
- Do not create other branches, locally or on GitHub, unless the owner asks for one.
- History on `main` is never rewritten: no force-push, no rebase, no amending a pushed commit.

Because nothing separates a change from the public repository once it is pushed, the review that a pull request would have given happens **before the commit** (section 6).

## 3. Commits

Format:

```
<type>(<scope>): <summary in the imperative, under 72 characters>

<optional body: what changed and why>

Refs #<issue-number>
```

| Type | Use for |
| --- | --- |
| `feat` | New behaviour |
| `fix` | Bug fix |
| `docs` | Documentation only |
| `test` | Tests only |
| `refactor` | Code change with no behaviour change |
| `chore` | Build, configuration, dependencies |
| `db` | A migration |

Scope is the feature package: `auth`, `attendance`, `quiz`, `ai`, and so on.

Example:

```
feat(attendance): calculate subject-wise percentage

Adds AttendanceService with HALF_UP rounding to two decimals and the
below-required flag. Subjects with no classes return a null percentage.

Refs #17
```

Rules:

- One logical change per commit. Do not mix a feature with unrelated formatting.
- Never commit secrets, `.env`, build output or real data.
- Do not amend or force-push commits that others may have pulled.

## 4. Code formatting

- Java follows Google Java Style, applied with Spotless.

  ```bash
  ./mvnw spotless:apply
  ```

- 4-space indentation in HTML, CSS and JavaScript; 2 spaces in YAML and JSON.
- No trailing whitespace; files end with a newline.
- Do not reformat files you are not otherwise changing.

Standards and naming conventions are in [AGENTS.md](AGENTS.md#7-coding-standards).

## 5. Tests

- New logic comes with tests. A bug fix starts with a test that fails because of the bug.
- Every endpoint has tests for success, validation failure, 401, 403 and, where ownership applies, 404.
- Run everything before asking for a commit:

  ```bash
  ./mvnw verify
  ```

- Never disable, skip or weaken a test to make the build pass.

What to test at each level is in [docs/17-testing-strategy.md](docs/17-testing-strategy.md).

## 6. Before a commit

Each task goes through these steps, in order, while the change is still uncommitted:

1. **Verify.** Run `./mvnw verify` and read the result. A failing or skipped check stops here.
2. **Review.** Run the `independent-code-review` skill on the full diff, and the `security-review` skill if the change touches authentication, authorization, uploads, AI, secrets or migrations. Fix what they find, then verify again.
3. **Report.** Give the owner the report from [AGENTS.md](AGENTS.md#13-how-to-report): what changed, what was run and its actual result, what was not verified.
4. **Owner approves.** The owner reads the diff and says to commit. An agent never commits or pushes without that.
5. **Commit** to `main` with a message in the format of section 3. One task, one commit.
6. **Push** only when the owner says so. After the push, check that CI on `main` is green; if it is not, fixing it is the next task and nothing else starts first.

Keep each task small enough to read in one sitting.

## 7. Review

- The owner reviews every change before it is committed. An agent never approves its own change.
- The owner must understand each change well enough to explain it. If a change cannot be explained simply, it is simplified before it is committed.
- Changes touching authentication, authorization, uploads, AI, secrets or migrations get a line-by-line review.
- The review checks: does it meet the acceptance criteria, is it tested, does it follow the layering, does it match the documents, is anything unrelated included.
- A mistake found after a commit is fixed with a new commit. It is not hidden by rewriting history.

## 8. Documentation updates

A change is incomplete without its documentation. Update in the same commit:

| If you change | Update |
| --- | --- |
| An endpoint | [openapi.yaml](docs/openapi.yaml), [09-api-contract.md](docs/09-api-contract.md), [10-authorization-matrix.md](docs/10-authorization-matrix.md) |
| The schema | [08-database-design.md](docs/08-database-design.md) and a new migration |
| A requirement or behaviour | [02-PRD.md](docs/02-PRD.md) |
| A screen or flow | [06-app-flow.md](docs/06-app-flow.md), [07-ui-ux-spec.md](docs/07-ui-ux-spec.md) |
| An AI prompt or behaviour | [13-AI-rules.md](docs/13-AI-rules.md), the prompt version |
| Configuration | [.env.example](.env.example), [18-production-plan.md](docs/18-production-plan.md) |
| A significant technical choice | A new record in [docs/decisions](docs/decisions/README.md) |
| A document's approval state | The status table in [README.md](README.md) |

Keep one source of truth. Link to a document; do not copy from it.

## 9. Dependencies

Adding, removing or upgrading a dependency, plugin, GitHub Action or vendored library needs the owner's approval before the change is made. In the issue, state:

1. What it is for and why existing tools are not enough.
2. Its licence (Apache 2.0, MIT, BSD or EPL only).
3. That it is actively maintained and has no known serious vulnerabilities.

## 10. Definition of done

A task is done when:

- [ ] Its acceptance criteria in the implementation plan are met.
- [ ] Tests exist for the new behaviour, including the failure and access-denied cases.
- [ ] `./mvnw verify` passes locally before the commit, and CI on `main` is green after the push.
- [ ] Code follows the layering, naming and formatting rules.
- [ ] Documentation is updated.
- [ ] No security rule is broken; no secrets or real data are included.
- [ ] The owner reviewed the diff and approved the commit.
- [ ] The issue is closed with a note of anything left for later.

## 11. Issues

Use the templates when opening an issue: feature, bug, technical debt, security hardening, documentation.

Labels: `feature`, `bug`, `tech-debt`, `security`, `docs`, plus a priority (`P0`, `P1`, `P2`) and, where it applies, `needs-owner`.

GitHub Issues is the only task tracker. Do not keep a separate task list in a Markdown file.

## Reporting security problems

**Do not open a public issue for a vulnerability.** A public report can expose an exploitable weakness, and this repository is public.

- Report privately through GitHub's "Report a vulnerability" option on the repository's Security tab (the owner must enable private vulnerability reporting), or contact the owner directly.
- Include what you found, how to reproduce it, and what it could expose.
- If a secret has been exposed, say where, but do not repeat its value.

The public "Security hardening" issue template is only for improvements that reveal no exploitable detail, such as adding a missing security header.
