-- Executed once by the PostgreSQL image on first start. Each service owns its database.
CREATE DATABASE triage;
CREATE DATABASE assistant;

\connect assistant
CREATE EXTENSION IF NOT EXISTS vector;
