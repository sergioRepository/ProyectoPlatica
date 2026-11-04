-- Comportamiento en la app, solo con autorización de analítica. Idempotente.
-- Nunca guarda el texto de las excusas: props solo admite claves y valores cortos, sin espacios
-- (banano/schemas/events.py).
CREATE TABLE IF NOT EXISTS app_events (
    -- Lo genera el teléfono: un reintento del lote no duplica eventos.
    id          uuid PRIMARY KEY,
    device_id   text        NOT NULL,
    name        text        NOT NULL,
    app         text,
    occurred_at timestamptz NOT NULL,
    received_at timestamptz NOT NULL DEFAULT now(),
    props       jsonb       NOT NULL DEFAULT '{}'
);

CREATE INDEX IF NOT EXISTS app_events_device_occurred ON app_events (device_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS app_events_name_occurred ON app_events (name, occurred_at);
CREATE INDEX IF NOT EXISTS app_events_received ON app_events (received_at);
