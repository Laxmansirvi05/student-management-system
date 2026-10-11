---
name: independent-code-review
description: Independently review changes for bugs, regressions, architecture drift, security failures, missing tests, and maintainability before a commit or merge.
---

# Independent Code Review

Act as a reviewer, not the original implementer. Do not edit files during the review unless the user separately asks for fixes.

## Review order
1. Read the task and acceptance criteria.
2. Inspect the complete diff, surrounding code, and relevant project documents.
3. Look first for correctness bugs, data loss, authorization bypass, unsafe database operations, security regressions, and behavior changes.
4. Check boundary conditions, missing/null data, concurrent requests, transaction boundaries, timeout/error paths, and backwards compatibility.
5. Check adherence to Java/Spring layering, API/OpenAPI contract, database design and UI state conventions.
6. Verify tests meaningfully cover both allowed and denied cases.
7. Run targeted checks if permitted and report exact results.

## Output format
- Findings first, ordered Critical / High / Medium / Low.
- Each finding must include file and line, the scenario that triggers it, user/business impact, and a concrete recommended fix.
- Distinguish verified defects from questions or suggestions.
- Then list tests/checks run, checks not run, residual risks, and an overall recommendation: Request changes / Approve with notes / Approve.
- Do not praise or restate code unless useful to explain a finding.
- Never claim a test passed if it was not run.
