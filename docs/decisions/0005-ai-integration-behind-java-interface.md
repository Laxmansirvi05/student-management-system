# 0005. AI integration behind a Java interface, server-side only

- Status: Proposed. The integration approach is ready for approval. **The provider and model are not yet chosen** (open decision OQ-1).
- Date: 2026-10-10
- Deciders: Laxman Sirvi

## Context

The study assistant needs a large language model to explain topics, summarize text and generate practice questions. Running a model ourselves is not practical. A hosted API costs money per use, needs a secret key, can be slow or unavailable, and returns text that cannot be trusted blindly. The assignment requires the integration to be in Java.

## Options considered

1. **Call the AI provider directly from browser JavaScript.** Simple, but exposes the API key to every user and removes all control over cost, input and output.
2. **Use a Java AI framework** (for example Spring AI or a provider's Java SDK). Less code to write, but adds a sizeable dependency, hides the HTTP integration that is worth demonstrating, and ties the code to a library's release cycle.
3. **A small Java interface (`AiProvider`) with a mock implementation and one real adapter that calls the provider's HTTP API through Spring's `RestClient`.**

## Decision

All AI calls are made by the backend through an `AiProvider` interface. A deterministic `MockAiProvider` is the default and is used by every automated test. One real adapter, selected by configuration, calls the chosen provider over HTTPS with explicit timeouts. Prompts are versioned template files. Model output must be JSON, which Java parses and validates before anything is returned to the browser. That validation covers structure and size, not whether the content follows the AI rules.

The model has no tools and no access to application data. It receives only the student's input for that request plus subject and unit names.

## Trade-offs

- We write and maintain the HTTP adapter and the JSON parsing ourselves.
- No streaming responses in the first release; the student waits for the whole answer.
- Single-request tools with no memory are less flexible than a chat.
- A provider SDK could offer retries and typed responses for free.

## Consequences

- The API key stays on the server.
- The application runs, and all tests pass, with no key and no spend.
- Switching provider means writing one new adapter class.
- Development of AI features is not blocked by the provider decision until the real adapter task.
- Limits, logging and the kill switch are implemented once, around the interface.
- Acceptance of the AI requirements still needs the real adapter. Whether a mock-only submission is acceptable is open decision OQ-9.

## To complete before this record is accepted

| Item | Value |
| --- | --- |
| Provider | **TBD.** Proposed: Anthropic Claude API. |
| Model | **TBD.** Proposed: a low-cost model from that provider, set by `AI_MODEL`. |
| API documentation link | TBD (read the current documentation before writing the adapter) |
| Data-handling terms reviewed (training use, retention) | TBD |
| Monthly budget and provider spending limit | TBD |
| Free or student credit available | TBD |
