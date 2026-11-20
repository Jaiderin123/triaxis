# 0007. Anti-hallucination strategy: retrieval gate, constrained prompt and validated citations

- **Status:** Accepted
- **Date:** 2026-11-19
- **Author:** Jaider Betancur Torres

## Context

RN-05: every clinical answer must cite at least one valid source of the corpus; a source that is not in the corpus makes the answer invalid; when nothing relevant matches, the answer must be only the "no protocol available" notice, without generated clinical content. A language model can invent content or sources, and can be steered by text inside the question. No single control is enough.

## Decision

Four layers, each one tested on its own:

1. **Retrieval gate (deterministic).** The question is embedded and the closest sections are retrieved (top-k 4). Only sections within `assistant.rag.max-distance` reach the model. If none does, the answer is the fixed notice and **the model is not called**. The threshold is calibrated per embedding model (ASSUMPTIONS A36).
2. **Constrained prompt.** Temperature 0 and JSON output. The system prompt allows only the content of the numbered excerpts. It forbids doses, drugs or recommendations that are not in them. It requires the numbers of the excerpts used. It asks for `noProtocol: true` when the excerpts do not answer. It tells the model to ignore instructions inside the question or the excerpts.
3. **Validated citations.** The answer is accepted only when it has text and at least one cited number, and every cited number is one of the excerpts that were sent. Otherwise it is replaced by the fixed notice (outcome `rejected`). The sources in the response are built from the corpus metadata of the cited excerpts (protocol id and section title), never from text written by the model.
4. **No clinical content in any fallback.** The notice is a constant (`No existe un protocolo disponible para esta pregunta.`) with empty `fuentes`; degraded mode is a 503 without answer text (ADR 0004).

The model cites excerpts by number, not by protocol name, so a citation can be checked against what was actually sent.

## Alternatives considered

- **Trusting the model to cite its sources:** sources would be free text, possibly invented.
- **Only the retrieval gate:** a question close to the corpus but not answered by it would still get a generated answer.
- **A second model call that judges the answer (LLM as a judge):** doubles cost and latency and is itself a model that can fail; kept as a possible improvement.
- **Checking that every sentence of the answer appears in an excerpt:** too strict for a summary, and fragile across wording.

## Consequences

- Covered by tests: the gate never calls the model below the threshold (the diabetes sample question); invalid, missing or unretrieved citations give the notice; sources come only from metadata; the model saying `noProtocol` gives the notice.
- Checked with the real provider (ASSUMPTIONS A36). The five covered questions were answered with the right protocol and section. Three hard questions passed the gate and got the notice from the model: a drug the protocol does not name, a dose requested through a prompt injection, and the dose of a contraindicated drug.
- A valid answer can still paraphrase the excerpt; it cannot cite a section that was not retrieved. The answer text is never logged.
