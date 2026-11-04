-- Mensajes de la IA reportados por los usuarios. Idempotente: se puede correr varias veces.
CREATE TABLE IF NOT EXISTS content_reports (
    id         uuid PRIMARY KEY,
    device_id  text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    -- block_message | verdict
    source     text        NOT NULL,
    -- offensive | harmful | wrong | other
    reason     text        NOT NULL,
    -- El texto tal como se mostró: puede traer el nombre o la meta del usuario.
    message    text        NOT NULL
);

CREATE INDEX IF NOT EXISTS content_reports_device ON content_reports (device_id);
CREATE INDEX IF NOT EXISTS content_reports_created ON content_reports (created_at);
