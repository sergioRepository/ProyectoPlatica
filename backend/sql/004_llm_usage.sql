-- Consumo: una fila por cada llamada a Grok. Idempotente: se puede correr varias veces.
CREATE TABLE IF NOT EXISTS llm_usage (
    id            uuid PRIMARY KEY,
    created_at    timestamptz NOT NULL DEFAULT now(),
    device_id     text        NOT NULL,
    -- NULL si la base no respondió al leer el plan.
    plan          text,
    -- template | judge | agent
    graph         text        NOT NULL,
    node          text        NOT NULL,
    model         text        NOT NULL,
    -- NULL si no hubo respuesta: un timeout no trae el conteo, aunque Grok haya gastado.
    input_tokens  integer,
    -- Incluye los tokens de razonamiento, que xAI cobra a la tarifa de salida.
    output_tokens integer,
    -- NULL sin precio configurado. No descuenta la tarifa menor de la entrada en caché.
    cost_usd      numeric(12, 6),
    latency_ms    integer     NOT NULL,
    -- ok | timeout | error
    status        text        NOT NULL,
    request_id    uuid        NOT NULL
);

CREATE INDEX IF NOT EXISTS llm_usage_device_created ON llm_usage (device_id, created_at DESC);
CREATE INDEX IF NOT EXISTS llm_usage_created ON llm_usage (created_at);
CREATE INDEX IF NOT EXISTS llm_usage_request ON llm_usage (request_id);
