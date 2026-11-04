-- Planes sin cobro y contadores diarios. Idempotente: se puede correr varias veces.
ALTER TABLE devices ADD COLUMN IF NOT EXISTS plan text NOT NULL DEFAULT 'free';
-- Respuesta a "¿Pagarías por este plan?": yes | no | maybe.
ALTER TABLE devices ADD COLUMN IF NOT EXISTS would_pay text;

-- Vive en la base porque en Vercel cada instancia lleva su propia memoria.
CREATE TABLE IF NOT EXISTS device_daily_usage (
    device_id  text    NOT NULL,
    day        date    NOT NULL,
    llm_calls  integer NOT NULL DEFAULT 0,
    rejections integer NOT NULL DEFAULT 0,
    templates  integer NOT NULL DEFAULT 0,
    PRIMARY KEY (device_id, day)
);
