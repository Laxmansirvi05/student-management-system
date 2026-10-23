# 0016. Several AI providers behind one router, with automatic fallback

- Status: Accepted by the owner's request of 2026-10-23 (Phase 1: use the configured providers with automatic fallback). **The review of each provider's data terms is still open** (OQ-1, see Consequences).
- Date: 2026-10-23
- Deciders: Laxman Sirvi
- Changes: extends [ADR 0005](0005-ai-integration-behind-java-interface.md) (AI behind a Java interface); adds `RoutingAiProvider`, `OpenAiCompatibleAiProvider`, `AI_PROVIDER=router` and `AI_ROUTES`; the rules are in [docs/12](../12-AI-features-spec.md) section 2b

## Context

ADR 0005 put every AI call behind the `AiProvider` interface, with the mock as the default and one real adapter (Gemini) added with ADR 0014. The owner has keys for four providers: Gemini, Groq, OpenRouter and Ollama's cloud. A single provider is a single point of failure: free plans have tight rate limits, models are retired (Gemini's 2.5 models are closed to new users), and a project can be refused outright. The owner asked for the assistants to keep working by falling back from one provider to another.

## Options considered

1. **One provider, chosen by `AI_PROVIDER`.** Simple, but the assistants stop whenever that provider is busy, retired or refused.
2. **Rotate through every key on each failure.** Gets round limits by spreading load over keys of the same provider, which can break the providers' terms and hides real outages.
3. **An ordered router over provider and model routes, pausing whole providers on rate limits.**

## Decision

Option 3, inside the existing architecture: the services still talk to one `AiProvider`; when `AI_PROVIDER=router`, that provider is a `RoutingAiProvider` over the models listed in `AI_ROUTES`, in that order. One new adapter class serves the three providers that speak the chat-completions format; no library is added.

- A request goes to the first route that is not resting, and to the next only after a failure or a reply that the feature's own parser or validator rejects.
- A rate limit or used-up quota pauses the **whole provider**. A provider's other keys are tried only when a key itself is refused, never to get round a limit.
- At most three calls per request, 15 s each, within the 25 s budget; a fully paused router fails at once. The mock is never a route, so a failure is never disguised as an answer.
- The order puts the providers with the more protective API terms first (Groq, then Ollama's cloud) and free tiers whose terms allow more use of the text last (Gemini's free tier, OpenRouter's `:free` models).

## Trade-offs

- Student text may go to any of four companies instead of one, each under its own terms.
- More configuration to keep right; a mistyped route stops the start, and a model can be retired at any time (the router then skips it, and the owner updates `AI_ROUTES`).
- Answers may differ in style from one request to the next when a fallback happens.

## Consequences

- **Owner action (OQ-1): review each provider's data-handling terms** before real students use the assistant, and remove any provider from `AI_ROUTES` whose terms are not acceptable. Free plans (in particular Gemini's free tier and OpenRouter's `:free` models) may keep prompts or use them to improve models.
- The privacy policy names the four services and says that each request goes to one of them, and to another only on failure.
- Keys live only in the server's environment (`<PROVIDER>_API_KEY`, `_2` … `_9`); logs name routes as `provider:model (key n)`.
- Automated tests never call a real provider: the default is the mock, the test base class pins it, and the router's tests use fake servers on the local machine.
