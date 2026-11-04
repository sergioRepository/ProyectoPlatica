-- Dispositivos y autorizaciones de datos. Idempotente: se puede correr varias veces.
CREATE TABLE IF NOT EXISTS devices (
    device_id         text PRIMARY KEY,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    -- Estado vigente de las autorizaciones opcionales, para leerlo en una sola consulta.
    consent_excuses   boolean     NOT NULL DEFAULT false,
    consent_analytics boolean     NOT NULL DEFAULT false
);

-- Prueba de cada autorización: una fila por cada cambio, nunca se actualiza.
CREATE TABLE IF NOT EXISTS device_consents (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    device_id    text        NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    excuses      boolean     NOT NULL,
    phone_usage  boolean     NOT NULL,
    cross_border boolean     NOT NULL,
    analytics    boolean     NOT NULL,
    text_version text        NOT NULL
);

CREATE INDEX IF NOT EXISTS device_consents_device_created
    ON device_consents (device_id, created_at DESC);
