# 0002. Custom R2DBC repository for vectors instead of `PgVectorStore`

- **Status:** Accepted
- **Date:** 2026-11-19
- **Author:** Jaider Betancur Torres

## Context

The assistant retrieves protocol sections by similarity (RF-04 A). The vector store is free to choose, but the service is reactive: no blocking call may run on an event-loop thread, and every remote call needs a timeout. PostgreSQL is already part of the stack, with the `pgvector` extension in the same image used locally and in tests. Spring AI ships `PgVectorStore`, which is built on `JdbcTemplate`: blocking JDBC, a second driver next to R2DBC, and its own table layout with a fixed vector dimension.

## Decision

- Keep the vectors in PostgreSQL with `pgvector`, in the table `protocol_chunks` of the assistant database, and access it with plain R2DBC (`DatabaseClient`).
- Vectors travel as text (`[0.1,0.2,...]`) and are cast with `CAST(:v AS vector)`; the search orders by the cosine distance operator `<=>`, filtered by the embedding model, with a `LIMIT` of top-k.
- The `embedding` column has no fixed dimension and every row stores its `embedding_model`, so a new model needs no schema change: the ingestion embeds the sections again (content hash and model decide what is pending).
- Spring AI is still used for what it does well: the `EmbeddingModel` and `ChatModel` clients of Gemini, and the structured output converter.

## Alternatives considered

- **`PgVectorStore`:** blocking JDBC on the request path (it would have to run on `boundedElastic`), a JDBC driver and pool next to R2DBC, and a fixed dimension in the schema.
- **Redis as the vector store:** Redis is already in the stack for the urgency view, but it would mix the read model of events with the corpus, and needs the search module.
- **An in-memory store:** simple for seven sections, but it is rebuilt (and re-embedded, with a cost) on every start, and does not show a production path.
- **A dedicated vector database (Qdrant, Weaviate):** one more component to run and deploy for a small corpus.

## Consequences

- The whole retrieval path is non-blocking, and the tests run it against a real `pgvector/pgvector:pg16` container with hand-made vectors, so the distances are checked exactly.
- Without a fixed dimension there is no vector index; for a handful of sections a sequential scan is enough. A larger corpus would fix the dimension per model and add an HNSW index.
- The SQL is ours to maintain (about forty lines), in exchange for full control of the schema and the query.
