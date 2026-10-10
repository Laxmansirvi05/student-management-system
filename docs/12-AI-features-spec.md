# 12. AI Features Specification

> Status: Draft v0.1 · Owner: Laxman Sirvi · Last updated: 2026-10-10

This document defines exactly what the AI study assistant does. How it must behave is in [13-AI-rules.md](13-AI-rules.md). How it is protected is in [14-AI-security-and-privacy.md](14-AI-security-and-privacy.md). How to build it is in [15-AI-features-handoff.md](15-AI-features-handoff.md). Wire formats are in [09-api-contract.md](09-api-contract.md) §9.

## 1. Overview

| # | Feature | Requirement | Release | Endpoint |
| --- | --- | --- | --- | --- |
| 1 | Explain a topic | FR-AI-001 | MVP (P0) | `POST /api/v1/ai/explain` |
| 2 | Summarize text | FR-AI-002 | MVP (P0) | `POST /api/v1/ai/summarize` |
| 3 | Generate practice questions | FR-AI-003 | MVP (P0) | `POST /api/v1/ai/generate-quiz` |
| 4 | Explain a quiz answer | FR-QZ-003 | MVP (P0) | Reuses `/ai/explain` |
| 5 | Summarize a library PDF | FR-AI-005 | P1 | `POST /api/v1/ai/summarize-resource` |
| 6 | Flowchart generation | FR-AI-006 | P1 | `POST /api/v1/ai/flowchart` |
| 7 | Flashcards | FR-AI-007 | P2 | TBD |
| 8 | Questions grounded in the resource library | | Future | TBD |

The assistant is a set of **single-request tools**, not a chat. There is no conversation history. Each request stands alone, which keeps cost, privacy and testing simple.

## 2. Requirements common to every feature

| Topic | Requirement |
| --- | --- |
| Who | Logged-in students only. |
| Where the call happens | Backend only, through `AiProvider`. |
| Context sent to the provider | The student's input for that request, the selected subject and unit **names**, and the versioned instructions. Nothing from the database about any person. |
| Output | Structured JSON, validated by Java, returned as typed fields of plain text. |
| Labelling | Every response includes `basis`, `notice` and `promptVersion`. |
| Time budget | 25 seconds in total. |
| Daily limit | 30 successful requests per user per day (`AI_DAILY_LIMIT_PER_USER`). |
| Burst limit | 5 requests per user per minute. |
| Output size | At most `AI_MAX_OUTPUT_TOKENS` (default 1,200). |
| Switch | `AI_ENABLED=false` makes every AI endpoint return `503 AI_UNAVAILABLE`. |
| Storage | Prompt and response text are not stored. Only metadata is logged. |

### Loading, timeout and failure behaviour

| Situation | API result | What the student sees |
| --- | --- | --- |
| Waiting | | Disabled button and "This can take up to 30 seconds". |
| Success | `200` | The result, its labels and requests remaining. |
| Input invalid | `400 VALIDATION_FAILED` | Message next to the field, with the limit. |
| Declined | `422 AI_REQUEST_DECLINED` | The reason given by the assistant. Counts toward the limit. |
| Daily limit reached | `429 AI_QUOTA_EXCEEDED` | "You have used today's AI requests. Try again tomorrow." |
| Too fast | `429 RATE_LIMITED` | "Please wait a moment." |
| Provider error or AI switched off | `503 AI_UNAVAILABLE` | "The assistant is unavailable right now." and Retry. |
| Provider too slow | `504 AI_TIMEOUT` | Same message and Retry. |
| Output failed validation | `502 AI_INVALID_RESPONSE` | Same message and Retry. |

Failures (`502`, `503`, `504`) do not count toward the daily limit.

### Cost controls

1. Input length limits per feature (below).
2. Output token cap.
3. Per-user daily and per-minute limits.
4. An application-wide daily ceiling.
5. The kill switch.
6. A low-cost model by default (OQ-1).

## 3. Feature 1: Explain a topic

**User action:** On the Explain tab, the student types a topic, optionally picks a subject and unit, optionally pastes up to 2,000 characters of their own material, picks a format and presses Explain.

| | |
| --- | --- |
| **Inputs** | `topic` (3 to 200 characters, required); `subjectId`, `unitId` (optional); `answerFormat` (`SIMPLE`, `FIVE_MARK`, `TEN_MARK`); `context` (up to 2,000 characters, optional). |
| **Permitted context** | Topic, context text, subject name, unit title. |
| **Output** | `explanation`, `keyPoints` (1 to 8), `example` (may be null), `basis`. |
| **Length guidance** | `SIMPLE`: about 120 words. `FIVE_MARK`: about 200 words. `TEN_MARK`: about 400 words. |
| **Basis** | `GENERAL_KNOWLEDGE` when no context was given; `PROVIDED_TEXT` or `MIXED` when it was. |

**Acceptance criteria**

1. A valid request returns all required fields within the time budget.
2. The explanation matches the requested format's approximate length.
3. With `context`, statements drawn from it are consistent with it; `basis` is not `GENERAL_KNOWLEDGE`.
4. An off-topic or unsafe topic returns `422`.
5. Output containing HTML or script-like text is shown as literal text.

**Tests:** validation boundaries (2, 3, 200, 201 characters); each format; with and without context; unit that does not belong to the subject (400); decline; timeout; malformed provider output.

## 4. Feature 2: Summarize text

**User action:** On the Summarize tab, the student pastes text, picks a style and presses Summarize.

| | |
| --- | --- |
| **Inputs** | `text` (200 to 12,000 characters, required); `style` (`SHORT`, `REVISION_NOTES`); `subjectId` (optional). |
| **Permitted context** | The pasted text and the subject name. |
| **Output** | `summary`, `keyPoints` (up to 10), `definitions` (up to 15, may be empty), `basis` always `PROVIDED_TEXT`. |
| **Length guidance** | `SHORT`: about 100 words. `REVISION_NOTES`: about 250 words plus key points. |

**Acceptance criteria**

1. The summary contains only information present in the text.
2. Instructions written inside the pasted text are treated as content, not obeyed (see AI rule R9).
3. Text under 200 or over 12,000 characters is rejected with `400` before any provider call.
4. `definitions` lists only terms the text itself defines.

**Tests:** length boundaries (199, 200, 12,000, 12,001); both styles; text containing an injection attempt; text with no definitions; timeout; malformed output.

## 5. Feature 3: Generate practice questions

**User action:** On the Practice questions tab, the student gives a topic or pastes text, chooses a type and a count, and presses Generate.

| | |
| --- | --- |
| **Inputs** | Exactly one of `topic` (3 to 200) or `sourceText` (200 to 8,000); `questionType` (`MCQ`, `SHORT`, `LONG`); `count` (1 to 10); `subjectId`, `unitId` (optional). |
| **Output** | `questions[]` each with `question`, `answer`, `explanation`; MCQs also have four `options` and a `correctOption`. Plus `requested` and `returned`. |
| **Persistence** | None. Questions are shown to the student and are not saved to the question bank. |

**Validation of generated MCQs (in Java):**

1. Exactly four non-empty, distinct options.
2. `correctOption` is one of A to D.
3. `answer` equals the text of the correct option.
4. Question and explanation are non-empty and within length limits.

Questions failing any check are discarded. If none remain, the response is `502 AI_INVALID_RESPONSE`.

**Acceptance criteria**

1. `returned` is at most `requested`, and every returned question passes validation.
2. With `sourceText`, questions are answerable from that text; `basis` is `PROVIDED_TEXT`.
3. The notice states that these are practice questions, not official exam questions.
4. Sending both `topic` and `sourceText`, or neither, returns `400`.

**Tests:** each type; count boundaries (0, 1, 10, 11); both and neither input; provider returns a duplicate-option MCQ (discarded); provider returns all-invalid (502).

## 6. Feature 4: Explain a quiz answer

On a quiz result, "Ask AI to explain" opens the Explain tab pre-filled with the question text as `topic` (trimmed to 200 characters), the quiz's subject and unit, and `answerFormat` `SIMPLE`. The student presses Explain. It is the same endpoint and counts as one request.

The official explanation stored with the question is always shown first; the AI explanation is additional.

## 7. Feature 5: Summarize a library PDF (P1)

| | |
| --- | --- |
| **Input** | `resourceId`, `style`. |
| **Source** | A published resource. The backend downloads the file, extracts text with PDFBox, and uses at most the first 12,000 characters. |
| **Output** | As Feature 2, plus `inputTruncated` (true when the document was longer) and the resource title. |
| **Failure** | A PDF with no extractable text returns `422` with "This PDF has no readable text." |

**Depends on:** resource hub (M3), Feature 2. **Extra risk:** document content is untrusted input to both the parser and the model.

## 8. Feature 6: Flowchart generation (P1)

| | |
| --- | --- |
| **Input** | `topic` (3 to 200) or `processText` (up to 3,000). |
| **Model output** | A JSON graph: up to 20 `nodes` (`id`, `label` up to 80 characters, `type` of `START`, `END`, `STEP`, `DECISION`) and `edges` (`from`, `to`, optional `label`). |
| **Java processing** | `FlowchartRenderer` validates the graph (known node IDs, one start, at least one end, no dangling edges) and builds Mermaid text itself with escaped labels. |
| **API output** | `title`, `mermaid` (built by Java, not by the model), and `steps[]` as a text list. |

The model never writes diagram syntax directly. This keeps untrusted text out of the renderer's syntax.

## 9. Future: questions grounded in the resource library

Not designed yet. It would need stored extracted text for every resource, a retrieval step that selects passages within the student's permitted resources, and citations of which resource each statement came from. It must be specified in this document and reviewed against [14-AI-security-and-privacy.md](14-AI-security-and-privacy.md) before any work starts.

## 10. Explicitly not built

- Free-form chat with memory.
- Any AI access to marks, attendance, results or profiles.
- AI grading of tests.
- Prediction of exam questions.
- Automatic insertion of AI questions into the question bank.
- Image, voice or handwriting input.
