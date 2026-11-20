-- Schema of the triage service. Idempotent: it runs at every startup.
-- The whole file is sent as a single script (see spring.sql.init.separator).

CREATE TABLE IF NOT EXISTS patients (
  id                      UUID PRIMARY KEY,
  full_name               VARCHAR(100) NOT NULL,
  age                     SMALLINT NOT NULL,
  heart_rate              SMALLINT NOT NULL,
  systolic                SMALLINT NOT NULL,
  diastolic               SMALLINT NOT NULL,
  spo2                    SMALLINT NOT NULL,
  temperature             NUMERIC(3,1) NOT NULL,
  pain                    SMALLINT NOT NULL,
  gcs                     SMALLINT NOT NULL,
  chief_complaint         TEXT NOT NULL,
  level                   VARCHAR(16) NOT NULL,
  sla_minutes             INTEGER NOT NULL,
  status                  VARCHAR(16) NOT NULL,
  escalated               BOOLEAN NOT NULL DEFAULT FALSE,
  escalated_at            TIMESTAMPTZ,
  registered_at           TIMESTAMPTZ NOT NULL,
  status_changed_at       TIMESTAMPTZ NOT NULL,
  consultation_started_at TIMESTAMPTZ,
  -- Second safety net: the domain validates first, the database refuses anything outside the ranges.
  CONSTRAINT chk_patients_name CHECK (char_length(btrim(full_name)) BETWEEN 3 AND 100),
  CONSTRAINT chk_patients_age CHECK (age BETWEEN 0 AND 120),
  CONSTRAINT chk_patients_heart_rate CHECK (heart_rate BETWEEN 20 AND 250),
  CONSTRAINT chk_patients_systolic CHECK (systolic BETWEEN 60 AND 260),
  CONSTRAINT chk_patients_diastolic CHECK (diastolic BETWEEN 30 AND 160 AND diastolic < systolic),
  CONSTRAINT chk_patients_spo2 CHECK (spo2 BETWEEN 70 AND 100),
  CONSTRAINT chk_patients_temperature CHECK (temperature BETWEEN 34.0 AND 42.0),
  CONSTRAINT chk_patients_pain CHECK (pain BETWEEN 0 AND 10),
  CONSTRAINT chk_patients_gcs CHECK (gcs BETWEEN 3 AND 15),
  CONSTRAINT chk_patients_complaint CHECK (char_length(btrim(chief_complaint)) BETWEEN 1 AND 500),
  CONSTRAINT chk_patients_level CHECK (level IN ('CRITICAL', 'URGENT', 'URGENT_2', 'NOT_URGENT', 'NOT_URGENT_2')),
  CONSTRAINT chk_patients_status CHECK (status IN ('REGISTERED', 'IN_CONSULTATION', 'DISCHARGED')),
  CONSTRAINT chk_patients_sla CHECK (sla_minutes > 0)
);

CREATE INDEX IF NOT EXISTS idx_patients_sla ON patients (status, escalated, registered_at);

-- Range filter of the operation report (RF-05).
CREATE INDEX IF NOT EXISTS idx_patients_registered_at ON patients (registered_at);

CREATE TABLE IF NOT EXISTS patient_audit (
  id              BIGSERIAL PRIMARY KEY,
  patient_id      UUID NOT NULL REFERENCES patients (id),
  actor           VARCHAR(100) NOT NULL,
  event_type      VARCHAR(32) NOT NULL,
  previous_status VARCHAR(16),
  new_status      VARCHAR(16) NOT NULL,
  escalated       BOOLEAN NOT NULL,
  occurred_at     TIMESTAMPTZ NOT NULL,
  CONSTRAINT chk_audit_event CHECK (event_type IN ('REGISTERED', 'CONSULTATION_STARTED', 'DISCHARGED', 'ESCALATED'))
);

CREATE INDEX IF NOT EXISTS idx_patient_audit_patient ON patient_audit (patient_id, occurred_at);

-- The audit is append-only: nothing may change or remove its rows.
CREATE OR REPLACE FUNCTION reject_audit_change() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'patient_audit is append-only';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS patient_audit_no_update_delete ON patient_audit;
CREATE TRIGGER patient_audit_no_update_delete
  BEFORE UPDATE OR DELETE ON patient_audit
  FOR EACH ROW EXECUTE FUNCTION reject_audit_change();

DROP TRIGGER IF EXISTS patient_audit_no_truncate ON patient_audit;
CREATE TRIGGER patient_audit_no_truncate
  BEFORE TRUNCATE ON patient_audit
  FOR EACH STATEMENT EXECUTE FUNCTION reject_audit_change();

-- Transactional outbox: events are written with the change that causes them and a relay delivers them to Kafka.
CREATE TABLE IF NOT EXISTS outbox_events (
  id             BIGSERIAL PRIMARY KEY,
  event_id       UUID NOT NULL UNIQUE,
  message_key    VARCHAR(64) NOT NULL,
  event_type     VARCHAR(32) NOT NULL,
  correlation_id VARCHAR(64),   -- trace id of the request
  payload        JSONB NOT NULL,
  created_at     TIMESTAMPTZ NOT NULL,
  published_at   TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_outbox_pending ON outbox_events (id) WHERE published_at IS NULL;

-- The W3C trace context of the request that caused the event; the relay sends it as a Kafka header.
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS traceparent VARCHAR(64);
