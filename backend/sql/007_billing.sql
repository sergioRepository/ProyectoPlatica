-- Cobros con Google Play: derechos validados y libro de créditos. Idempotente.
-- Análisis del agente; llm_calls pasa a contar solo evaluaciones de 10 minutos.
ALTER TABLE device_daily_usage ADD COLUMN IF NOT EXISTS agent_calls integer NOT NULL DEFAULT 0;

-- Un derecho por compra de suscripción, ligado a la instalación que la canjeó.
CREATE TABLE IF NOT EXISTS entitlements (
    purchase_token        text PRIMARY KEY,
    device_id             text NOT NULL,
    product_id            text NOT NULL,
    -- monthly | semester
    plan                  text NOT NULL,
    -- Valor SUBSCRIPTION_STATE_* de Google.
    state                 text NOT NULL,
    expiry_time           timestamptz,
    auto_renewing         boolean NOT NULL DEFAULT false,
    linked_purchase_token text,
    account_hash          text,
    updated_at            timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS entitlements_device_idx ON entitlements (device_id);

-- El saldo es la suma de delta; un acceso incluido en el plan se anota con delta = 0.
CREATE TABLE IF NOT EXISTS credit_ledger (
    id         bigserial PRIMARY KEY,
    device_id  text NOT NULL,
    delta      integer NOT NULL,
    -- purchase | access | refund | transfer_in | transfer_out
    kind       text NOT NULL,
    -- Siempre un hash (token de compra, pedido o traslado): tras borrar no queda nada en claro.
    ref        text NOT NULL,
    -- Día de cupo (hora de Colombia).
    day        date NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (kind, ref)
);
CREATE INDEX IF NOT EXISTS credit_ledger_device_idx ON credit_ledger (device_id);
-- Hash de la suscripción que pagó un acceso incluido: los 3 del día son de la suscripción, no de
-- la instalación, así que reinstalar o borrar los datos no los renueva.
ALTER TABLE credit_ledger ADD COLUMN IF NOT EXISTS plan_ref text;
CREATE INDEX IF NOT EXISTS credit_ledger_plan_day_idx ON credit_ledger (plan_ref, day);
