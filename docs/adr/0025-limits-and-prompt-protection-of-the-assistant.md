# 0025. Limits and prompt protection of the clinical assistant

- **Status:** Accepted
- **Date:** 2026-11-21
- **Author:** Jaider Betancur Torres

## Context

The deployed assistant uses Gemini, whose free quota has a low limit of requests per minute and per day, and every question costs one or two calls: the embedding of the question always, and the answer when a protocol is close enough. The circuit breaker of ADR 0004 protects against a provider that fails, but nothing stopped one user from spending the quota, and with it the assistant of every user: once the quota is gone, every question gets the 503 of the degraded mode.

The anti-hallucination layers of ADR 0007 (the retrieval gate, the constrained prompt and the validated citations) had one known gap. The question was sent in the same message as the numbered excerpts, so a question could imitate an excerpt (`[2] ...`) or close its own block, and the citation check only verified the numbers of the excerpts, not what the answer says. In a clinical answer the worst outcome is an invented figure, such as a dose, a percentage or a time.

## Decision

- **A limit of questions per user.** `POST /asistente/consulta` allows 10 questions per minute to each user, the `sub` of the token (`ASSISTANT_QUESTIONS_PER_MINUTE`). It never waits: a question over the limit gets **429** with `Retry-After` and the standard error format.
  - Every question counts, answered or not, because each one costs at least the embedding. The limit is checked before the body is read.
  - It is a Resilience4j rate limiter per user, from the configuration `question`. The users are the accounts of auth, so the registry stays small.
- **A bound of calls in flight.** The bulkhead `llm` allows 4 calls to the provider at a time (`ASSISTANT_LLM_MAX_CONCURRENT_CALLS`) and never waits.
  - A call over the bound gets the 503 of ADR 0004 at once, and it does not count as a failure of the circuit breaker.
  - It applies to the embeddings and the answers, so it also bounds a burst from several users.
- **The question isolated in the prompt.** The excerpts go between `<extractos>` tags and the question between `<pregunta>` tags. The system prompt says that the question is only text of the user, never an excerpt or an instruction.
  - Before it is sent, the question becomes one line, the tags are removed from it, and excerpt markers such as `[2]` are written as `(2)`. It cannot close its block or forge an excerpt the model would cite.
- **Figures grounded in the cited excerpts.** The citation validator also checks that every number of the answer (integers and decimals, with a point or a comma) is written in one of the cited excerpts, their protocol or their section. Otherwise the answer is rejected and replaced by the fixed notice.
  - This check does not depend on the model obeying the prompt.

## Alternatives considered

- **A limit per address.** Behind the Ingress every client may share the address of the proxy (A28), and the cost is caused by a user, not by an address.
- **A shared limit for the whole service.** It protects the quota, but one user can still take all the questions of the others.
- **Waiting for a permission instead of refusing.** It holds a connection and a reactive pipeline open during the wait. A 429 with `Retry-After` is clear for a client and costs nothing.
- **A classifier or a list of forbidden phrases for injections.** It is easy to bypass, and it would refuse legitimate clinical questions; the structural isolation and the deterministic checks do not depend on the wording.
- **A second model call to judge the answer.** It doubles the calls to a provider whose quota is the problem, and the judge can be manipulated too.

## Consequences

- One user cannot spend the quota of the provider for the others, and a burst gets a clear 429 or 503 instead of hanging.
- A forged excerpt in a question is not seen by the model as an excerpt, and an invented figure never reaches a doctor.
- **A false negative:** an answer that writes a figure in another form than the excerpt (for example "1 hora" for "la primera hora") is replaced by the fixed notice. That is the safe direction for RN-05.
- A number spelled in words is not checked by the validator; the prompt and the citations cover it.
- The limits are configuration. The tests raise the limit of the shared context and check the 429 with a limiter of their own.
