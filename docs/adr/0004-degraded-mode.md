# 0004. Degraded mode of the assistant: circuit breaker, 503 and triage independence

- **Status:** Accepted
- **Date:** 2026-11-19
- **Author:** Jaider Betancur Torres

## Context

The assistant depends on an external language model provider for embeddings and answers. The specification asks for a circuit breaker on those calls with an explicit fallback: a 503 with a clear message when the LLM is unavailable. The triage service must never depend on the assistant, and the urgency view (RF-04 B) has nothing to do with the LLM.

## Decision

- One Resilience4j circuit breaker, `llm`, wraps both kinds of call (embedding and answer) in a decorator of the provider port (`ResilientLlmAdapter`), so the use case only sees a port that either answers or fails with `AssistantUnavailableException`.
- Every call has a timeout (embedding 5 seconds, answer 20 seconds); a timeout counts as a failure. The breaker counts the last ten calls and opens when half of them failed (at least five calls), stays open 30 seconds and then lets three trial calls through. All values are properties.
- Any provider failure, a timeout or an open breaker becomes a 503 in the shared RFC 7807 format, with `Retry-After: 30` and the detail "The clinical assistant is temporarily unavailable". The fallback never contains generated or clinical text.
- An answer that is not the expected JSON is not a provider failure: it is a successful call whose content is rejected by the citation check (ADR 0007), so it does not open the breaker.
- A question without a relevant protocol is answered with the fixed notice without calling the chat model. It still needs the embedding of the question from the same provider and through the same breaker, so while the provider is down, or the breaker is open, it gets the 503 too (without clinical content).
- The corpus is validated at startup, and its embedding is retried in the background; until it is ready, questions get the same 503 (ASSUMPTIONS A37).
- No retry of the answer call: a retry doubles the wait and the cost of a request that is already slow, and the breaker protects the provider better.
- Metrics: `assistant.llm.latency` (tags `operation` and `outcome`), `assistant.answers` with outcome `degraded`, and the breaker state from Resilience4j.
- Triage never calls the assistant, and the assistant reads triage only through Kafka.

## Alternatives considered

- **Annotations (`@CircuitBreaker`, `@TimeLimiter`):** less code, but they work through proxies, are harder to see and to test with reactive types; the decorator is explicit and tested without Spring.
- **A cached or generic answer as fallback:** it would be clinical content that was not produced for the question, which RN-05 forbids.
- **Two breakers (embedding and chat):** finer control, but both calls go to the same provider and fail together.
- **Retries with back-off on the answer:** see above.

## Consequences

- A provider outage costs at most five slow requests before the breaker answers 503 at once; the provider is not called again until the trial calls.
- The tests prove it: unit tests of the decorator (failure, timeout, open breaker, unreadable answer) and an end-to-end test where the provider always fails and is no longer called once the breaker is open.
- The values are a starting point; they should be tuned with the real latency of the provider (measured at about one to two seconds per answer).
