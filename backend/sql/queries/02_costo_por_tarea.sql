-- Costo promedio por intervención y por tarea resuelta
-- Tarea resuelta: un juicio o una plantilla con respuesta de Grok, o un análisis que terminó en
-- una decisión. Una intervención es la que el teléfono confirmó como ejecutada.
WITH por_peticion AS (
    SELECT
        graph,
        device_id,
        request_id,
        sum(cost_usd) AS costo_usd,
        sum(coalesce(input_tokens, 0) + coalesce(output_tokens, 0)) AS tokens,
        bool_or(status = 'ok') AS respondio
    FROM llm_usage
    GROUP BY 1, 2, 3
)
SELECT
    'intervencion ejecutada' AS tarea,
    count(*) AS total,
    avg(p.costo_usd) AS costo_promedio_usd,
    avg(p.tokens) AS tokens_promedio
FROM por_peticion p
JOIN agent_events e USING (device_id, request_id)
WHERE e.status = 'executed'
UNION ALL
SELECT 'analisis con decision', count(*), avg(p.costo_usd), avg(p.tokens)
FROM por_peticion p
JOIN agent_events e USING (device_id, request_id)
WHERE e.decision <> 'agent_unavailable'
UNION ALL
SELECT 'juicio con respuesta', count(*), avg(costo_usd), avg(tokens)
FROM por_peticion
WHERE graph = 'judge' AND respondio
UNION ALL
SELECT 'plantilla generada', count(*), avg(costo_usd), avg(tokens)
FROM por_peticion
WHERE graph = 'template' AND respondio;
