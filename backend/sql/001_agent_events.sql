-- Historial del agente de intervención (S2). Idempotente: se puede correr varias veces.
CREATE TABLE IF NOT EXISTS agent_events (
    id               uuid PRIMARY KEY,
    device_id        text        NOT NULL,
    request_id       uuid        NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    trigger          text        NOT NULL,
    -- Herramientas que pidió el agente, con su estado y tiempo.
    steps            jsonb       NOT NULL DEFAULT '[]',
    -- Solo agregados (minutos y aperturas por app), nunca sesiones crudas.
    evidence_summary jsonb,
    decision         text        NOT NULL,
    reason           text        NOT NULL DEFAULT '',
    proposed_action  jsonb,
    policy_code      text,
    -- NULL sin intervención; sent | rejected | executed | failed. "unknown" se calcula al leer.
    status           text,
    error_code       text,
    confirmed_at     timestamptz,
    -- Pendiente: uso observado después de la intervención.
    observed_outcome jsonb,
    UNIQUE (device_id, request_id)
);

CREATE INDEX IF NOT EXISTS agent_events_device_created
    ON agent_events (device_id, created_at DESC);
