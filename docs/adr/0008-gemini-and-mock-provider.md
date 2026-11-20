# 0008. Gemini for answers and embeddings, with a deterministic `mock` provider by default

- **Status:** Accepted
- **Date:** 2026-11-19
- **Author:** Jaider Betancur Torres

## Context

The assistant needs a chat model and an embedding model. The specification leaves the provider free, requires the key to come from an environment variable, and requires that the LLM can be mocked in tests. The system must also run with one command for someone who has no key.

## Decision

- **Gemini** through the Spring AI 1.1 starters (`spring-ai-starter-model-google-genai` and `spring-ai-starter-model-google-genai-embedding`):
  - chat: `gemini-2.5-flash`, with temperature 0, JSON output and thinking disabled (`thinking-budget: 0`);
  - embeddings: `gemini-embedding-001`, with 768 dimensions and task type `SEMANTIC_SIMILARITY`;
  - the key comes only from `GEMINI_API_KEY`;
  - both model names are properties (`GEMINI_CHAT_MODEL`, `GEMINI_EMBEDDING_MODEL`).
- Gemini is activated with the `gemini` profile. Without it, the Spring AI auto-configuration of Gemini is off (`spring.ai.model.chat=none`, `spring.ai.model.embedding.text=none`). The embedding connection auto-configuration does not honor those switches, so it is excluded by default.
- **`mock`** is the default provider. It needs no key and no network, and it is deterministic:
  - embeddings are a normalized bag of Spanish words, hashed into 512 dimensions;
  - the answer is extractive: the text of the closest excerpt, cited.
  
  The mock never writes clinical text of its own, and every test uses it or a fake.
- Both providers sit behind the same port, `LlmPort`, decorated with the circuit breaker (ADR 0004). Changing providers touches only an adapter and its configuration.
- Checked with a real key on 2026-11-19 (free tier):
  - the configured models exist and answer;
  - an answer takes about 1 second, and about 2 seconds for the first one;
  - with thinking on, the first answer took 16.5 seconds, which is why it is disabled;
  - the threshold was calibrated (ASSUMPTIONS A36).
- Models seen during that check and not chosen:
  - `gemini-3.5-flash` answered, but it is an alias that is currently served by `gemini-3.6-flash`;
  - `gemini-3.8-flash` and `gemini-flash-latest` did not answer within 60 seconds.

  Fixed names keep the behavior stable.

## Alternatives considered

- **Ollama with a local model:** no key and no cost, but it needs several gigabytes of model files and a GPU or patience. It does not fit the cluster budget of the deployment.
- **DeepSeek or other hosted APIs:** comparable, but they offer no first-party embeddings in Spring AI 1.1, so two providers would be needed.
- **OpenAI or Anthropic models:** good options, but they are paid from the first call, while Gemini has a free tier that covers development and the demonstration.
- **Only a real provider, no mock:** anyone running the project would need a key, and tests could not be deterministic.

## Consequences

- Local runs, the compose file and all tests work without a key.
- With the `gemini` profile, a missing or invalid key does not stop the service. The embedding of the corpus is retried, questions get a 503, and the urgency view keeps working (ASSUMPTIONS A37).
- The free tier may use prompts to improve Google products. Only general clinical questions and the sample protocols are sent, never patient data.
- Whether the deployed environment uses `gemini` or `mock` is decided with the deployment (ASSUMPTIONS A16, phase 8).
