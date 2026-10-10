# 14. AI Security and Privacy

> Status: Draft v0.1 · Owner: Laxman Sirvi · Last updated: 2026-10-10

This document protects the application and its users when it processes prompts, files and AI responses at runtime. It concerns the **in-product** assistant. Limits on coding agents are in [AI_AGENT_SECURITY_RULES.md](../AI_AGENT_SECURITY_RULES.md).

## 1. Principle

> The AI model is an untrusted text generator sitting outside the trust boundary. It receives the minimum needed, has no access to anything, and everything it returns is validated before use.

The assistant never has database access "because it needs to answer a question". It answers from the text in the request and its general knowledge only.

## 2. Threats and controls

| # | Threat | Control |
| --- | --- | --- |
| A1 | Prompt injection in student input | §3, §4 |
| A2 | Injection hidden in a pasted or uploaded document | §3, §4, §8 |
| A3 | Leaking system instructions or keys | §4, §6 |
| A4 | Leaking other users' data | §5 |
| A5 | Personal data sent to a third party | §5 |
| A6 | Malicious content in AI output reaching the browser | §7 |
| A7 | Wrong or harmful academic content | §7, [13-AI-rules.md](13-AI-rules.md) |
| A8 | Cost exhaustion and abuse | §9 |
| A9 | Provider outage affecting the application | §10 |
| A10 | Malicious file attacking the PDF parser | §8 |
| A11 | Sensitive text retained in logs | §11 |

## 3. Prompt injection

Injection cannot be fully prevented by wording, so the design limits what a successful injection could achieve:

1. **No tools.** The model cannot call functions, run queries, fetch URLs or trigger actions. Its only effect is the text it returns.
2. **No secrets or records in the prompt.** There is nothing sensitive to extract.
3. **Structured output.** A response that is not the expected JSON is rejected, so "reply only with HACKED" produces an error, not a displayed message.
4. **Text-only rendering.** Whatever survives is shown as plain text.
5. **Single request.** No memory means an injection cannot persist to another request or another user.

The worst realistic outcome is a low-quality or off-topic answer for the student who sent the input.

## 4. Separation of instructions and content

- Instructions go in the provider's **system** field. They come only from versioned template files in the repository.
- Everything from the user goes in the **user** message, wrapped in clearly named delimiters, for example:

  ```
  <student_topic>...</student_topic>
  <student_material>...</student_material>
  ```

- The system instructions state that text inside these delimiters is material to work on and that instructions inside it must not be followed.
- User text is never concatenated into the system field.
- Before sending, the backend removes any occurrence of the delimiter tags from user text so the wrapper cannot be closed early.
- Subject and unit names come from the database by ID, not from the request, and are inserted as data.

## 5. Privacy and data minimisation

**Sent to the AI provider:** the student's typed topic or pasted text for that request; subject name and unit title; the instructions.

**Never sent:**

- Names, roll numbers, usernames, email addresses or phone numbers from the database.
- Marks, attendance, results or any academic record of anyone.
- Session identifiers, internal IDs, IP addresses.
- Other students' content.

Additional rules:

1. The provider request carries **no user identifier**. Quotas are tracked on our side.
2. The UI states near the input: "Do not paste personal information. Text you submit is sent to an AI service."
3. The AI service classes have no dependency on student, attendance, marks or results repositories. This is checked in code review and by an architecture test.
4. Before choosing a provider (OQ-1), confirm its data-handling terms, in particular whether API inputs are used for training and how long they are retained. Record the answer in [ADR 0005](decisions/0005-ai-integration-behind-java-interface.md).
5. Because all application data is synthetic, the main real-world privacy exposure is whatever a student chooses to paste. The UI notice addresses that.

## 6. Keys and provider access

- `AI_API_KEY` exists only in server configuration. It is never sent to the browser, logged, or included in an error.
- The key is used by one class (the provider adapter).
- Set a spending limit in the provider's console where one is available, as a backstop independent of our code.
- The key is rotated if exposed, and after the demo period ends.
- The provider's base URL is fixed in configuration. No request field can change the destination (prevents request forgery through the server).

## 7. Output validation and safe rendering

Every response goes through `AiResponseParser`:

1. Extract the JSON object. Anything that does not parse is `AI_INVALID_RESPONSE`.
2. Map it to a typed Java record. Unknown fields are ignored; missing required fields fail.
3. Enforce field limits (lengths, list sizes). Over-long fields are truncated at a sentence boundary or the response is rejected, as specified per field in the handoff.
4. Apply feature rules (for example the MCQ checks in [12-AI-features-spec.md](12-AI-features-spec.md) §5).
5. Strip control characters. No HTML is interpreted at any point.
6. Add the fixed `notice` text. The notice is written by our code, not by the model.

In the browser, AI text is inserted with `textContent` only. Flowchart syntax (P1) is built by Java from a validated graph, never taken from the model.

AI output is **never**:

- used to decide access or identity,
- written to official records,
- used to score a test,
- executed, evaluated or used as a URL to fetch,
- inserted into SQL, file paths or shell commands.

## 8. Files (P1 PDF summarization)

- Only resources already accepted by the upload validation ([11-security-plan.md](11-security-plan.md) §9) can be summarized. Students cannot upload files to the assistant.
- Text extraction limits: at most 50 pages read, at most 12,000 characters kept, a hard time limit on parsing, and PDFBox configured to bound memory use.
- Extracted text is untrusted content and is wrapped in delimiters like any other input.
- Extraction failures return a clear error and are logged without the document text.
- Keep PDFBox updated; parser vulnerabilities are the main risk here.

## 9. Limits and abuse prevention

| Control | Value |
| --- | --- |
| Input length | Per feature, enforced before any provider call |
| Output tokens | `AI_MAX_OUTPUT_TOKENS`, default 1,200 |
| Per user, per minute | 5 |
| Per user, per day | `AI_DAILY_LIMIT_PER_USER`, default 30 |
| Application-wide, per day | Configured ceiling; beyond it AI returns `503` |
| Kill switch | `AI_ENABLED=false` |
| Concurrency | At most 2 AI requests in flight per user |

Counts are taken from `ai_request_logs`, so a restart does not reset the daily limit. Repeated declines from one account are visible in the logs for the owner to review.

## 10. Timeouts, retries and failure

| Setting | Value |
| --- | --- |
| Connect timeout | 5 seconds |
| Response timeout | 20 seconds |
| Total budget | 25 seconds |
| Retry | At most one, only for HTTP 429 or 5xx from the provider, only if at least 10 seconds of budget remain, after a 1-second pause |
| No retry | Timeouts, 4xx other than 429, and invalid output |

A provider failure affects only the AI endpoints. It is caught in the AI service and converted to an AI error code; it never propagates as an unhandled exception. No academic feature calls the AI service.

## 11. Logging and retention

| Item | Logged? |
| --- | --- |
| User ID, feature, prompt version, input and output character counts, status, duration | Yes, in `ai_request_logs`, kept 90 days |
| Prompt text, pasted material, AI response text | **No** |
| Provider error status and a short error type | Yes, in application logs |
| Provider error bodies | No (they can echo the input) |
| API key, request headers | No |

Responses are not cached or stored. A student who wants to keep a result copies it.

## 12. Adversarial testing

Automated tests (with the mock provider) verify the mechanics:

1. User text containing delimiter tags cannot break out of the wrapper.
2. User text never appears in the system field.
3. The outbound request contains no user identifier and no database record.
4. Non-JSON, truncated JSON, extra-field and oversized responses are handled as specified.
5. A response containing `<script>` or HTML is returned as literal text.
6. Quota, burst limit, ceiling and kill switch each block the provider call.
7. Timeouts and provider errors map to the right error codes and do not consume quota.
8. The AI package has no dependency on academic-record packages.

Manual tests (with the real provider, before release) run the behaviour cases B8 to B18 in [13-AI-rules.md](13-AI-rules.md), which cover injection, data requests, instruction leakage and off-topic use. Results are recorded in the release checklist.

## 13. Release criteria for AI

- [ ] All automated tests in §12 pass.
- [ ] Behaviour cases B1 to B20 have been run and recorded.
- [ ] Provider data-handling terms reviewed and recorded.
- [ ] Spending limit set at the provider, where available.
- [ ] The key is present only in production configuration.
- [ ] The kill switch has been tested in production.
- [ ] The privacy notice is visible on the assistant page.
