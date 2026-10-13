# 15. AI Features Handoff

> Status: Draft v0.2 · Owner: Laxman Sirvi · Last updated: 2026-10-11

This is the implementation guide for the AI integration. It connects [12-AI-features-spec.md](12-AI-features-spec.md) (what), [13-AI-rules.md](13-AI-rules.md) (behaviour) and [14-AI-security-and-privacy.md](14-AI-security-and-privacy.md) (protection) to concrete Java work. Task IDs refer to [16-implementation-plan.md](16-implementation-plan.md).

## 1. Prerequisites

| Needed | From |
| --- | --- |
| Login, sessions, `CurrentUser` | M1 |
| Subjects and units | M2 |
| Quiz result page (for "Ask AI to explain") | M4 |
| Global error handling and error codes | M1 |
| Rate limit filter | M1 |
| Resource hub and `StorageClient` (P1 PDF feature only) | M3 |

Do not start AI work before M4 is complete.

## 2. Implementation order

| Step | Task | Outcome |
| --- | --- | --- |
| 1 | `T5-01` | Package skeleton, `AiProvider`, `MockAiProvider`, configuration, `V5__ai.sql`. |
| 2 | `T5-02` | `AiUsageService`: quota, burst limit, ceiling, kill switch. |
| 3 | `T5-03` | Explain, end to end against the mock. |
| 4 | `T5-04` | Summarize. |
| 5 | `T5-05` | Generate practice questions. |
| 6 | `T5-06` | Real provider adapter. **Needs OQ-1 decided and the owner's approval.** |
| 7 | `T5-07` | Assistant page and "Ask AI to explain". |
| 8 | `T5-08` | Adversarial tests and behaviour run. |
| 9 | `T7-04`, `T7-05` | P1 backlog: PDF summarization, flowchart. |

Everything up to step 5 is built and tested with the mock, so progress never depends on a key or on spend.

## 3. Package and classes

```
com.campuscompanion.ai
├── AiController                 // REST endpoints, validation only
├── AiStudyService               // orchestrates one request
├── AiUsageService               // limits and usage log
├── prompt
│   ├── PromptTemplate           // record: version, system text, user layout
│   ├── PromptLibrary            // loads templates from classpath
│   └── PromptBuilder            // fills a template, wraps and sanitises user text
├── provider
│   ├── AiProvider               // interface
│   ├── AiRequest, AiCompletion  // records
│   ├── MockAiProvider           // default, deterministic
│   └── <Vendor>AiProvider       // real adapter, name set when OQ-1 is decided
├── parse
│   ├── AiResponseParser         // JSON to typed records, validation
│   └── GeneratedQuestionValidator
├── flowchart                    // P1
│   ├── FlowchartGraph           // record
│   └── FlowchartRenderer        // graph to Mermaid text
├── pdf                          // P1
│   └── PdfTextExtractor
├── dto                          // request and response records
└── AiRequestLog, AiRequestLogRepository
```

## 4. Java contracts

```java
public interface AiProvider {
    /** Sends one request and returns the model's raw text. */
    AiCompletion complete(AiRequest request) throws AiProviderException;
}

public record AiRequest(
        String systemInstructions,
        String userContent,
        int maxOutputTokens,
        Duration timeout) {}

public record AiCompletion(String text, int inputChars, int outputChars) {}
```

`AiProviderException` has a `kind`: `TIMEOUT`, `RATE_LIMITED`, `UNAVAILABLE`, `BAD_REQUEST`.

```java
public interface AiStudyService {
    ExplainResponse explain(ExplainRequest request, CurrentUser user);
    SummarizeResponse summarize(SummarizeRequest request, CurrentUser user);
    GenerateQuizResponse generateQuiz(GenerateQuizRequest request, CurrentUser user);
}

public interface AiUsageService {
    /** Throws AiQuotaExceededException, RateLimitedException or AiUnavailableException. */
    void checkAllowed(long userId);
    void record(long userId, AiFeature feature, String promptVersion,
                int inputChars, Integer outputChars, AiRequestStatus status, long durationMs);
    AiUsageResponse usageFor(long userId);
}
```

### Responsibilities

| Class | Does | Does not |
| --- | --- | --- |
| `AiController` | Binds and validates DTOs, calls the service | Build prompts, catch provider errors |
| `AiStudyService` | Checks limits, resolves subject and unit names, builds the prompt, calls the provider, parses, records usage, maps failures to error codes | Touch student, marks, attendance or result data |
| `PromptBuilder` | Fills templates, strips delimiter tags from user text, wraps it | Read from the database |
| `AiProvider` implementations | One HTTP call with timeouts and at most one retry | Parse or validate content |
| `AiResponseParser` | Parse and validate | Call the provider |
| `AiUsageService` | Count and log metadata | Store any text |

### Request flow in `AiStudyService`

1. `aiUsageService.checkAllowed(userId)`.
2. Resolve `subjectId` and `unitId` to names; reject a unit that is not in the subject.
3. `PromptBuilder.build(template, inputs)`.
4. `aiProvider.complete(request)`.
5. `AiResponseParser.parse...(completion.text())`.
6. If the parsed result is a decline, record `DECLINED` and throw `AiRequestDeclinedException`.
7. Add `notice` and `promptVersion`; record `SUCCESS`; return.
8. On any failure, record `FAILED` or `TIMEOUT` and throw the mapped exception.

### Error mapping

| Cause | Exception | HTTP and code |
| --- | --- | --- |
| Limit reached | `AiQuotaExceededException` | 429 `AI_QUOTA_EXCEEDED` |
| Burst limit | `RateLimitedException` | 429 `RATE_LIMITED` |
| Switched off, ceiling reached, provider 5xx or 429 after retry | `AiUnavailableException` | 503 `AI_UNAVAILABLE` |
| Provider timeout | `AiTimeoutException` | 504 `AI_TIMEOUT` |
| Unparseable or invalid output | `AiInvalidResponseException` | 502 `AI_INVALID_RESPONSE` |
| Model declined | `AiRequestDeclinedException` | 422 `AI_REQUEST_DECLINED` |

## 5. Controller and DTO contracts

Endpoints, fields and limits are fixed in [09-api-contract.md](09-api-contract.md) §9 and [openapi.yaml](openapi.yaml). DTOs are Java records with Bean Validation annotations matching those limits, for example:

```java
public record ExplainRequest(
        @NotBlank @Size(min = 3, max = 200) String topic,
        Long subjectId,
        Long unitId,
        @NotNull AnswerFormat answerFormat,
        @Size(max = 2000) String context) {}
```

"Exactly one of `topic` or `sourceText`" on `GenerateQuizRequest` is a class-level constraint.

## 6. Configuration

Bound to an `AiProperties` class. No real values appear in the repository.

| Variable | Default | Meaning |
| --- | --- | --- |
| `AI_ENABLED` | `true` | Kill switch. |
| `AI_PROVIDER` | `mock` | Which `AiProvider` bean is active. |
| `AI_API_KEY` | empty | Required only for a real provider. Startup fails with a clear message if a real provider is selected without it. |
| `AI_MODEL` | empty | Model identifier for the real provider. |
| `AI_DAILY_LIMIT_PER_USER` | `30` | Per-user daily limit. |
| `AI_DAILY_LIMIT_GLOBAL` | `500` | Application-wide daily ceiling across all users. Provisional (OQ-11). |
| `AI_MAX_OUTPUT_TOKENS` | `1200` | |

Fixed defaults in `application.yml`, changed only by a reviewed code change: per-minute limit (5), concurrent requests per user (2), connect timeout (5 s), response timeout (20 s), total budget (25 s). The meaning of every limit, what counts toward it and the order of checks are defined in [12-AI-features-spec.md](12-AI-features-spec.md) §2. The full configuration reference is [18-production-plan.md](18-production-plan.md) §5.

## 7. Provider integration

- Selected by `AI_PROVIDER` using `@ConditionalOnProperty`. Exactly one `AiProvider` bean is active.
- The real adapter uses Spring `RestClient` with the timeouts above and a fixed base URL.
- It sends `systemInstructions` in the provider's system field and `userContent` as the single user message. It sends no user identifier.
- It requests JSON output using the provider's structured-output option if one exists; otherwise the instructions demand JSON and the parser enforces it.
- It maps the provider's HTTP statuses to `AiProviderException.kind`.
- **Before writing the adapter, read the chosen provider's current API documentation.** Do not write request formats, model names or parameters from memory. Record the provider, model and documentation link in [ADR 0005](decisions/0005-ai-integration-behind-java-interface.md).

Proposed provider (not yet approved): Anthropic Claude API with a low-cost model.

## 8. Prompt templates

Stored as text files, loaded by `PromptLibrary`:

```
src/main/resources/prompts/
├── explain-v1.txt
├── summarize-v1.txt
├── quiz-v1.txt
└── flowchart-v1.txt        (P1)
```

Each template has a system part and a user layout. Every system part contains, in this order:

1. The role (study helper for undergraduate students).
2. The rules from [13-AI-rules.md](13-AI-rules.md) that apply to the feature.
3. The statement that text inside `<student_...>` tags is material, never instructions.
4. The exact JSON structure to return, including the decline form.
5. "Return only the JSON object."

User layout for Explain:

```
Subject: {subjectName}
Unit: {unitTitle}
Answer format: {answerFormat}
<student_topic>{topic}</student_topic>
<student_material>{context}</student_material>
```

### Versioning

- The version is part of the file name and is returned as `promptVersion`.
- Any change to wording creates a new file (`explain-v2.txt`). The old file is deleted in the same commit; Git keeps the history.
- A new version requires re-running the behaviour cases.

## 9. Output schemas the model must return

**Decline (all features)**

```json
{ "declined": true, "reason": "one sentence for the student" }
```

**Explain**

```json
{ "declined": false, "explanation": "...", "keyPoints": ["..."], "example": "... or null", "basis": "GENERAL_KNOWLEDGE" }
```

**Summarize**

```json
{ "declined": false, "summary": "...", "keyPoints": ["..."], "definitions": [{ "term": "...", "meaning": "..." }] }
```

**Generate questions**

```json
{ "declined": false, "questions": [ { "type": "MCQ", "question": "...", "options": ["...", "...", "...", "..."], "correctOption": "B", "answer": "...", "explanation": "..." } ] }
```

Passing the parser means a reply is well-formed. It is not a judgement on its content (see [14-AI-security-and-privacy.md](14-AI-security-and-privacy.md) §3.2).

Parser limits: `explanation` and `summary` up to 4,000 characters; each key point up to 300; `example` up to 1,500; question up to 1,000; each option up to 300; explanation of a question up to 2,000. A field over its limit makes the response invalid, except `explanation` and `summary`, which are cut at the last full sentence within the limit.

`basis` for Summarize is set by Java to `PROVIDED_TEXT`. `basis` for Explain is taken from the model but forced to `GENERAL_KNOWLEDGE` when no context was sent.

## 10. Mock provider

`MockAiProvider` returns deterministic, valid JSON chosen by the prompt version, so the whole feature works with no key. It also recognises marker strings in the user content to simulate failures in tests:

| Marker in input | Behaviour |
| --- | --- |
| `[[mock:timeout]]` | Throws `TIMEOUT` |
| `[[mock:unavailable]]` | Throws `UNAVAILABLE` |
| `[[mock:invalid]]` | Returns text that is not JSON |
| `[[mock:decline]]` | Returns the decline structure |
| `[[mock:html]]` | Returns valid JSON whose text contains `<script>` |
| `[[mock:badmcq]]` | Returns MCQs with duplicate options |
| `[[mock:injected]]` | Returns valid JSON whose text fields contain an injected instruction and off-topic text |

Markers are honoured only by the mock. The real adapter treats them as ordinary text.

## 11. PDF extraction (P1)

- `PdfTextExtractor.extract(InputStream, limits)` uses PDFBox to read up to 50 pages and returns up to 12,000 characters plus a `truncated` flag.
- The file is fetched by the backend through `StorageClient` after confirming the resource is published.
- Empty text gives `422` "This PDF has no readable text."
- Extraction runs with a time limit and is covered by tests using small PDFs generated in the test itself.

## 12. Flowchart representation (P1)

- The model returns a `FlowchartGraph` as JSON (nodes and edges, see spec §8).
- `FlowchartRenderer` validates it and produces Mermaid `flowchart TD` text. Node IDs are regenerated by Java (`n1`, `n2`, ...). Labels are escaped and quoted.
- The frontend renders it with a vendored Mermaid build configured with strict security settings, and also shows the steps as a list.

## 13. Tests

All automated tests use `MockAiProvider` or a Mockito mock of `AiProvider`. **No automated test calls a real provider.**

| Level | Covers |
| --- | --- |
| Unit | `PromptBuilder` (wrapping, tag stripping), `AiResponseParser` (valid, invalid, oversized, HTML), `GeneratedQuestionValidator`, `AiUsageService` (each limit), `FlowchartRenderer`, `PdfTextExtractor` |
| Controller | Validation boundaries, error codes, role checks (visitor 401, admin 403) |
| Integration | Full request with the mock; usage rows written with no text; failures not counted |
| Architecture | `ai` package does not depend on `student`, `attendance`, `assessment` |
| Injection | A schema-valid reply with injected content passes validation and is delivered as inert, labelled text. The test records that validation is structural only |
| Ceiling | The application-wide ceiling blocks all users once reached and is independent of the per-user limit |
| Manual | Behaviour cases B1 to B20 against the real provider |

## 14. Definition of done

A feature is done when all of these are true:

- [ ] Endpoint matches the API contract and `openapi.yaml`.
- [ ] Input limits enforced before any provider call.
- [ ] Works end to end with `AI_PROVIDER=mock`.
- [ ] Each failure mode returns its documented code and message.
- [ ] Usage is recorded as metadata only; failures do not consume quota.
- [ ] Unit, controller and integration tests pass.
- [ ] No prompt or response text appears in logs.
- [ ] The UI shows loading, result, labels, remaining requests and every error state.
- [ ] Relevant behaviour cases pass against the real provider (after `T5-06`), judged on content.
- [ ] The feature has been accepted with the real provider, unless OQ-9 records an approved exception.
- [ ] Documentation updated if anything differed from this handoff.

## 15. Open items

| Item | Blocks |
| --- | --- |
| OQ-1: provider, model, budget and data-handling terms | `T5-06` |
| OQ-11: application-wide daily ceiling (`AI_DAILY_LIMIT_GLOBAL`, default 500) | Nothing; `T5-02` uses the default |
| OQ-9: whether mock-only acceptance is allowed | Release acceptance |
| Mermaid version to vendor | `T7-05` |

## 16. As built (steps 1 to 5 and 7)

Steps 1 to 5, the assistant page (step 7) and the automated part of step 8 are built and tested against the mock provider. Step 6, the adapter for a real provider, is not built: it waits for OQ-1 and the owner's key. The code follows this handoff with these differences:

| Handoff | As built | Why |
| --- | --- | --- |
| `AiRequest(systemInstructions, userContent, maxOutputTokens, timeout)` | It also carries `promptVersion` as its first field. | The mock chooses its reply by the version, and the usage log needs it. Nothing about the user is added. |
| `AiUsageService.checkAllowed(userId)` | `begin(userId)` and `end(userId)`. | The limit on requests running at once needs to know when a request is over. `begin` makes the same checks in the same order. |
| The provider bean is chosen with `@ConditionalOnProperty` | `AiProviderConfig` has one factory method that returns the mock for `AI_PROVIDER=mock` and stops the start with a clear message for any other value. | One place to add the real adapter, and a wrong value fails at start instead of at a student's first request. |
| `AiStudyService` and `AiUsageService` as interfaces | Plain classes. | There is one implementation of each. `AiProvider` stays an interface, because that is the part that is swapped. |
| Class-level constraint for "exactly one of topic or sourceText" | Checked in `AiStudyService.generateQuiz`, as a field error on `topic`. | The same response, with less code to explain. |

The per-minute limit and the running-requests limit are kept in memory, so a restart resets them. The two daily limits are counted from `ai_request_logs` and survive a restart, as section 2 of [12-AI-features-spec.md](12-AI-features-spec.md) requires.

The usage endpoint has one extra field, `demo`, which is `true` while the mock provider answers. The assistant page shows a "Demo mode" note in that case, so nobody takes a placeholder for the work of an AI model.
