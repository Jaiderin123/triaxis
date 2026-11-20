-- Schema of the assistant service. Idempotent: it runs at every startup.
-- The whole file is sent as a single script (see spring.sql.init.separator).

CREATE EXTENSION IF NOT EXISTS vector;

-- One row per protocol section. The embedding has no fixed dimension, so a new embedding model does not need
-- a schema change: the ingestion embeds the sections again and searches filter by model (ADR 0002).
CREATE TABLE IF NOT EXISTS protocol_chunks (
  id              TEXT PRIMARY KEY,          -- for example PROTO-001#medidas-iniciales
  protocol_id     TEXT NOT NULL,
  protocol_title  TEXT NOT NULL,
  section         TEXT NOT NULL,
  content         TEXT NOT NULL,
  content_hash    TEXT NOT NULL,
  embedding_model TEXT NOT NULL,
  embedding       vector NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL,
  updated_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_protocol_chunks_model ON protocol_chunks (embedding_model);
